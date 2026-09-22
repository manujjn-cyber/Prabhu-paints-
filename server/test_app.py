import unittest,tempfile,threading,json,urllib.request,urllib.error,http.cookiejar,uuid,os
from concurrent.futures import ThreadPoolExecutor
import app
class ShopTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();app.DB=self.tmp.name+'/test.db';os.environ['ADMIN_PASSWORD']='test-owner-only-123';app.init()
        self.server=app.ThreadingHTTPServer(('127.0.0.1',0),app.Handler);self.url='http://127.0.0.1:'+str(self.server.server_port);app.ORIGIN=self.url
        self.thread=threading.Thread(target=self.server.serve_forever,daemon=True);self.thread.start()
        self.owner=self.client();self.request('/api/login',{'username':'owner','password':os.environ['ADMIN_PASSWORD']})
    def tearDown(self): self.server.shutdown();self.server.server_close();self.thread.join();self.tmp.cleanup()
    def client(self): return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
    def request(self,path,data=None,client=None,origin=None):
        req=urllib.request.Request(self.url+path,data=json.dumps(data).encode() if data is not None else None,headers={'Origin':origin or self.url,'Content-Type':'application/json','X-Shop-Request':'1'})
        try:
            with (client or self.owner).open(req) as r: return r.status,json.loads(r.read())
        except urllib.error.HTTPError as e:return e.code,json.loads(e.read())
    def product(self,stock=5,price='100',name='Enamel'):
        self.assertEqual(self.request('/api/product',dict(brand='Decora',name=name,shade='White',pack='1 L',stock=stock,price=price,tax='18',low=2))[0],200)
        return self.request('/api/state')[1]['products'][0]['id']
    def sale(self,pid,qty=1,paid='118',key=None,customer=None,price=10000):return self.request('/api/invoice',dict(request_id=key or str(uuid.uuid4()),customer_id=customer,paid=paid,lines=[dict(product_id=pid,quantity=qty,expected_price=price)]))
    def test_auth_and_origin(self):
        self.assertEqual(self.request('/api/state',client=self.client())[0],401)
        self.assertEqual(self.request('/api/customer',{'name':'X'},origin='https://evil.example')[0],403)
    def test_invoice_retry_is_once(self):
        pid=self.product();a=self.sale(pid,key='same');b=self.sale(pid,key='same');self.assertEqual(a,b)
        s=self.request('/api/state')[1];self.assertEqual(s['products'][0]['stock'],4);self.assertEqual(len(s['invoices']),1);self.assertEqual(s['invoices'][0]['total'],11800)
    def test_two_phones_cannot_oversell(self):
        pid=self.product(stock=1)
        with ThreadPoolExecutor(2) as ex:results=list(ex.map(lambda _: self.sale(pid),range(2)))
        self.assertEqual(sorted(r[0] for r in results),[200,400]);self.assertEqual(self.request('/api/state')[1]['products'][0]['stock'],0)
    def test_rollback_on_invalid_second_line(self):
        pid=self.product();d={'request_id':'rollback','paid':'236','lines':[{'product_id':pid,'quantity':1,'expected_price':10000},{'product_id':999,'quantity':1,'expected_price':10000}]}
        self.assertEqual(self.request('/api/invoice',d)[0],400);self.assertEqual(self.request('/api/state')[1]['products'][0]['stock'],5)
    def test_credit_and_payment_retry(self):
        pid=self.product();self.assertEqual(self.sale(pid,paid='0')[0],400)
        self.request('/api/customer',{'name':'Test Customer','phone':'9999999999'});cid=self.request('/api/state')[1]['customers'][0]['id'];iid=self.sale(pid,paid='18',customer=cid)[1]['id']
        payment={'request_id':'pay','invoice_id':iid,'amount':'100'}
        self.assertEqual(self.request('/api/payment',payment)[0],200);self.assertEqual(self.request('/api/payment',payment)[0],200)
        self.assertEqual(self.request('/api/state')[1]['invoices'][0]['paid'],11800)
        self.assertEqual(self.request('/api/payment',{**payment,'request_id':'over','amount':'1'})[0],400)
    def test_cancellation_restores_once(self):
        pid=self.product();iid=self.sale(pid)[1]['id'];cancel={'invoice_id':iid,'reason':'All items returned','refund':'118'}
        self.assertEqual(self.request('/api/cancel',cancel)[0],200);self.assertEqual(self.request('/api/cancel',cancel)[0],200)
        self.assertEqual(self.request('/api/state')[1]['products'][0]['stock'],5)
        self.assertEqual(self.request('/api/payment',{'request_id':'no','invoice_id':iid,'amount':'1'})[0],400)
    def test_staff_cannot_change_stock_or_prices(self):
        pid=self.product();self.request('/api/staff',{'username':'staff','password':'staff-testing-123'});cl=self.client();self.request('/api/login',{'username':'staff','password':'staff-testing-123'},client=cl)
        self.assertEqual(self.request('/api/price',{'product_id':pid,'price':'1'},client=cl)[0],403)
        self.assertEqual(self.request('/api/cancel',{},client=cl)[0],403)
        self.assertEqual(self.request('/api/state',client=cl)[1]['audit'],[])
    def test_stale_price_requires_review(self):
        pid=self.product();self.request('/api/price',{'product_id':pid,'price':'110'});self.assertEqual(self.sale(pid)[0],400)
        self.assertEqual(self.request('/api/state')[1]['products'][0]['stock'],5)
    def test_purchase_and_expense_retries(self):
        pid=self.product();d={'request_id':'purchase','product_id':pid,'quantity':3,'cost':'200','note':'Supplier bill 1'}
        for _ in range(2):self.assertEqual(self.request('/api/purchase',d)[0],200)
        self.assertEqual(self.request('/api/state')[1]['products'][0]['stock'],8)
        for _ in range(2):self.request('/api/expense',{'request_id':'expense','category':'Freight','amount':'25','note':'Delivery'})
        self.assertEqual(len(self.request('/api/state')[1]['expenses']),1)
    def test_invalid_money_and_duplicate_products(self):
        self.product();self.assertEqual(self.request('/api/product',dict(brand='Decora',name='Enamel',shade='White',pack='1 L',stock=5,price='100',tax='18',low=2))[0],409)
        for value in ['NaN','-1','1.001','Infinity']:
            with self.assertRaises((ValueError,ArithmeticError)):app.money(value)
if __name__=='__main__':unittest.main(verbosity=2)
