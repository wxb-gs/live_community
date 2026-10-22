#!/usr/bin/env python3
"""Versioned snapshot + Kafka catch-up + atomic alias switch; stop only the search Worker."""
import argparse, importlib.util, json, os, time, uuid
from pathlib import Path
import requests
from kafka import KafkaConsumer, TopicPartition
from kafka.admin import KafkaAdminClient
spec=importlib.util.spec_from_file_location('migration',Path(__file__).with_name('migrate-boundaries.py'));m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
ES=os.getenv('ES_URL','http://localhost:9200').rstrip('/')
def request(method,path,**kw):
 r=requests.request(method,ES+path,timeout=60,**kw);r.raise_for_status();return r.json()
def apply(event,targets):
 if event['schemaVersion']!=1 or event['entityType'] not in targets:raise ValueError('Invalid event')
 doc=event['document'] if not event['deleted'] else {'id':event['entityId'],'status':'DELETED','deleted':True}
 r=requests.put(f"{ES}/{targets[event['entityType']]}/_doc/{event['entityId']}",params={'version':event['version'],'version_type':'external'},json=doc,timeout=30)
 if r.status_code!=409:r.raise_for_status()
def main():
 p=argparse.ArgumentParser();p.add_argument('--worker-stopped-confirmed',action='store_true');args=p.parse_args()
 if not args.worker_stopped_confirmed:p.error('Stop search-sync-service; writers and outbox relays may continue. Pass --worker-stopped-confirmed.')
 brokers=os.getenv('KAFKA_SERVERS','localhost:9092').split(',');admin=KafkaAdminClient(bootstrap_servers=brokers)
 def ensure_stopped():
  group=admin.describe_consumer_groups(['search-documents-v1'])[0]
  if getattr(group,'members',[]):raise RuntimeError('Search Worker still has active consumer members')
 ensure_stopped()
 consumer=KafkaConsumer(bootstrap_servers=brokers,enable_auto_commit=False,value_deserializer=lambda b:json.loads(b.decode()),consumer_timeout_ms=1000)
 topic='search_documents';partitions=consumer.partitions_for_topic(topic)
 if not partitions:raise RuntimeError('search_documents topic must already exist')
 tps=[TopicPartition(topic,p) for p in sorted(partitions)];consumer.assign(tps)
 start=consumer.end_offsets(tps)
 for tp,offset in start.items():consumer.seek(tp,offset)
 stamp=str(int(time.time()*1000));targets={'note':'notes_v'+stamp,'user':'users_v'+stamp};old={}
 for entity,alias in [('note','notes'),('user','users')]:
  # Aliases are required; never delete a concrete index as a side effect of rebuild.
  r=requests.get(ES+'/_alias/'+alias,timeout=10)
  if r.status_code!=200:raise RuntimeError(alias+' must be an alias. Use init-search-aliases.py during first migration.')
  old[alias]=list(r.json());source=old[alias][0];mapping=request('GET','/'+source+'/_mapping')[source]['mappings']
  settings=request('GET','/'+source+'/_settings')[source]['settings']['index'];analysis=settings.get('analysis')
  body={'mappings':mapping,'settings':{'number_of_shards':1,'number_of_replicas':0}}
  if analysis:body['settings']['analysis']=analysis
  request('PUT','/'+targets[entity],json=body)
 with m.connect() as db,db.cursor() as c:
  c.execute('SET TRANSACTION ISOLATION LEVEL REPEATABLE READ');c.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT')
  for entity,table,key,fields in [('note','note','id','id,user_id,title,content,summary,tags,category,view_count,like_count,status,created_at,version'),('user','user_info','user_id','user_id,username,nickname,avatar,status,created_at,version')]:
   c.execute(f'SELECT {fields} FROM {table}')
   while True:
    rows=c.fetchmany(500)
    if not rows:break
    for row in rows:
     id=row[key];version=row.pop('version');row['id']=id
     if entity=='user':row.pop('user_id')
     apply({'schemaVersion':1,'entityType':entity,'entityId':id,'version':version,'deleted':row['status']!=('PUBLISHED' if entity=='note' else 'ACTIVE'),'document':row},targets)
  db.rollback()
 # Since normal Worker stays stopped, anything not yet relayed will be handled after alias switch.
 barrier=consumer.end_offsets(tps);deadline=time.monotonic()+300
 while any(consumer.position(tp)<barrier[tp] for tp in tps):
  if time.monotonic()>deadline:raise TimeoutError('Catch-up timed out; old aliases left intact')
  for tp,messages in consumer.poll(timeout_ms=1000,max_records=200).items():
   for msg in messages:apply(msg.value,targets)
 ensure_stopped()
 for target in targets.values():request('POST','/'+target+'/_refresh')
 actions=[]
 for entity,alias in [('note','notes'),('user','users')]:
  for index in old[alias]:actions.append({'remove':{'index':index,'alias':alias}})
  actions.append({'add':{'index':targets[entity],'alias':alias}})
 request('POST','/_aliases',json={'actions':actions})
 print(json.dumps({'new':targets,'old':old,'start':{str(k):v for k,v in start.items()},'barrier':{str(k):v for k,v in barrier.items()},'instruction':'Restart search-sync-service. Old indices retained; rollback requires replay/catch-up for changes after cutover.'},indent=2))
 consumer.close();admin.close()
if __name__=='__main__':main()
