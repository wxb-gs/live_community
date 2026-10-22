#!/usr/bin/env python3
"""Offline, idempotent boundary migration. Stop writers and back up before running."""
import argparse, json, os, re, time, uuid
from pathlib import Path
import pymysql
from project_layout import module_path
ROOT=Path(__file__).resolve().parents[1]
def connect():
 return pymysql.connect(host=os.getenv('MYSQL_HOST','localhost'),port=int(os.getenv('MYSQL_PORT','13306')),user=os.getenv('MIGRATION_DB_USER','root'),password=os.environ['MIGRATION_DB_PASSWORD'],database='live_community',charset='utf8mb4',autocommit=False,cursorclass=pymysql.cursors.DictCursor)
def execute_file(cursor,path):
 text=re.sub(r'--[^\n]*','',path.read_text(encoding='utf-8'))
 for statement in text.split(';'):
  if statement.strip():
   try:cursor.execute(statement)
   except pymysql.err.OperationalError as e:
    if e.args[0]!=1061:raise  # old init script contains standalone duplicate index creation

def main():
 p=argparse.ArgumentParser();p.add_argument('--maintenance-confirmed',action='store_true');p.add_argument('--legacy-redis-url');p.add_argument('--grant-dev-users',action='store_true');args=p.parse_args()
 if not args.maintenance_confirmed:p.error('Stop old writers and consumers, back up MySQL and Redis, then pass --maintenance-confirmed')
 with connect() as db:
  with db.cursor() as c:
   for path in [module_path('leaf-service')/'src/main/resources/sql/init_leaf.sql',module_path('auth-service')/'src/main/resources/sql/init_auth.sql',module_path('note-service')/'src/main/resources/sql/init_comment_mysql.sql',module_path('note-service')/'src/main/resources/sql/init_interaction_mysql.sql',ROOT/'scripts/sql/service-boundaries.sql']:
    execute_file(c,path)
   columns={'file_metadata':{'temp_removed':'TINYINT NOT NULL DEFAULT 0'},'note':{'file_id':'VARCHAR(36) NULL','version':'BIGINT NOT NULL DEFAULT 1','like_version':'BIGINT NOT NULL DEFAULT 0'},'comment_index':{'like_version':'BIGINT NOT NULL DEFAULT 0'},'user_info':{'version':'BIGINT NOT NULL DEFAULT 1'}}
   for table,fields in columns.items():
    c.execute('SELECT COLUMN_NAME FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=%s',(table,));present={row['COLUMN_NAME'] for row in c.fetchall()}
    for name,definition in fields.items():
     if name not in present:c.execute(f'ALTER TABLE `{table}` ADD COLUMN `{name}` {definition}')
   c.execute('UPDATE note SET view_count=COALESCE(view_count,0),like_count=COALESCE(like_count,0)')
   c.execute('UPDATE comment_index SET like_count=COALESCE(like_count,0)')
   for table,column in [('note','view_count'),('note','like_count'),('comment_index','like_count')]:c.execute(f'ALTER TABLE `{table}` MODIFY `{column}` BIGINT NOT NULL DEFAULT 0')
   # Never reset an existing allocation high-water mark.
   for tag in ['note','comment','user','wechat','phone','taobao']:
    c.execute('INSERT IGNORE INTO leaf_alloc(biz_tag,max_id,step,description) VALUES(%s,1,1000,%s)',(tag,tag+' IDs'))
   for tag,table,col in [('note','note','id'),('comment','comment_index','id'),('user','user_info','user_id'),('wechat','user_wechat','wechat_id'),('phone','user_phone','phone_id'),('taobao','user_taobao','taobao_id')]:
    c.execute(f'SELECT COALESCE(MAX(`{col}`),0)+1 AS next_id FROM `{table}`');next_id=c.fetchone()['next_id'];c.execute('UPDATE leaf_alloc SET max_id=GREATEST(max_id,%s) WHERE biz_tag=%s',(next_id,tag))
   if args.legacy_redis_url:
    import redis
    cache=redis.Redis.from_url(args.legacy_redis_url,decode_responses=True)
    for prefix,kind in [('like','like'),('fav','favorite')]:
     for key in cache.scan_iter(match=prefix+':users:*',count=200):
      parts=key.split(':')
      if len(parts)!=4 or parts[2] not in ['note','comment'] or not parts[3].isdigit():continue
      target,id=parts[2],int(parts[3]);now=int(time.time()*1000)
      c.execute('UPDATE interaction_record SET status=%s WHERE target_type=%s AND target_id=%s AND interaction_type=%s',('INACTIVE',target,id,kind))
      for user in cache.sscan_iter(key,count=500):
       c.execute('INSERT INTO interaction_record(target_type,target_id,interaction_type,user_id,status,created_at,updated_at) VALUES(%s,%s,%s,%s,%s,%s,%s) ON DUPLICATE KEY UPDATE status=VALUES(status),updated_at=VALUES(updated_at)',(target,id,kind,int(user),'ACTIVE',now,now))
    for key in cache.scan_iter(match='note:view:*',count=200):
     suffix=key.split(':')[-1]
     if not suffix.isdigit():continue
     id=int(suffix);amount=int(cache.get(key) or 0)
     if amount<=0:continue
     # Reserved event ID records the one-time legacy aggregate import; Redis is not deleted.
     changed=c.execute('INSERT IGNORE INTO note_view(event_id,note_id,user_id,created_at,applied) VALUES(%s,%s,0,%s,1)',('legacy-view-'+str(id),id,int(time.time()*1000)))
     if changed:c.execute('UPDATE note SET view_count=view_count+%s,version=version+1 WHERE id=%s',(amount,id))
   else:print('No Redis import requested: Redis-only relationships cannot be recovered; review the reconciliation report before cutover.')
   # Recompute authoritative counters from durable relations; version always advances on a rerun.
   c.execute("INSERT INTO interaction_count(target_type,target_id,interaction_type,total,version) SELECT target_type,target_id,interaction_type,SUM(status='ACTIVE'),1 FROM interaction_record GROUP BY target_type,target_id,interaction_type ON DUPLICATE KEY UPDATE total=VALUES(total),version=version+1")
   c.execute("UPDATE note n LEFT JOIN interaction_count i ON i.target_type='note' AND i.target_id=n.id AND i.interaction_type='like' SET n.like_count=COALESCE(i.total,0),n.like_version=COALESCE(i.version,0),n.version=n.version+1")
   c.execute("UPDATE comment_index c LEFT JOIN interaction_count i ON i.target_type='comment' AND i.target_id=c.id AND i.interaction_type='like' SET c.like_count=COALESCE(i.total,0),c.like_version=COALESCE(i.version,0)")
   # Seed current public projections; do not serialize password or third-party credentials.
   for entity,table,key,owner,fields in [('note','note','id','note','id,user_id,title,content,summary,tags,category,view_count,like_count,status,created_at,version'),('user','user_info','user_id','auth','user_id,username,nickname,avatar,status,created_at,version')]:
    c.execute(f'SELECT {fields} FROM {table}');rows=c.fetchall()
    for row in rows:
     id=row[key];version=row.pop('version');row['id']=id
     if entity=='user':row.pop('user_id')
     event={'schemaVersion':1,'entityType':entity,'entityId':id,'version':version,'deleted':row['status']!=('PUBLISHED' if entity=='note' else 'ACTIVE'),'document':row}
     c.execute(f'INSERT INTO {owner}_outbox(event_id,topic,event_key,payload,created_at) VALUES(%s,%s,%s,%s,%s)',(str(uuid.uuid4()),'search_documents',f'{entity}:{id}',json.dumps(event,ensure_ascii=False),int(time.time()*1000)))
   if args.grant_dev_users:
    # Development-only defaults match local YAML; deployments supply independent secrets.
    grants={'auth':['user_info','user_wechat','user_phone','user_taobao','auth_outbox'],'note':['note','comment_index','comment_content','comment_subject','interaction_record','interaction_count','interaction_command','note_view','note_outbox'],'upload':['file_metadata'],'leaf':['leaf_alloc']}
    for owner,tables in grants.items():
     user=owner+'_app';password=os.getenv(owner.upper()+'_DB_PASSWORD',owner+'_dev')
     c.execute('CREATE USER IF NOT EXISTS %s@%s IDENTIFIED BY %s',(user,'%',password))
     c.execute('REVOKE ALL PRIVILEGES, GRANT OPTION FROM %s@%s',(user,'%'))
     for table in tables:c.execute(f'GRANT SELECT,INSERT,UPDATE,DELETE ON live_community.`{table}` TO %s@%s',(user,'%'))
  db.commit()
 print('Migration complete. Run reconcile-boundaries.py and inspect legacy file references before restarting writers.')
if __name__=='__main__':main()
