import assert from 'node:assert/strict';
import vm from 'node:vm';
import fs from 'node:fs';
import worker from '../src/index.js';
import {SourceMetrics} from '../src/source-metrics.js';
const data = new Map();
let queue = Promise.resolve();
const state = {storage: {get: async k => structuredClone(data.get(k)), put: async (k,v) => data.set(k, structuredClone(v))},
  blockConcurrencyWhile(fn) { const job = queue.then(fn); queue = job.catch(()=>{}); return job; }};
const object = new SourceMetrics(state);
const env = {SOURCE_METRICS: {idFromName: n=>n, get: ()=>object}};
const url = 'https://worker.test/api/source-metrics';
const post = (body, origin='https://shmily0826.github.io') => worker.fetch(new Request(url,{method:'POST',headers:{origin},body:JSON.stringify(body)}),env);
assert.equal((await post({source:'reddit',event:'visit'},'https://evil.example')).status,403);
assert.equal((await post({source:'reddit',event:'bad'})).status,400);
assert.equal((await post({source:'other',event:'visit'})).status,400);
await Promise.all(Array.from({length:30},()=>post({source:'reddit',event:'visit'})));
for(const source of ['v2ex','unknown']) await post({source,event:'lanzou_click'});
const counts = (await (await worker.fetch(new Request(url),env)).json()).counts;
assert.equal(counts.reddit.visit,30);
assert.equal(counts.v2ex.lanzou_click,1);
assert.equal(counts.unknown.lanzou_click,1);
assert.deepEqual([...data.keys()],['counts']);
// Exercise the actual landing script without production requests or downloads.
const code = fs.readFileSync(new URL('../../assets/landing/source-metrics.js',import.meta.url),'utf8');
for (const src of ['reddit','v2ex','bad']) {
  const calls=[]; let click; const language={href:'https://shmily0826.github.io/Appause/zh.html'};
  const context={location:{origin:'https://shmily0826.github.io',search:`?src=${src}`},navigator:{webdriver:false},URL,URLSearchParams,
    document:{querySelectorAll:()=>[language],addEventListener:(type,fn)=>{click=fn;}},fetch:(u,o)=>{calls.push(JSON.parse(o.body));return Promise.reject(new Error('offline'));}};
  vm.runInNewContext(code,context);
  click({target:{closest:()=>({href:'https://github.com/Shmily0826/Appause/releases/download/v0.5.49/Appause-v0.5.49.apk'})}});
  assert.equal(calls[0].source,src==='bad'?'unknown':src);
  assert.equal(calls[1].event,'github_click');
  click({target:{closest:()=>({href:'https://github.com/Shmily0826/Appause/releases/latest'})}});
  assert.equal(calls[2].event,'github_click');
  const beforeUnrelated=calls.length;
  click({target:{closest:()=>({href:'https://github.com/another/repo/releases/latest'})}});
  assert.equal(calls.length,beforeUnrelated);
  assert.equal(new URL(language.href).searchParams.get('src'),calls[0].source);
  const before=calls.length;context.navigator.webdriver=true;vm.runInNewContext(code,context);assert.equal(calls.length,before);
}
console.log('PASS source validation, concurrent increments, independent totals, offline downloads, source propagation and preview exclusion');
