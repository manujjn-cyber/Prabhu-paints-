const DATA = [];
// ==========================================

DATA.forEach((d,i)=>{ d.__idx = i; });

const BRANDS = Array.from(new Set(DATA.map(d=>d.brand)));
const BRAND_COLORS = {};
const PALETTE = ['#e2231a','#0a6b3f','#0f4c81','#8a5a00','#6a3fb5','#b3261e'];
BRANDS.forEach((b,i)=>{ BRAND_COLORS[b] = PALETTE[i % PALETTE.length]; });

/* ---------- rebate groups: fully user-editable list, checked top-to-bottom, first match wins ----------
   Each group has one or more "keyword phrases" (OR'd together). A phrase can contain several
   words separated by spaces -- ALL of those words must appear somewhere in the product name
   (in any order) for that phrase to match, e.g. "ENAMEL WHITE" needs both ENAMEL and WHITE present.
   Anything matching no group at all falls into the built-in "Other" bucket. */
const GROUPS_SEED = [
  { name:'Enamel White',                                keywords:['ENAMEL WHITE'], rebate:0 },
  { name:'Enamel Colour',                                keywords:['ENAMEL'], rebate:0 },
  { name:'Apex Advance',                                 keywords:['APEX ADVANCE','APEX ADVANCED'], rebate:0 },
  { name:'Apex',                                         keywords:['APEX'], rebate:0 },
  { name:'Ace Advance / Tractor Emulsion Advance',       keywords:['ACE ADVANCE','ACE ADVANCED','TRACTOR EMULSION ADVANCE','TRACTOR EMULSION ADVANCED'], rebate:0 },
  { name:'Ace / Tractor Emulsion',                       keywords:['ACE','TRACTOR EMULSION'], rebate:0 },
  { name:'Stainer / SC Repair Polymer / Repair Max',     keywords:['STAINER','REPAIR POLYMER','REPAIRMAX'], rebate:0 },
  { name:'MTO & Uno / Neo Bharat',                       keywords:['MTO','UNO','NEO BHARAT'], rebate:0 },
  { name:'Mica Marble Stretch & Sheen',                  keywords:['MICA MARBLE STRETCH','MICA MARBLE SHEEN'], rebate:0 },
  { name:'Mica Marble',                                  keywords:['MICA MARBLE'], rebate:0 },
  { name:'Excel',                                        keywords:['EXCEL'], rebate:0 },
  { name:'Suraksha Plus',                                keywords:['SURAKSHA PLUS'], rebate:0 },
  { name:'Suraksha',                                     keywords:['SURAKSHA'], rebate:0 },
  { name:'Oil Based Primer',                             keywords:['PRIMER OIL','OIL PRIMER'], rebate:0 },
  { name:'Primer',                                       keywords:['PRIMER'], rebate:0 }
];
const OTHER_GROUP_NAME = 'Other';

const GROUP_COLOR_PALETTE = [
  { bg:'#fdeceb', fg:'#b3261e' },
  { bg:'#eef1f6', fg:'#3a4a63' },
  { bg:'#e8f6ee', fg:'#0a6b3f' },
  { bg:'#f1ecfb', fg:'#6a3fb5' },
  { bg:'#e7f5f6', fg:'#0f6d75' },
  { bg:'#fbf1e3', fg:'#8a5a00' },
  { bg:'#fdf0f6', fg:'#a3175e' },
  { bg:'#eef7e3', fg:'#3f6b12' },
  { bg:'#f3ecec', fg:'#7a3b3b' },
  { bg:'#eceef3', fg:'#3b4a7a' }
];
const OTHER_COLOR = { bg:'#eeece4', fg:'#6b6a62' };
function groupColor(name){
  if(name === OTHER_GROUP_NAME) return OTHER_COLOR;
  let hash = 0;
  for(let i=0;i<name.length;i++){ hash = (hash*31 + name.charCodeAt(i)) >>> 0; }
  return GROUP_COLOR_PALETTE[hash % GROUP_COLOR_PALETTE.length];
}

