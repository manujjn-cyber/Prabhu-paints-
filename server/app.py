"""Single-shop, transactional cloud service. Python 3.12+, no third-party packages."""
import os, json, sqlite3, secrets, hashlib, hmac, time, re, io, csv
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from http.cookies import SimpleCookie
DB=os.environ.get('DB_PATH','shop.db')
ORIGIN=os.environ.get('PUBLIC_ORIGIN','http://localhost:8080').rstrip('/')
STATIC=Path(__file__).parent/'static'
def connect():
    c=sqlite3.connect(DB,timeout=30); c.row_factory=sqlite3.Row
    c.execute('PRAGMA foreign_keys=ON'); return c
def pw(password,salt): return hashlib.pbkdf2_hmac('sha256',password.encode(),salt.encode(),210000).hex()
def text(v,maximum=150):
    s=str(v).strip()
    if not s or len(s)>maximum: raise ValueError('Required text missing or too long / विवरण जाँचें')
    return s
def integer(v,low=0,high=100000000):
    if isinstance(v,bool) or not re.fullmatch(r'\d+',str(v)): raise ValueError('Enter a whole positive number / पूरी संख्या दर्ज करें')
    n=int(v)
    if not low<=n<=high: raise ValueError('Number outside allowed range')
    return n
def money(v):
    d=Decimal(str(v))
    if not d.is_finite() or d<0 or d>Decimal('10000000') or d!=d.quantize(Decimal('.01')): raise ValueError('Invalid rupee amount / राशि जाँचें')
    return int(d*100)
def init():
    Path(DB).parent.mkdir(parents=True,exist_ok=True)
    with connect() as c:
        c.executescript('''
        PRAGMA journal_mode=WAL;
        CREATE TABLE IF NOT EXISTS users(id INTEGER PRIMARY KEY,username TEXT UNIQUE,hash TEXT,salt TEXT,role TEXT);
        CREATE TABLE IF NOT EXISTS sessions(token TEXT PRIMARY KEY,user_id INTEGER REFERENCES users(id),expires INTEGER);
        CREATE TABLE IF NOT EXISTS attempts(key TEXT PRIMARY KEY,count INTEGER,until INTEGER);
        CREATE TABLE IF NOT EXISTS products(id INTEGER PRIMARY KEY,brand TEXT COLLATE NOCASE,name TEXT COLLATE NOCASE,shade TEXT COLLATE NOCASE,pack TEXT COLLATE NOCASE,stock INTEGER CHECK(stock>=0),price INTEGER,tax INTEGER,low INTEGER,UNIQUE(brand,name,shade,pack));
        CREATE TABLE IF NOT EXISTS customers(id INTEGER PRIMARY KEY,name TEXT,phone TEXT);
        CREATE TABLE IF NOT EXISTS invoices(id INTEGER PRIMARY KEY AUTOINCREMENT,request_id TEXT UNIQUE,customer_id INTEGER REFERENCES customers(id),created INTEGER,user_id INTEGER REFERENCES users(id),subtotal INTEGER,tax INTEGER,total INTEGER,paid INTEGER,lines TEXT);
        CREATE TABLE IF NOT EXISTS cancellations(invoice_id INTEGER PRIMARY KEY REFERENCES invoices(id),reason TEXT,refund INTEGER,created INTEGER,user_id INTEGER REFERENCES users(id));
        CREATE TABLE IF NOT EXISTS expenses(id INTEGER PRIMARY KEY,request_id TEXT UNIQUE,category TEXT,amount INTEGER,note TEXT,created INTEGER,user_id INTEGER REFERENCES users(id));
        CREATE TABLE IF NOT EXISTS audit(id INTEGER PRIMARY KEY,action TEXT,details TEXT,created INTEGER,user_id INTEGER REFERENCES users(id));
        CREATE TABLE IF NOT EXISTS payments(id INTEGER PRIMARY KEY,request_id TEXT UNIQUE,invoice_id INTEGER REFERENCES invoices(id),amount INTEGER,created INTEGER,user_id INTEGER REFERENCES users(id));
        CREATE TABLE IF NOT EXISTS movements(id INTEGER PRIMARY KEY,request_id TEXT UNIQUE,product_id INTEGER REFERENCES products(id),quantity INTEGER,cost INTEGER,note TEXT,created INTEGER,user_id INTEGER REFERENCES users(id));
        ''')
        if not c.execute('SELECT 1 FROM users').fetchone():
            password=os.environ.get('ADMIN_PASSWORD','')
            if len(password)<12: raise RuntimeError('Set ADMIN_PASSWORD (12+ characters) for first startup')
            salt=secrets.token_hex(16)
            c.execute('INSERT INTO users(username,hash,salt,role) VALUES(?,?,?,?)',('owner',pw(password,salt),salt,'owner'))
