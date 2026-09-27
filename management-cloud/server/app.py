"""Authenticated single-shop cloud snapshots with transactional revision checks."""
import legacy as core
import os,json,time,sqlite3,hashlib
from pathlib import Path
from http.server import ThreadingHTTPServer
ROOT=Path(__file__).parent/'static'
def durable():return os.environ.get('REQUIRE_DURABLE')!='1' or os.path.ismount('/data')
def init():
 core.init()
 with core.connect() as c:
  c.executescript('CREATE TABLE IF NOT EXISTS shop_versions(revision INTEGER PRIMARY KEY, request_id TEXT UNIQUE NOT NULL, payload TEXT NOT NULL, created INTEGER NOT NULL);')
def validate(d):
 if not isinstance(d,dict) or not isinstance(d.get('state'),dict):raise ValueError('Missing shop state')
 s=d['state'];p=s.get('products');sales=s.get('sales')
 if s.get('schema')!=1 or not isinstance(p,list) or len(p)>20000 or not isinstance(sales,list) or len(sales)>50000:raise ValueError('Invalid data or capacity exceeded')
 ids=set()
 for x in p:
  if not isinstance(x,dict) or not isinstance(x.get('id'),str) or x['id'] in ids:raise ValueError('Duplicate stock ID')
  ids.add(x['id'])
  if not all(isinstance(x.get(k),str) and len(x[k])<=500 for k in ['name','brand','pack']):raise ValueError('Invalid product')
  if type(x.get('qty'))!=int or not 0<=x['qty']<=10000000:raise ValueError('Invalid stock quantity')
  if x.get('rate') is not None:core.money(x['rate'])
 numbers=set()
 for b in sales:
  no=core.text(b.get('number',''),80)
  if no in numbers:raise ValueError('Duplicate bill number')
  numbers.add(no);total=core.money(b.get('total'));paid=core.money(b.get('paid'));discount=core.money(b.get('discount'))
  if paid>total or not isinstance(b.get('items'),list) or not b['items']:raise ValueError('Invalid bill')
  subtotal=0
  for item in b['items']:
   if item.get('id') not in ids:raise ValueError('Missing stock item')
   subtotal+=core.money(item['rate'])*core.integer(item['qty'],1,100000)
  if subtotal-discount!=total or core.money(b.get('subtotal'))!=subtotal:raise ValueError('Bill totals do not reconcile')
 if type(s.get('next'))!=int or s['next']<1:raise ValueError('Invalid bill sequence')
 if any(int(n[3:])>=s['next'] for n in numbers if n.startswith('PP-') and n[3:].isdigit()):raise ValueError('Bill sequence would reuse a number')
 if not isinstance(d.get('catalogue'),list) or len(d['catalogue'])>20000:raise ValueError('Invalid catalogue')
 for x in d['catalogue']:
  if not isinstance(x,dict) or not isinstance(x.get('name'),str) or not isinstance(x.get('brand'),str):raise ValueError('Invalid catalogue row')
 for key in ['customers','suppliers','expenses','purchases','supplierPayments']:
  if not isinstance(s.get(key,[]),list):raise ValueError('Invalid management records')
 for x in s.get('expenses',[]):core.money(x['amount'])
 return json.dumps(d,ensure_ascii=False,allow_nan=False,separators=(',',':'))
class Handler(core.Handler):
 def token(self):
  a=self.headers.get('Authorization','')
  return a[7:] if a.startswith('Bearer ') else super().token()
 def send(self,status,data,kind='application/json',cookie=None):
  # Native clients obtain only their freshly issued login token over TLS.
  if cookie and self.headers.get('X-Native-Client')=='1' and isinstance(data,dict):
   data={**data,'token':cookie.split(';',1)[0].split('=',1)[1]}
  body=json.dumps(data,ensure_ascii=False).encode() if kind=='application/json' else data
  self.send_response(status);self.send_header('Content-Type',kind);self.send_header('Content-Length',str(len(body)));self.send_header('Cache-Control','no-store');self.send_header('X-Content-Type-Options','nosniff');self.send_header('Referrer-Policy','no-referrer');self.send_header('Content-Security-Policy',"default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; media-src 'self' blob:; frame-ancestors 'none'; base-uri 'none'; form-action 'self'")
  if cookie:self.send_header('Set-Cookie',cookie)
  self.end_headers();self.wfile.write(body)
 def do_GET(self):
  path=self.path.split('?')[0]
  if path=='/health':return self.send(200 if durable() else 503,{'ok':durable(),'durable':durable()})
  if path=='/api/shop':
   with core.connect() as c:
    u=self.user(c)
    if not u:return self.send(401,{'error':'Sign in to your cloud workspace.'})
    if u['role']!='owner':return self.send(403,{'error':'Owner access is required.'})
    row=c.execute('SELECT * FROM shop_versions ORDER BY revision DESC LIMIT 1').fetchone()
    return self.send(200,{'revision':row['revision'] if row else 0,'payload':json.loads(row['payload']) if row else None,'durable':durable()})
  files={'/':'index.html','/app.js':'app.js','/cloud.js':'cloud.js','/management.js':'management.js','/pricing.js':'pricing.js','/stock.js':'stock.js','/style.css':'style.css'}
  if path in files:
   f=ROOT/files[path];mime='text/html; charset=utf-8' if f.suffix=='.html' else 'text/css' if f.suffix=='.css' else 'application/javascript'
   return self.send(200,f.read_bytes(),mime)
  return self.send(404,{'error':'Not found'})
 def do_POST(self):
  if self.path in ['/api/login','/api/logout','/api/password']:return super().do_POST()
  if self.path!='/api/shop':return self.send(404,{'error':'Not found'})
  if self.headers.get('Origin')!=core.ORIGIN or self.headers.get('X-Shop-Request')!='1':return self.send(403,{'error':'Request rejected'})
  if not durable():return self.send(503,{'error':'Persistent storage is not ready. Cloud writes disabled.'})
  try:
   size=int(self.headers.get('Content-Length','0'))
   if not 0<size<=8000000:raise ValueError('Backup exceeds 8 MB limit')
   d=json.loads(self.rfile.read(size));key=core.text(d['request_id'],100);expected=core.integer(d['revision']);payload=validate(d['payload'])
   with core.connect() as c:
    c.execute('BEGIN IMMEDIATE');u=self.user(c)
    if not u:return self.send(401,{'error':'Session expired. Sign in again.'})
    if u['role']!='owner':return self.send(403,{'error':'Owner access required'})
    previous=c.execute('SELECT revision FROM shop_versions WHERE request_id=?',(key,)).fetchone()
    if previous:return self.send(200,{'revision':previous['revision']})
    current=c.execute('SELECT COALESCE(MAX(revision),0) FROM shop_versions').fetchone()[0]
    if current!=expected:return self.send(409,{'error':'Another device saved changes. Refresh cloud data before retrying.','revision':current})
    rev=current+1;c.execute('INSERT INTO shop_versions VALUES(?,?,?,?)',(rev,key,payload,int(time.time())))
    c.execute('DELETE FROM shop_versions WHERE revision<?',(max(1,rev-9),));c.commit()
    return self.send(200,{'revision':rev})
  except (ValueError,TypeError,KeyError,ArithmeticError) as e:return self.send(400,{'error':str(e)})
  except Exception:return self.send(500,{'error':'Cloud save failed. Refresh before retrying.'})
if __name__=='__main__':
 init();ThreadingHTTPServer(('0.0.0.0',int(os.environ.get('PORT',8080))),Handler).serve_forever()
