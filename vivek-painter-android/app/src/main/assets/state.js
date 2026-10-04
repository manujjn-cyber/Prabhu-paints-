const BUSINESS={name:"VIVEK KUMAR PAINTER",tag:"PREMIUM PAINT & COATING SERVICES",address:"BUSINESS_ADDRESS",phone:"BUSINESS_PHONE"};
const works=[
["Interior Painting","इंटीरियर पेंटिंग","Tractor / Premium Gloss / Royal Luxury Emulsion (Asian, Berger, Opus)"],
["Exterior Painting","एक्सटीरियर बाहरी पेंट","Weather-proof Apex / Ultima / Dust-proof Exterior Coating"],
["Surface Prep: Putty & Primer","पुट्टी व प्राइमर","2 Coats Waterproof Putty + Primer Base Coat (Birla / JK / Asian)"],
["Texture, Metallic & Stencil Art","टेक्सचर / डिजाइन","Royal Play Texture / Metallic Finish / Stencil Wall Design"],
["Waterproofing & Damp Treatment","सीलन उपचार","Wall Seepage Repair / Damp Block / Roof Waterproofing"],
["Wood Polish & Enamel Painting","लकड़ी पॉलिश व पेंट","Doors/Windows: PU Polish / Melamine / Spirit Polish / Synthetic Enamel"],
["Metal, Gate & Grill Painting","लोहा / ग्रिल पेंट","Anti-rust Red Oxide Primer + Double Coat Gloss Enamel"],
["POP Punning, Crack Seal & Site Prep","पीओपी व तैयारी","Wall POP / Crack Seal Repair / Floor Protection"]
];
const rateOptions=["Lumpsum / एकमुश्त","Per Sqft / प्रति फुट","Daily Wages / दिहाड़ी","Per Room / Wall","Per Door / Window","Per Running Ft","Per Item Basis","Job Basis"];
const defaultTerms=["Payment: Booking advance, stage payment and final payment must be recorded clearly.","Materials: Only approved brands/material will be used.","Site Facilities: Electricity, clean water and access/scaffolding by client unless agreed otherwise.","Quote Validity: Normally valid for 15 days.","Protection: Furniture and valuable items should be shifted/protected before work.","Weather Delay: Rain, humidity or site conditions may affect completion time.","Seepage: Active plumbing leakage must be corrected before damp treatment.","Color Finalization: Final shade should be approved before material purchase."];
let store={quotations:[],invoices:[],workers:[],attendance:[],settings:{gstDefault:true},nextQ:1,nextI:1,nextWorker:1};
let currentPage="home",editingQ=null,editingI=null;
function native(){return window.Android||null}
function loadStore(){try{const s=native()?.loadStore?.();if(s&&s!=="{}")store=Object.assign(store,JSON.parse(s));else{const l=localStorage.getItem("vp_store");if(l)store=Object.assign(store,JSON.parse(l))}}catch(e){}ensureStore();const c=document.getElementById("businessContact");if(c)c.textContent=BUSINESS.address+" • "+BUSINESS.phone}
function ensureStore(){for(const k of ["quotations","invoices","workers","attendance"])if(!Array.isArray(store[k]))store[k]=[];store.settings=store.settings||{gstDefault:true};store.nextQ=store.nextQ||1;store.nextI=store.nextI||1;store.nextWorker=store.nextWorker||1}
function persist(){const s=JSON.stringify(store);try{native()?.saveStore?.(s)}catch(e){}try{localStorage.setItem("vp_store",s)}catch(e){}}
function esc(v=""){return String(v).replace(/[&<>"']/g,m=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[m]))}
function money(n){return "₹ "+Number(n||0).toLocaleString("en-IN",{maximumFractionDigits:2})}
function today(){return new Date().toISOString().slice(0,10)}
function v(id){return document.getElementById(id)?.value||""} function num(id){return Number(v(id)||0)}
function qNo(){return "VKP-Q-"+String(store.nextQ).padStart(4,"0")} function iNo(){return "VKP-I-"+String(store.nextI).padStart(4,"0")}
function toast(t){const x=document.getElementById("toast");x.textContent=t;x.classList.add("show");setTimeout(()=>x.classList.remove("show"),2200)}
function setPage(p){currentPage=p;document.querySelectorAll(".nav").forEach(b=>b.classList.toggle("active",b.dataset.page===p));render()}
document.querySelectorAll(".nav").forEach(b=>b.onclick=()=>setPage(b.dataset.page));
function render(){const page=document.getElementById("page");if(currentPage==="home")page.innerHTML=homeView();else if(currentPage==="quotation")page.innerHTML=quotationView();else if(currentPage==="invoice")page.innerHTML=invoiceView();else if(currentPage==="labor")page.innerHTML=laborView();else if(currentPage==="history")page.innerHTML=historyView();else page.innerHTML=backupView();bindPage()}
function bindPage(){if(currentPage==="labor")syncLaborRate()}
function homeView(){const bal=store.invoices.reduce((a,x)=>a+Number(x.balance||0),0),lab=typeof laborTotals==="function"?laborTotals():{remaining:0};return `<div class="grid grid2"><div class="metric"><span>Total Quotations / कुल कोटेशन</span><b>${store.quotations.length}</b></div><div class="metric"><span>Total Invoices / कुल बिल</span><b>${store.invoices.length}</b></div><div class="metric"><span>Customer Outstanding / ग्राहक बकाया</span><b>${money(bal)}</b></div><div class="metric"><span>Labour Remaining / मजदूर शेष</span><b>${money(lab.remaining)}</b></div></div><div class="sectionTitle"><h2>Quick Actions</h2><span>तेज़ कार्य</span></div><div class="card"><div class="btns"><button class="btn primary" onclick="newQuotation()">＋ New Quotation</button><button class="btn gold" onclick="newInvoice()">＋ New Invoice</button><button class="btn secondary" onclick="setPage('labor')">👷 Labour & Attendance</button><button class="btn secondary" onclick="setPage('history')">History</button><button class="btn green" onclick="setPage('backup')">☁ Backup</button></div></div><div class="sectionTitle"><h2>Recent Documents</h2><span>हाल के दस्तावेज</span></div>${recentDocs()}`}
function recentDocs(){let all=[...store.quotations.map(x=>({...x,_type:"Quotation"})),...store.invoices.map(x=>({...x,_type:"Invoice"}))].sort((a,b)=>(b.updatedAt||"").localeCompare(a.updatedAt||"")).slice(0,5);if(!all.length)return '<div class="card empty">No quotation or invoice yet.</div>';return all.map(x=>`<div class="historyItem"><div class="historyTop"><div><span class="tag">${x._type}</span><div style="font-weight:900;margin-top:4px">${esc(x.client||"Unnamed Client")}</div><div class="small muted">${esc(x.no||"")} · ${esc(x.date||"")}</div></div><div class="money">${money(x.total)}</div></div></div>`).join("")}
function newQuotation(){editingQ=null;setPage("quotation")} function newInvoice(){editingI=null;setPage("invoice")}