def bill(c,d,u):
    key=text(d['request_id'],100)
    existing=c.execute('SELECT id FROM invoices WHERE request_id=?',(key,)).fetchone()
    if existing: return dict(existing)
    lines=d['lines']
    if not isinstance(lines,list) or not 1<=len(lines)<=100: raise ValueError('Add 1–100 items')
    customer=d.get('customer_id') or None
    if customer is not None:
        customer=integer(customer,1)
        if not c.execute('SELECT 1 FROM customers WHERE id=?',(customer,)).fetchone(): raise ValueError('Customer not found')
    saved=[]; subtotal=tax=0; seen=set()
    for line in lines:
        pid=integer(line['product_id'],1); qty=integer(line['quantity'],1,100000)
        if pid in seen: raise ValueError('Combine duplicate product rows')
        seen.add(pid)
        p=c.execute('SELECT * FROM products WHERE id=?',(pid,)).fetchone()
        if not p or p['stock']<qty: raise ValueError('Insufficient stock / स्टॉक कम है')
        if integer(line['expected_price'])!=p['price']: raise ValueError('Price changed. Refresh and review the bill / कीमत बदल गई है')
        base=p['price']*qty
        gst=int((Decimal(base)*p['tax']/10000).quantize(Decimal('1'),rounding=ROUND_HALF_UP))
        subtotal+=base; tax+=gst
        saved.append({**dict(p),'quantity':qty,'base':base,'line_tax':gst})
        c.execute('UPDATE products SET stock=stock-? WHERE id=?',(qty,pid))
    paid=money(d.get('paid','0')); total=subtotal+tax
    if total>1000000000: raise ValueError('Bill exceeds the pilot limit of ₹1 crore')
    if paid>total: raise ValueError('Payment exceeds bill total')
    if paid<total and not customer: raise ValueError('Select a customer for credit sales / उधार के लिए ग्राहक चुनें')
    cur=c.execute('INSERT INTO invoices(request_id,customer_id,created,user_id,subtotal,tax,total,paid,lines) VALUES(?,?,?,?,?,?,?,?,?)',(key,customer,int(time.time()),u['id'],subtotal,tax,total,paid,json.dumps(saved)))
    return {'id':cur.lastrowid}
