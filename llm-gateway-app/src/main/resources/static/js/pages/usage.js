function gatewayBaseUrl(){
  const host=location.hostname||'127.0.0.1';
  const port=parseInt(location.port,10)||(location.protocol==='https:'?443:80);
  return port===8091?location.protocol+'//'+location.host:'http://'+host+':8091';
}

function pgUsage(){
  const baseUrl=gatewayBaseUrl()+'/v1';
  const protocols=[
    {id:'chat',label:'OpenAI Chat'},
    {id:'responses',label:'OpenAI Responses'},
    {id:'anthropic',label:'Anthropic Messages'}
  ];
  const langs=['curl','javascript','typescript','java'];
  const keys=state.data.apiKeys||[];
  const models=[...new Set((state.data.channels||[]).flatMap(c=>c.models||[]))];
  if(!state._usageKey&&keys.length)state._usageKey=keys[0].key||keys[0].apiKey||'';
  if(!state._usageModel&&models.length)state._usageModel=models[0];
  if(!state._usageMsg)state._usageMsg='Hello, how are you?';
  return`<div class="page-shell">
  <div class="page-header"><div><div class="page-title">接入示例</div><div class="page-subtitle">API 对接指南与在线测试</div></div></div>
  <div class="surface" style="padding:24px;margin-bottom:16px">
    <div style="display:flex;align-items:center;gap:12px;flex-wrap:wrap">
      <span style="font-weight:700;font-size:13px">Base URL</span>
      <code style="flex:1;background:var(--slate-l);border:1px solid var(--border);border-radius:var(--rs);padding:10px 14px;font-family:monospace;font-size:13px;color:var(--foreground);min-width:200px">${esc(baseUrl)}</code>
      <button class="btn btn-secondary btn-sm" onclick="copyText('${esc(baseUrl)}')">${I.copy}复制</button>
    </div>
  </div>
  <div class="grid grid-2" style="margin-bottom:16px">
    <div class="surface" style="padding:24px">
      <div style="font-weight:700;margin-bottom:12px">在线测试</div>
      <div class="input-group"><label class="input-label">选择 API Key</label><select class="input" id="usage-key" onchange="syncUsageKey(this.value)">${keys.length?keys.map((k,i)=>`<option value="${esc(k.key||k.apiKey)}"${i===0?' selected':''}>${esc(k.name)}</option>`).join(''):'<option value="">请先创建密钥</option>'}</select></div>
      <div class="input-group"><label class="input-label">模型</label><select class="input" id="usage-model" onchange="syncUsageModel(this.value)">${models.length?models.map((m,i)=>`<option value="${esc(m)}"${i===0?' selected':''}>${esc(m)}</option>`).join(''):'<option value="">请先配置渠道模型</option>'}</select></div>
      <div class="input-group"><label class="input-label">消息内容</label><textarea class="input" id="usage-message" rows="3" oninput="syncUsageMsg(this.value)">${esc(state._usageMsg)}</textarea></div>
      <button class="btn btn-primary" onclick="usageTest()">${I.send}发送测试</button>
      <div id="usage-result" style="margin-top:14px"></div>
    </div>
    <div class="surface" style="padding:24px">
      <div style="font-weight:700;margin-bottom:12px">协议与语言</div>
      <div style="font-size:12px;font-weight:600;color:var(--muted-foreground);margin-bottom:8px">协议</div>
      <div class="tab-bar" style="margin-bottom:16px">
        ${protocols.map(p=>`<div class="tab-item ${state.usageProtocol===p.id?'active':''}" onclick="switchUsageProtocol('${p.id}')">${p.label}</div>`).join('')}
      </div>
      <div style="font-size:12px;font-weight:600;color:var(--muted-foreground);margin-bottom:8px">语言</div>
      <div class="usage-lang-tabs">
        ${langs.map(l=>`<div class="usage-lang-tab ${state.usageLang===l?'active':''}" onclick="switchUsageLang('${l}')">${l==='curl'?'cURL':l.charAt(0).toUpperCase()+l.slice(1)}</div>`).join('')}
      </div>
    </div>
  </div>
  <div class="surface" style="padding:24px">
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px"><span style="font-weight:700">代码示例</span><button class="btn btn-secondary btn-sm" onclick="copyCode()">${I.copy}复制代码</button></div>
    <div class="code-block" id="usage-code">${usageCode()}</div>
  </div></div>`;
}