function escapeRegex(s){ return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'); }
function hasWord(nameUpper, word){
  return new RegExp('\\b'+escapeRegex(word)+'\\b').test(nameUpper);
}
function matchesPhrase(nameUpper, phrase){
  const words = phrase.toUpperCase().split(/\s+/).filter(Boolean);
  if(words.length===0) return false;
  return words.every(w=>hasWord(nameUpper,w));
}
function classifyGroup(name){
  const n = String(name).toUpperCase();
  for(const g of settings.groups){
    if(g.keywords.some(kw=>matchesPhrase(n,kw))) return g.name;
  }
  return OTHER_GROUP_NAME;
}
function getGroupRebate(groupName){
  if(groupName === OTHER_GROUP_NAME) return Number(settings.otherRebate)||0;
  const g = settings.groups.find(x=>x.name===groupName);
  return g ? (Number(g.rebate)||0) : 0;
}

/* ---------- Decolite (Decora): brand-specific tax + percentage rebate scheme ----------
   Decora gets its own fixed tax rate (default 18%) and a rebate that's a % OFF the
   after-tax price (not a flat ₹/unit like the other brands), varying by shade category. */
const DECORA_BRAND = 'Decolite (Decora)';
const DECORA_SHADE_RATES_SEED = {
  oxaBlueOrange: 4,   // Oxford Blue & Orange shades
  allColours:    7,   // every other colour not called out separately
  white:         11,  // White / Base White / Ultra White
  black:         14,  // Black
  pinkFamily:    5,   // Pink, Wild Purple, Bright Blue, Magenta, Imperial Crimson
  signalRed:     5    // Signal Red
};
const DECORA_GROUP_LABELS = {
  oxaBlueOrange: 'Oxford Blue & Orange',
  allColours:    'All Colours',
  white:         'White',
  black:         'Black',
  pinkFamily:    'Pink / Wild Purple / Bright Blue / Magenta / Imperial Crimson',
  signalRed:     'Signal Red'
};
function classifyDecoraShade(name){
  const n = String(name).toUpperCase();
  if(n.indexOf('OXFORD') !== -1 || n.indexOf('ORANGE') !== -1) return 'oxaBlueOrange';
  if(n.indexOf('BLACK') !== -1) return 'black';
  if(n.indexOf('SIGNAL RED') !== -1) return 'signalRed';
  if(n.indexOf('WHITE') !== -1) return 'white';
  if(n.indexOf('PINK') !== -1 || n.indexOf('WILD PURPLE') !== -1 || n.indexOf('BRIGHT BLUE') !== -1 ||
     n.indexOf('MAGENTA') !== -1 || n.indexOf('CRIMSON') !== -1) return 'pinkFamily';
  return 'allColours';
}
function getDecoraRebatePercent(shadeKey){
  const v = settings.decoraShadeRates && settings.decoraShadeRates[shadeKey];
  return (typeof v === 'number' && !isNaN(v)) ? v : (DECORA_SHADE_RATES_SEED[shadeKey]||0);
}

/* ---------- persisted, fully editable settings ---------- */
const SETTINGS_KEY = 'dpl_settings_v4';

/* keeps: any custom groups the user added, and rebate values already set on known groups.
   adds: any brand-new seed groups (e.g. Excel, Mica Marble) that didn't exist in an older save. */
function mergeGroups(savedGroups){
  if(!Array.isArray(savedGroups) || savedGroups.length===0){
    return GROUPS_SEED.map(g=>Object.assign({}, g, { keywords: g.keywords.slice() }));
  }
  const savedByName = {};
  savedGroups.forEach(g=>{ if(g && g.name) savedByName[g.name] = g; });
  const seedNames = new Set(GROUPS_SEED.map(g=>g.name));
  const customGroups = savedGroups.filter(g=>g && g.name && !seedNames.has(g.name));
  const mergedSeed = GROUPS_SEED.map(seed=>{
    const existing = savedByName[seed.name];
    return existing
      ? { name:seed.name, keywords: Array.isArray(existing.keywords) ? existing.keywords : seed.keywords.slice(), rebate: (typeof existing.rebate==='number') ? existing.rebate : seed.rebate }
      : Object.assign({}, seed, { keywords: seed.keywords.slice() });
  });
  return customGroups.concat(mergedSeed);
}

function loadSettings(){
  try{
    const raw = localStorage.getItem(SETTINGS_KEY);
    if(raw){
      const p = JSON.parse(raw);
      return {
        taxPercent: (typeof p.taxPercent === 'number') ? p.taxPercent : 12.5,
        groups: mergeGroups(p.groups),
        otherRebate: (typeof p.otherRebate === 'number') ? p.otherRebate : 0,
        dealerOverrides: p.dealerOverrides || {},
        decoraTaxPercent: (typeof p.decoraTaxPercent === 'number') ? p.decoraTaxPercent : 18,
        decoraShadeRates: Object.assign({}, DECORA_SHADE_RATES_SEED, (p.decoraShadeRates && typeof p.decoraShadeRates === 'object') ? p.decoraShadeRates : {}),
        decoraFreightRate: (typeof p.decoraFreightRate === 'number') ? p.decoraFreightRate : 8.8
      };
    }
  }catch(e){}
  return {
    taxPercent: 12.5,
    groups: GROUPS_SEED.map(g=>Object.assign({}, g, { keywords: g.keywords.slice() })),
    otherRebate: 0,
    dealerOverrides: {},
    decoraTaxPercent: 18,
    decoraShadeRates: Object.assign({}, DECORA_SHADE_RATES_SEED),
    decoraFreightRate: 8.8
  };
}
function saveSettings(){
  try{ localStorage.setItem(SETTINGS_KEY, JSON.stringify(settings)); }catch(e){}
}
let settings = loadSettings();

function isTaxable(brand){
  return brand === 'Asian Paints' || brand.indexOf('Nerolac') === 0 || brand === DECORA_BRAND;
}
function getBrandTaxPercent(brand){
  if(brand === DECORA_BRAND) return Number(settings.decoraTaxPercent)||0;
  return Number(settings.taxPercent)||0;
}
function parsePackNumber(pack){
  if(typeof pack === 'number') return pack;
  const s = String(pack).trim().toUpperCase();
  // "2 X 10 L" style multi-packs: multiply the two numbers, then apply unit
  let m = s.match(/^(\d+(?:\.\d+)?)\s*[X×]\s*(\d+(?:\.\d+)?)\s*(ML|LTR|KG|L|K)?/);
  if(m){
    let val = parseFloat(m[1]) * parseFloat(m[2]);
    if(m[3]==='ML') val = val/1000;
    return isNaN(val) ? 0 : val;
  }
  // plain "500 ML" / "0.5 L" / "5KG" / "1 L/KG" style single values
  m = s.match(/^(\d+(?:\.\d+)?)\s*(ML|LTR|KG|L|K)?/);
  if(m){
    let val = parseFloat(m[1]);
    if(m[2]==='ML') val = val/1000;
    return isNaN(val) ? 0 : val;
  }
  const f = parseFloat(s);
  return isNaN(f) ? 0 : f;
}
function fmtPrice(v){
  if(v===null||v===undefined||v==='') return '';
  const n = Number(v);
  if(Number.isNaN(n)) return v;
  return n.toLocaleString('en-IN',{minimumFractionDigits:2, maximumFractionDigits:2});
}
function fmtPack(v){
  if(v===null||v===undefined||v==='') return '';
  if(typeof v === 'number'){
    return (Number.isInteger(v) ? v.toString() : v.toFixed(2).replace(/0$/,'').replace(/\.$/,''));
  }
  return v;
}
function escapeHtml(s){
  return String(s).replace(/[&<>"']/g, c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
}

/* dealer rate for an item, honouring any saved override */
function getDealerRate(item){
  const ov = settings.dealerOverrides[String(item.__idx)];
  return (ov !== undefined && ov !== null && ov !== '') ? Number(ov) : Number(item.price);
}

/* rebate is calculated per the item's OWN pack size, using its group's rate:
   rebate = the matched group's rate (₹ per 1 L/Kg) × pack size
   e.g. Enamel Colour rate 40 → pack 0.05 → rebate = 40 × 0.05 = ₹2 */
function calculatePrices(item){
  const dealerRate = getDealerRate(item);
  const isDecora = item.brand === DECORA_BRAND;
  const taxable = isTaxable(item.brand);
  const taxPercent = taxable ? getBrandTaxPercent(item.brand) : 0;
  const taxAmount = taxable ? (dealerRate * taxPercent) / 100 : 0;
  const afterTax = dealerRate + taxAmount;
  const packNum = parsePackNumber(item.pack);

  let group, rebateRate, rebateAmount, rebatePercent;
  if(isDecora){
    group = classifyDecoraShade(item.name);
    rebatePercent = taxable ? getDecoraRebatePercent(group) : 0;
    rebateAmount = afterTax * rebatePercent / 100;
    rebateRate = null;
  } else {
    group = classifyGroup(item.name);
    rebateRate = taxable ? getGroupRebate(group) : 0;
    rebateAmount = rebateRate * packNum;
    rebatePercent = null;
  }
  /* Decora-only freight add-on: ₹/L(or Kg) × pack size, added back on top after the rebate deduction */
  const freightRate = isDecora ? (Number(settings.decoraFreightRate)||0) : 0;
  const freightAmount = isDecora ? (freightRate * packNum) : 0;
  const finalPrice = afterTax - rebateAmount + freightAmount;
  return { dealerRate, taxable, taxPercent, taxAmount, afterTax, group, rebateRate, rebateAmount, rebatePercent, freightRate, freightAmount, finalPrice, isDecora };
}