class Handler(BaseHTTPRequestHandler):
    def log_message(self,*args): pass
    def send(self,status,data,kind='application/json',cookie=None):
        body=json.dumps(data,ensure_ascii=False).encode() if kind=='application/json' else data
        self.send_response(status); self.send_header('Content-Type',kind); self.send_header('Content-Length',str(len(body)))
        self.send_header('Cache-Control','no-store'); self.send_header('X-Content-Type-Options','nosniff')
        self.send_header('Content-Security-Policy',"default-src 'self'; script-src 'self'; style-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'")
        if cookie: self.send_header('Set-Cookie',cookie)
        self.end_headers(); self.wfile.write(body)
    def token(self):
        try:
            cookies=SimpleCookie(self.headers.get('Cookie','')); return cookies['session'].value if 'session' in cookies else ''
        except Exception: return ''
    def user(self,c):
        token=hashlib.sha256(self.token().encode()).hexdigest()
        return c.execute('SELECT u.id,u.username,u.role FROM users u JOIN sessions s ON u.id=s.user_id WHERE s.token=? AND s.expires>?',(token,time.time())).fetchone()
    def do_GET(self):
        path=self.path.split('?')[0]
        if path=='/health': return self.send(200,{'ok':True})
        files={'/':('index.html','text/html; charset=utf-8'),'/app.js':('app.js','application/javascript'),'/style.css':('style.css','text/css')}
        if path in files:
            name,kind=files[path]; return self.send(200,(STATIC/name).read_bytes(),kind)
        with connect() as c:
            u=self.user(c)
            if not u: return self.send(401,{'error':'Please sign in / लॉगिन करें'})
            if path=='/api/state':
                data={name:[dict(r) for r in c.execute('SELECT * FROM '+name+' ORDER BY id DESC')] for name in ['products','customers','invoices','payments','movements','expenses','audit']}
                data['cancellations']=[dict(r) for r in c.execute('SELECT * FROM cancellations')]
                if u['role']!='owner':
                    data['audit']=[]; data['expenses']=[]; data['movements']=[]
                data['user']=dict(u)
                data['users']=[dict(r) for r in c.execute('SELECT id,username,role FROM users')] if u['role']=='owner' else []
                return self.send(200,data)
            if path=='/api/backup' and u['role']=='owner':
                backup=sqlite3.connect(':memory:'); c.backup(backup); data=backup.serialize(); backup.close()
                return self.send(200,data,'application/octet-stream')
        self.send(404,{'error':'Not found'})
    def do_POST(self):
        if self.headers.get('Origin')!=ORIGIN or self.headers.get('X-Shop-Request')!='1': return self.send(403,{'error':'Request origin rejected'})
        try:
            size=int(self.headers.get('Content-Length',0))
            if not 0<size<=65536: raise ValueError('Request too large')
            d=json.loads(self.rfile.read(size))
            with connect() as c:
                c.execute('BEGIN IMMEDIATE')
                if self.path=='/api/login':
                    username=text(d.get('username',''),60).lower(); key=username
                    a=c.execute('SELECT * FROM attempts WHERE key=?',(key,)).fetchone()
                    if a and a['until']>time.time() and a['count']>=8: return self.send(429,{'error':'Too many attempts. Try after 15 minutes.'})
                    u=c.execute('SELECT * FROM users WHERE username=?',(username,)).fetchone()
                    correct=u and hmac.compare_digest(u['hash'],pw(str(d.get('password','')),u['salt']))
                    if not correct:
                        count=a['count']+1 if a and a['until']>time.time() else 1
                        c.execute('INSERT OR REPLACE INTO attempts VALUES(?,?,?)',(key,count,int(time.time())+900)); c.commit()
                        return self.send(401,{'error':'Incorrect username or password / लॉगिन विवरण गलत हैं'})
                    c.execute('DELETE FROM attempts WHERE key=?',(key,)); c.execute('DELETE FROM sessions WHERE expires<?',(time.time(),))
                    token=secrets.token_urlsafe(32); c.execute('INSERT INTO sessions VALUES(?,?,?)',(hashlib.sha256(token.encode()).hexdigest(),u['id'],int(time.time())+43200)); c.commit()
                    secure='; Secure' if ORIGIN.startswith('https://') else ''
                    return self.send(200,{'ok':True},cookie='session='+token+'; Path=/; HttpOnly; SameSite=Strict; Max-Age=43200'+secure)
                u=self.user(c)
                if not u: return self.send(401,{'error':'Please sign in / लॉगिन करें'})
                route=self.path; result={'ok':True}
                if route in ['/api/product','/api/purchase','/api/staff','/api/price','/api/cancel','/api/expense','/api/adjust'] and u['role']!='owner': return self.send(403,{'error':'Owner access required / मालिक का अधिकार आवश्यक'})
                if route=='/api/logout':
                    c.execute('DELETE FROM sessions WHERE token=?',(hashlib.sha256(self.token().encode()).hexdigest(),))
                elif route=='/api/product':
                    rate=money(d['tax'])
                    if rate>10000: raise ValueError('Tax rate must be between 0 and 100')
                    c.execute('INSERT INTO products(brand,name,shade,pack,stock,price,tax,low) VALUES(?,?,?,?,?,?,?,?)',tuple(text(d[k],100) for k in ['brand','name','shade','pack'])+(integer(d['stock']),money(d['price']),rate,integer(d['low'])))
                elif route=='/api/price':
                    pid=integer(d['product_id'],1); price=money(d['price'])
                    if c.execute('UPDATE products SET price=? WHERE id=?',(price,pid)).rowcount!=1: raise ValueError('Product not found')
                elif route=='/api/adjust':
                    pid=integer(d['product_id'],1); expected=integer(d['expected_stock']); target=integer(d['stock']); reason=text(d['reason'])
                    old=c.execute('SELECT stock FROM products WHERE id=?',(pid,)).fetchone()
                    if not old or old['stock']!=expected: raise ValueError('Stock changed. Refresh and recount before adjusting')
                    c.execute('UPDATE products SET stock=? WHERE id=?',(target,pid))
                    c.execute('INSERT INTO movements(request_id,product_id,quantity,cost,note,created,user_id) VALUES(?,?,?,?,?,?,?)',(text(d['request_id']),pid,target-expected,0,'Adjustment: '+reason,int(time.time()),u['id']))
                elif route=='/api/expense':
                    if not c.execute('SELECT 1 FROM expenses WHERE request_id=?',(text(d['request_id']),)).fetchone():
                        c.execute('INSERT INTO expenses(request_id,category,amount,note,created,user_id) VALUES(?,?,?,?,?,?)',(d['request_id'],text(d['category']),money(d['amount']),text(d['note']),int(time.time()),u['id']))
                elif route=='/api/cancel':
                    iid=integer(d['invoice_id'],1)
                    if not c.execute('SELECT 1 FROM cancellations WHERE invoice_id=?',(iid,)).fetchone():
                        inv=c.execute('SELECT * FROM invoices WHERE id=?',(iid,)).fetchone()
                        if not inv: raise ValueError('Bill not found')
                        if money(d['refund'])!=inv['paid']: raise ValueError('Record refund equal to all money received')
                        for line in json.loads(inv['lines']): c.execute('UPDATE products SET stock=stock+? WHERE id=?',(line['quantity'],line['id']))
                        c.execute('INSERT INTO cancellations VALUES(?,?,?,?,?)',(iid,text(d['reason']),inv['paid'],int(time.time()),u['id']))
                elif route=='/api/customer':
                    phone=str(d.get('phone','')).strip()
                    if phone and not re.fullmatch(r'[+\d ()-]{7,20}',phone): raise ValueError('Invalid phone number')
                    c.execute('INSERT INTO customers(name,phone) VALUES(?,?)',(text(d['name']),phone))
                elif route=='/api/purchase':
                    key=text(d['request_id'],100)
                    if not c.execute('SELECT 1 FROM movements WHERE request_id=?',(key,)).fetchone():
                        pid=integer(d['product_id'],1); qty=integer(d['quantity'],1)
                        if not c.execute('SELECT 1 FROM products WHERE id=?',(pid,)).fetchone(): raise ValueError('Product not found')
                        c.execute('INSERT INTO movements(request_id,product_id,quantity,cost,note,created,user_id) VALUES(?,?,?,?,?,?,?)',(key,pid,qty,money(d['cost']),text(d['note']),int(time.time()),u['id']))
                        c.execute('UPDATE products SET stock=stock+? WHERE id=?',(qty,pid))
                elif route=='/api/invoice': result=bill(c,d,u)
                elif route=='/api/payment':
                    key=text(d['request_id'],100)
                    if not c.execute('SELECT 1 FROM payments WHERE request_id=?',(key,)).fetchone():
                        inv=c.execute('SELECT * FROM invoices WHERE id=?',(integer(d['invoice_id'],1),)).fetchone(); amount=money(d['amount'])
                        if inv and c.execute('SELECT 1 FROM cancellations WHERE invoice_id=?',(inv['id'],)).fetchone(): raise ValueError('This bill was cancelled')
                        if not inv or amount<=0 or amount>inv['total']-inv['paid']: raise ValueError('Payment must be within outstanding amount')
                        c.execute('INSERT INTO payments(request_id,invoice_id,amount,created,user_id) VALUES(?,?,?,?,?)',(key,inv['id'],amount,int(time.time()),u['id']))
                        c.execute('UPDATE invoices SET paid=paid+? WHERE id=?',(amount,inv['id']))
                elif route=='/api/password':
                    current=c.execute('SELECT * FROM users WHERE id=?',(u['id'],)).fetchone()
                    if not hmac.compare_digest(current['hash'],pw(str(d['current_password']),current['salt'])): raise ValueError('Current password is incorrect')
                    password=str(d['password'])
                    if len(password)<12: raise ValueError('Use at least 12 characters')
                    salt=secrets.token_hex(16); c.execute('UPDATE users SET hash=?,salt=? WHERE id=?',(pw(password,salt),salt,u['id']))
                    c.execute('DELETE FROM sessions WHERE user_id=?',(u['id'],))
                elif route=='/api/staff':
                    username=text(d['username'],60).lower(); password=str(d['password'])
                    if len(password)<12: raise ValueError('Use a password with at least 12 characters')
                    salt=secrets.token_hex(16); c.execute('INSERT INTO users(username,hash,salt,role) VALUES(?,?,?,?)',(username,pw(password,salt),salt,'cashier'))
                else: return self.send(404,{'error':'Not found'})
                safe={k:v for k,v in d.items() if k not in ['password','current_password']}
                c.execute('INSERT INTO audit(action,details,created,user_id) VALUES(?,?,?,?)',(route,json.dumps(safe,ensure_ascii=False),int(time.time()),u['id']))
                c.commit(); self.send(200,result)
        except sqlite3.IntegrityError: self.send(409,{'error':'Duplicate entry or invalid reference / डुप्लिकेट विवरण'})
        except (ValueError,KeyError,TypeError,ArithmeticError) as e: self.send(400,{'error':str(e)})
        except Exception: self.send(500,{'error':'Server error. Refresh before retrying / सर्वर त्रुटि'})
if __name__=='__main__':
    init(); ThreadingHTTPServer(('0.0.0.0',int(os.environ.get('PORT',8080))),Handler).serve_forever()
