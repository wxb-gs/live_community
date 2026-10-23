#!/usr/bin/env python3
"""Read-only reconciliation; exit nonzero on unexplained divergence or excess privileges."""
import argparse, importlib.util, json, os
from pathlib import Path
import pymysql
spec=importlib.util.spec_from_file_location('migration',Path(__file__).with_name('migrate-boundaries.py'));m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
def main():
 p=argparse.ArgumentParser();p.add_argument('--verify-permissions',action='store_true');args=p.parse_args();report={}
 with m.connect() as db, db.cursor() as c:
  queries={
   'count_mismatches':"SELECT i.target_type,i.target_id,i.interaction_type,i.total,COALESCE(r.actual,0) actual FROM interaction_count i LEFT JOIN (SELECT target_type,target_id,interaction_type,SUM(status='ACTIVE') actual FROM interaction_record GROUP BY target_type,target_id,interaction_type) r ON i.target_type=r.target_type AND i.target_id=r.target_id AND i.interaction_type=r.interaction_type WHERE i.total<>COALESCE(r.actual,0)",
   'missing_counters':"SELECT DISTINCT r.target_type,r.target_id,r.interaction_type FROM interaction_record r LEFT JOIN interaction_count i ON i.target_type=r.target_type AND i.target_id=r.target_id AND i.interaction_type=r.interaction_type WHERE i.target_id IS NULL",
   'note_projection_mismatches':"SELECT n.id,n.like_count,i.total FROM note n JOIN interaction_count i ON i.target_type='note' AND i.target_id=n.id AND i.interaction_type='like' WHERE n.like_count<>i.total OR n.like_version<>i.version",
   'comment_projection_mismatches':"SELECT c.id,c.like_count,i.total FROM comment_index c JOIN interaction_count i ON i.target_type='comment' AND i.target_id=c.id AND i.interaction_type='like' WHERE c.like_count<>i.total OR c.like_version<>i.version",
   'legacy_file_references':"SELECT id,object_key FROM note WHERE file_id IS NULL AND object_key IS NOT NULL",
   'pending_note_outbox':"SELECT COUNT(*) total,MIN(created_at) oldest FROM note_outbox WHERE sent_at IS NULL",
   'pending_auth_outbox':"SELECT COUNT(*) total,MIN(created_at) oldest FROM auth_outbox WHERE sent_at IS NULL"}
  for name,sql in queries.items():c.execute(sql);report[name]=c.fetchall()
 if args.verify_permissions:
  report['permissions']={}
  for owner,own,foreign in [('auth','user_info','note'),('note','note','user_info'),('upload','file_metadata','note'),('leaf','leaf_alloc','user_info')]:
   with pymysql.connect(host=os.getenv('MYSQL_HOST','localhost'),port=int(os.getenv('MYSQL_PORT','13306')),user=os.getenv(owner.upper()+'_DB_USER',owner+'_app'),password=os.getenv(owner.upper()+'_DB_PASSWORD',owner+'_dev'),database='live_community') as db,db.cursor() as c:
    c.execute(f'SELECT 1 FROM {own} LIMIT 0')
    try:c.execute(f'SELECT 1 FROM {foreign} LIMIT 0')
    except pymysql.err.OperationalError as e:
     if e.args[0]!=1142:raise
     report['permissions'][owner]='isolated'
    else:raise SystemExit(owner+' can access foreign table '+foreign)
 print(json.dumps(report,ensure_ascii=False,indent=2,default=str))
 if any(report[k] for k in ['count_mismatches','missing_counters','note_projection_mismatches','comment_projection_mismatches']):raise SystemExit(1)
if __name__=='__main__':main()
