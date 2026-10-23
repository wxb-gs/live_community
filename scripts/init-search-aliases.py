#!/usr/bin/env python3
"""One-time alias adoption. Requires all search writers stopped; existing data is cloned first."""
import argparse, os, time
import requests
ES=os.getenv('ES_URL','http://localhost:9200').rstrip('/')
def call(method,path,**kw):
 r=requests.request(method,ES+path,timeout=120,**kw);r.raise_for_status();return r.json()
def main():
 p=argparse.ArgumentParser();p.add_argument('--maintenance-confirmed',action='store_true');args=p.parse_args()
 if not args.maintenance_confirmed:p.error('Stop search writers and back up ES, then pass --maintenance-confirmed')
 for name in ['notes','users']:
  if requests.get(ES+'/_alias/'+name,timeout=10).ok:continue
  old=requests.get(ES+'/'+name,timeout=10)
  if old.status_code==404:
   # Prefer init-all.sh for IK mappings, then adopt its indices here.
   raise RuntimeError('Initialize '+name+' mapping with scripts/init-all.sh first; no empty default mapping is created')
  old.raise_for_status();target=name+'_legacy_'+str(int(time.time()*1000))
  call('PUT','/'+name+'/_settings',json={'index.blocks.write':True})
  call('POST','/'+name+'/_clone/'+target)
  call('GET','/_cluster/health/'+target,params={'wait_for_status':'yellow','timeout':'60s'})
  call('PUT','/'+target+'/_settings',json={'index.blocks.write':False})
  source=call('GET','/'+name+'/_count')['count'];copy=call('GET','/'+target+'/_count')['count']
  if source!=copy:raise RuntimeError('Clone count mismatch; original index preserved')
  # Atomic adoption removes the original name, but the complete clone remains available.
  call('POST','/_aliases',json={'actions':[{'remove_index':{'index':name}},{'add':{'index':target,'alias':name}}]})
  print(name+' -> '+target+' (complete clone retained)')
if __name__=='__main__':main()