function usageCode(){
  const baseUrl=gatewayBaseUrl()+'/v1';
  const key=state._usageKey||'sk-your-api-key';
  const model=state._usageModel||'gpt-4o';
  const msg=(state._usageMsg||'Hello!').replace(/'/g,"\\'");
  const q=JSON.stringify;
  const chatBody=JSON.stringify({model,messages:[{role:'user',content:msg}],stream:false});
  const chatBodyNoStream=JSON.stringify({model,messages:[{role:'user',content:msg}]});
  const respBody=JSON.stringify({model,input:msg});
  const claudeBody=JSON.stringify({model:'claude-sonnet-4-20250514',max_tokens:1024,messages:[{role:'user',content:msg}]});
  const chatPretty=`{
  "model": ${q(model)},
  "messages": [{"role": "user", "content": ${q(msg)}}],
  "stream": false
}`;
  const respPretty=`{
  "model": ${q(model)},
  "input": ${q(msg)}
}`;
  const claudePretty=`{
  "model": "claude-sonnet-4-20250514",
  "max_tokens": 1024,
  "messages": [{"role": "user", "content": ${q(msg)}}]
}`;
  if(state.usageProtocol==='chat'){
    if(state.usageLang==='curl'){
      return `<span class="kw">curl</span> <span class="str">"${baseUrl}/chat/completions"</span> \\
  -H <span class="str">"Content-Type: application/json"</span> \\
  -H <span class="str">"Authorization: Bearer ${key}"</span> \\
  -d <span class="str">'${chatPretty}'</span>`;
    }
    if(state.usageLang==='javascript'){
      return `<span class="kw">const</span> res = <span class="kw">await</span> <span class="fn">fetch</span>(<span class="str">"${baseUrl}/chat/completions"</span>, {
  method: <span class="str">"POST"</span>,
  headers: {<span class="str">"Content-Type"</span>:<span class="str">"application/json"</span>,<span class="str">"Authorization"</span>:<span class="str">"Bearer ${key}"</span>},
  body: <span class="fn">JSON</span>.<span class="fn">stringify</span>(${chatBody})
});
<span class="kw">const</span> data = <span class="kw">await</span> res.<span class="fn">json</span>();
<span class="fn">console</span>.<span class="fn">log</span>(data.choices[<span class="num">0</span>].message.content);`;
    }
    if(state.usageLang==='typescript'){
      return `<span class="kw">const</span> res = <span class="kw">await</span> <span class="fn">fetch</span>(<span class="str">"${baseUrl}/chat/completions"</span>, {
  method: <span class="str">"POST"</span>,
  headers: {<span class="str">"Content-Type"</span>:<span class="str">"application/json"</span>,<span class="str">"Authorization"</span>:<span class="str">"Bearer ${key}"</span>},
  body: <span class="fn">JSON</span>.<span class="fn">stringify</span>(${chatBodyNoStream})
});
<span class="kw">const</span> data = <span class="kw">await</span> res.<span class="fn">json</span>() <span class="kw">as</span> {choices:{message:{content:<span class="fn">string</span>}}[]};
<span class="fn">console</span>.<span class="fn">log</span>(data.choices[<span class="num">0</span>].message.content);`;
    }
    if(state.usageLang==='java'){
      return `<span class="kw">import</span> java.net.http.*;
<span class="fn">HttpClient</span> client = <span class="fn">HttpClient</span>.<span class="fn">newHttpClient</span>();
<span class="fn">HttpRequest</span> req = <span class="fn">HttpRequest</span>.<span class="fn">newBuilder</span>()
    .<span class="fn">uri</span>(<span class="fn">URI</span>.<span class="fn">create</span>(<span class="str">"${baseUrl}/chat/completions"</span>))
    .<span class="fn">header</span>(<span class="str">"Content-Type"</span>, <span class="str">"application/json"</span>)
    .<span class="fn">header</span>(<span class="str">"Authorization"</span>, <span class="str">"Bearer ${key}"</span>)
    .<span class="fn">POST</span>(<span class="fn">BodyPublishers</span>.<span class="fn">ofString</span>(<span class="str">"${chatBodyNoStream.replace(/"/g,'\\"')}"</span>))
    .<span class="fn">build</span>();
<span class="fn">System</span>.out.<span class="fn">println</span>(client.<span class="fn">send</span>(req, <span class="fn">BodyHandlers</span>.<span class="fn">ofString</span>()).<span class="fn">body</span>());`;
    }
  }
  if(state.usageProtocol==='responses'){
    if(state.usageLang==='curl'){
      return `<span class="kw">curl</span> <span class="str">"${baseUrl}/responses"</span> \\
  -H <span class="str">"Authorization: Bearer ${key}"</span> \\
  -d <span class="str">'${respPretty}'</span>`;
    }
    if(state.usageLang==='javascript'){
      return `<span class="kw">const</span> res = <span class="kw">await</span> <span class="fn">fetch</span>(<span class="str">"${baseUrl}/responses"</span>, {
  method: <span class="str">"POST"</span>,
  headers: {<span class="str">"Authorization"</span>:<span class="str">"Bearer ${key}"</span>},
  body: <span class="fn">JSON</span>.<span class="fn">stringify</span>(${respBody})
});
<span class="kw">const</span> data = <span class="kw">await</span> res.<span class="fn">json</span>();
<span class="fn">console</span>.<span class="fn">log</span>(data.output);`;
    }
    if(state.usageLang==='typescript'){
      return `<span class="kw">const</span> res = <span class="kw">await</span> <span class="fn">fetch</span>(<span class="str">"${baseUrl}/responses"</span>, {
  method: <span class="str">"POST"</span>,
  headers: {<span class="str">"Authorization"</span>:<span class="str">"Bearer ${key}"</span>},
  body: <span class="fn">JSON</span>.<span class="fn">stringify</span>(${respBody})
});
<span class="kw">const</span> data = <span class="kw">await</span> res.<span class="fn">json</span>() <span class="kw">as</span> {output:<span class="fn">any</span>};
<span class="fn">console</span>.<span class="fn">log</span>(data.output);`;
    }
    if(state.usageLang==='java'){
      return `<span class="kw">import</span> java.net.http.*;
<span class="fn">HttpClient</span> client = <span class="fn">HttpClient</span>.<span class="fn">newHttpClient</span>();
<span class="fn">HttpRequest</span> req = <span class="fn">HttpRequest</span>.<span class="fn">newBuilder</span>()
    .<span class="fn">uri</span>(<span class="fn">URI</span>.<span class="fn">create</span>(<span class="str">"${baseUrl}/responses"</span>))
    .<span class="fn">header</span>(<span class="str">"Authorization"</span>, <span class="str">"Bearer ${key}"</span>)
    .<span class="fn">POST</span>(<span class="fn">BodyPublishers</span>.<span class="fn">ofString</span>(<span class="str">"${respBody.replace(/"/g,'\\"')}"</span>))
    .<span class="fn">build</span>();
<span class="fn">System</span>.out.<span class="fn">println</span>(client.<span class="fn">send</span>(req, <span class="fn">BodyHandlers</span>.<span class="fn">ofString</span>()).<span class="fn">body</span>());`;
    }
  }
  if(state.usageProtocol==='anthropic'){
    if(state.usageLang==='curl'){
      return `<span class="kw">curl</span> <span class="str">"${baseUrl}/messages"</span> \\
  -H <span class="str">"x-api-key: ${key}"</span> \\
  -H <span class="str">"anthropic-version: 2023-06-01"</span> \\
  -d <span class="str">'${claudePretty}'</span>`;
    }
    if(state.usageLang==='javascript'){
      return `<span class="kw">const</span> res = <span class="kw">await</span> <span class="fn">fetch</span>(<span class="str">"${baseUrl}/messages"</span>, {
  method: <span class="str">"POST"</span>,
  headers: {
    <span class="str">"x-api-key"</span>:<span class="str">"${key}"</span>,
    <span class="str">"anthropic-version"</span>:<span class="str">"2023-06-01"</span>,
    <span class="str">"Content-Type"</span>:<span class="str">"application/json"</span>
  },
  body: <span class="fn">JSON</span>.<span class="fn">stringify</span>(${claudeBody})
});
<span class="kw">const</span> data = <span class="kw">await</span> res.<span class="fn">json</span>();
<span class="fn">console</span>.<span class="fn">log</span>(data.content[<span class="num">0</span>].text);`;
    }
    if(state.usageLang==='typescript'){
      return `<span class="kw">const</span> res = <span class="kw">await</span> <span class="fn">fetch</span>(<span class="str">"${baseUrl}/messages"</span>, {
  method: <span class="str">"POST"</span>,
  headers: {
    <span class="str">"x-api-key"</span>:<span class="str">"${key}"</span>,
    <span class="str">"anthropic-version"</span>:<span class="str">"2023-06-01"</span>,
    <span class="str">"Content-Type"</span>:<span class="str">"application/json"</span>
  },
  body: <span class="fn">JSON</span>.<span class="fn">stringify</span>(${claudeBody})
});
<span class="kw">const</span> data = <span class="kw">await</span> res.<span class="fn">json</span>() <span class="kw">as</span> {content:{text:<span class="fn">string</span>}[]};
<span class="fn">console</span>.<span class="fn">log</span>(data.content[<span class="num">0</span>].text);`;
    }
    if(state.usageLang==='java'){
      return `<span class="kw">import</span> java.net.http.*;
<span class="fn">HttpClient</span> client = <span class="fn">HttpClient</span>.<span class="fn">newHttpClient</span>();
<span class="fn">HttpRequest</span> req = <span class="fn">HttpRequest</span>.<span class="fn">newBuilder</span>()
    .<span class="fn">uri</span>(<span class="fn">URI</span>.<span class="fn">create</span>(<span class="str">"${baseUrl}/messages"</span>))
    .<span class="fn">header</span>(<span class="str">"x-api-key"</span>, <span class="str">"${key}"</span>)
    .<span class="fn">header</span>(<span class="str">"anthropic-version"</span>, <span class="str">"2023-06-01"</span>)
    .<span class="fn">POST</span>(<span class="fn">BodyPublishers</span>.<span class="fn">ofString</span>(<span class="str">"${claudeBody.replace(/"/g,'\\"')}"</span>))
    .<span class="fn">build</span>();
<span class="fn">System</span>.out.<span class="fn">println</span>(client.<span class="fn">send</span>(req, <span class="fn">BodyHandlers</span>.<span class="fn">ofString</span>()).<span class="fn">body</span>());`;
    }
  }
  return'';
}

function switchUsageProtocol(p){state.usageProtocol=p;updatePage('usage');fillUsageModels()}
function switchUsageLang(l){state.usageLang=l;updatePage('usage');fillUsageModels()}
function syncUsageKey(v){state._usageKey=v;updateUsageCodeBlock()}
function syncUsageModel(v){state._usageModel=v;updateUsageCodeBlock()}
function syncUsageMsg(v){state._usageMsg=v;updateUsageCodeBlock()}
function updateUsageCodeBlock(){const el=document.getElementById('usage-code');if(el)el.innerHTML=usageCode()}

async function loadUsageData(){
  // 先渲染页面骨架（此时 apiKeys/channels 可能还没加载）
  updatePage('usage');
  fillUsageModels();
  // 异步加载数据后再更新
  if(!state.data.apiKeys||!state.data.apiKeys.length){await loadApiKeys();updatePage('usage');}
  if(!state.data.channels||!state.data.channels.length){await loadChannels();}
  // channels 加载完后重新渲染（因为 pgUsage 依赖 apiKeys 列表）
  updatePage('usage');
  fillUsageModels();
}

function fillUsageModels(){
  const sel=document.getElementById('usage-model');if(!sel)return;
  const models=[...new Set((state.data.channels||[]).flatMap(c=>c.models||[]))];
  const old=sel.value;
  if(!models.length){sel.innerHTML='<option value="">请先配置渠道模型</option>';return}
  sel.innerHTML=models.map(m=>`<option value="${esc(m)}"${m===old?' selected':''}>${esc(m)}</option>`).join('');
  if(!sel.value)sel.value=models[0];
  if(!state._usageModel)state._usageModel=sel.value||models[0];
}

async function usageTest(){
  const apiKey=val('usage-key');
  const model=val('usage-model');
  const message=val('usage-message');
  state._usageKey=apiKey;state._usageModel=model;state._usageMsg=message;
  if(!apiKey){toast('请先选择 API Key','error');return}
  if(!model){toast('请输入模型名称','error');return}
  const el=document.getElementById('usage-result');
  el.innerHTML='<div style="text-align:center;padding:20px;color:var(--muted-foreground)"><span class="loading"></span> 发送中...</div>';
  try{
    const res=await fetch(API_BASE+'/v1/chat/completions',{method:'POST',headers:{'Content-Type':'application/json','Authorization':'Bearer '+apiKey},body:JSON.stringify({model,messages:[{role:'user',content:message}],stream:false})});
    const data=await res.json();
    if(data.choices&&data.choices[0]){
      el.innerHTML=`<div style="background:var(--green-l);border:1px solid var(--green);border-radius:var(--rs);padding:14px;font-size:13px;line-height:1.6"><strong style="color:var(--green)">响应:</strong> ${esc(data.choices[0].message.content)}</div><div style="font-size:11px;color:var(--muted-foreground);margin-top:6px">Tokens: ${data.usage?(data.usage.total_tokens||0):0} · 耗时: ${data.usage?.duration||'-'}ms</div>`;
    }else{
      el.innerHTML=`<div style="background:var(--red-l);border:1px solid var(--red);border-radius:var(--rs);padding:14px;font-size:13px"><strong style="color:var(--red)">错误:</strong> ${esc(JSON.stringify(data))}</div>`;
    }
  }catch(e){
    el.innerHTML=`<div style="background:var(--red-l);border:1px solid var(--red);border-radius:var(--rs);padding:14px;font-size:13px"><strong style="color:var(--red)">请求失败:</strong> ${esc(e.message)}</div>`;
  }
}

function copyCode(){
  const el=document.getElementById('usage-code')||document.querySelector('.code-block');
  if(!el)return;
  const text=el.textContent.replace('复制代码','').trim();
  copyText(text);
}
