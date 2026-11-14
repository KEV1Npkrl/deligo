const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
function screen(){
 const nodes=new Map();
 const node=s=>{if(!nodes.has(s))nodes.set(s,{innerHTML:'',textContent:'',hidden:false,open:false,addEventListener(){},classList:{toggle(){},add(){},remove(){}},showModal(){this.open=true;},close(){this.open=false;},focus(){}});return nodes.get(s);};
 const context=vm.createContext({document:{querySelector:node,querySelectorAll:()=>[],addEventListener(){}},window:{addEventListener(){}},location:{hash:'#cart'},crypto:require('node:crypto').webcrypto,Intl,URLSearchParams,setTimeout:()=>0,clearTimeout(){},deliveryArt:'',welcomeArt:''});
 const code=fs.readFileSync(path.join(__dirname,'../../main/resources/static/app.js'),'utf8').replace(/^import .*;\r?\n/,'').replace(/\ninit\(\);\s*$/,'');
 vm.runInContext(code,context);return code=>vm.runInContext(code,context);
}
test('cart renders quantity totals and escapes product names',()=>{
 const run=screen();
 run(`state.cart=[{id:'a',name:'<img onerror=x>',price:12.5,quantity:2}];state.shops=[];`);
 const html=run('cartContent()');
 assert.match(html,/25\.00/);assert.match(html,/&lt;img onerror=x&gt;/);assert.doesNotMatch(html,/<img onerror/);
});
test('Yape preparation remains unavailable until verification',()=>{
 const run=screen();run(`state.user={user_role:'MERCHANT'};`);
 const input={order_status:'PENDING',payment_method:'YAPE',payment_status:'VERIFYING',review:[]};
 assert.doesNotMatch(run(`orderActions(${JSON.stringify(input)})`),/data-value="PREPARE"/);
 input.payment_status='PAID';assert.match(run(`orderActions(${JSON.stringify(input)})`),/data-value="PREPARE"/);
});
test('cash settlement is shown only after rendering and disappears when settled',()=>{
 const run=screen();run(`state.user={user_role:'ADMIN'};`);
 const input={order_status:'DELIVERED',payment_method:'CASH',payment_status:'PAID',review:[]};
 assert.doesNotMatch(run(`orderActions(${JSON.stringify(input)})`),/data-value="SETTLE"/);
 input.cash_rendered_at='2026-11-11';assert.match(run(`orderActions(${JSON.stringify(input)})`),/data-value="SETTLE"/);
 input.settled_at='2026-11-11';assert.doesNotMatch(run(`orderActions(${JSON.stringify(input)})`),/data-value="SETTLE"/);
});
test('product detail keeps availability and escapes API descriptions',()=>{
 const run=screen();run(`state.products=[{id:'a',merchant_id:'shop',name:'Producto',description:'<script>x</script>',price:10,stock:0}];state.shops=[{id:'shop',name:'Comercio',is_open:true,prep_minutes:15}];productDetail('a');`);
 const html=run(`$('#modal-body').innerHTML`);
 assert.match(html,/&lt;script&gt;/);assert.match(html,/disabled/);assert.match(html,/10\.00/);
});
