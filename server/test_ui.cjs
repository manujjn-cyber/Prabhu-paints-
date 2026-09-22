// Lightweight rendering checks; not a replacement for Android/browser device testing.
const vm=require('node:vm'),fs=require('node:fs'),assert=require('node:assert/strict');
const element={innerHTML:'',addEventListener(){},className:'',textContent:''};
const context={console,Intl,Date,JSON,String,Number,Object,Array,Math,Error,Map,Set,Promise,setTimeout(){},setInterval(){},localStorage:{getItem(){return null},setItem(){}},document:{documentElement:{},querySelector(){return element}},fetch:async()=>({ok:true,json:async()=>({})})};
vm.createContext(context);
let code=fs.readFileSync(__dirname+'/static/app.js','utf8').replace("refresh().catch(()=>{state=null;render()});",'');vm.runInContext(code,context);
vm.runInContext(`state={user:{username:'owner',role:'owner'},users:[],products:[{id:1,brand:'Decora',name:'<script>alert(1)</script>',shade:'White',pack:'1 kg',stock:5,price:10000,tax:1800,low:2}],customers:[{id:1,name:'Demo',phone:''}],invoices:[{id:1,customer_id:1,total:11800,paid:1800,subtotal:10000,tax:1800,created:1700000000,lines:JSON.stringify([{id:1,brand:'Decora',name:'Enamel',shade:'White',pack:'1 kg',quantity:1,price:10000,base:10000,line_tax:1800}])}],payments:[],cancellations:[],movements:[],expenses:[],audit:[]};`,context);
for(const language of ['en','hi'])for(const page of ['dashboard','stock','billing','sales','customers','purchases','reports','settings']){
 vm.runInContext(`lang='${language}';tab='${page}';render();`,context);
 assert(element.innerHTML.includes('<main>'));assert(!element.innerHTML.includes('<script>alert(1)</script>'));
}
vm.runInContext('receiptId=1;render()',context);assert(element.innerHTML.includes('PP-00001'));
console.log('PASS: 16 page/language render checks, escaped product text, bill rendering. No visual or device testing performed.');
