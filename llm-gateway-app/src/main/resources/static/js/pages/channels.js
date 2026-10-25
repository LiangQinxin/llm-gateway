function pgChannels(){
  const chs=state.data.channels||[];
  return`<div class="page-shell">
  <div class="page-header"><div><div class="page-title">渠道管理</div><div class="page-subtitle">配置 LLM 供应商渠道 · 负载均衡 · 故障转移</div></div>
    <div class="page-header-actions"><button class="btn btn-secondary btn-sm" onclick="loadChannels()">${I.refresh}刷新</button><button class="btn btn-primary" onclick="showChannelModal()">${I.plus}添加渠道</button></div>
  </div>
  <div class="surface" style="overflow:hidden"><div class="table-wrap">
    <table class="table"><thead><tr><th>名称</th><th>类型</th><th>Base URL</th><th>模型</th><th>优先级</th><th>权重</th><th>状态</th><th>操作</th></tr></thead><tbody>
    ${chs.map(ch=>`<tr>
      <td style="font-weight:600">${esc(ch.name)}</td>
      <td><span class="badge badge-blue">${esc(ch.type)}</span></td>
      <td><span class="ch-url" title="${esc(ch.baseUrl||'')}">${esc(ch.baseUrl||'-')}</span></td>
      <td><div class="ch-models">${(ch.models||[]).slice(0,3).map(m=>`<span class="tag">${esc(m)}</span>`).join('')}${(ch.models||[]).length>3?`<span class="tag" title="${esc((ch.models||[]).join(', '))}+${ch.models.length-3}</span>`:''||'-'}</div></td>
      <td><span style="font-variant-numeric:tabular-nums">${ch.priority??0}</span></td>
      <td><span style="font-variant-numeric:tabular-nums">${ch.weight??1}</span></td>
      <td><div class="switch ${ch.status===1?'on':''}" onclick="toggleChannelStatus('${ch.id}',${ch.status===1?0:1})" title="${ch.status===1?'点击禁用':'点击启用'}"><div class="switch-knob"></div></div></td>
      <td><div class="row-actions">
        <button class="btn btn-secondary btn-sm btn-icon" onclick="testChannel('${ch.id}')" title="测试连接">${I.test}</button>
        <button class="btn btn-secondary btn-sm btn-icon" onclick="showChannelModal('${ch.id}')" title="编辑">${I.edit}</button>
        <button class="btn btn-danger btn-sm btn-icon" onclick="confirmDel('渠道','${esc(ch.name)}',()=>delChannel('${ch.id}'))" title="删除">${I.trash}</button>
      </div></td>
    </tr>`).join('')||emptyRow(8,'暂无渠道','点击右上角添加渠道')}
    </tbody></table>
  </div></div></div>`;
}

async function loadChannels(){const r=await api('/channels');if(r.code==='0000')state.data.channels=r.data||[];updatePage('channels');if(state.activeTab==='usage')fillUsageModels()}

function showChannelModal(id){
  const ch=id?(state.data.channels||[]).find(c=>c.id===id):null;
  const title=id?'编辑渠道':'添加渠道';
  showModal(`<div class="modal-header"><div class="modal-title">${title}</div></div>
  <div class="modal-body">
  <div class="input-group"><label class="input-label">渠道名称</label><input class="input" id="ch-name" value="${ch?esc(ch.name):''}" placeholder="如：OpenAI 官方"></div>
  <div class="input-group"><label class="input-label">渠道类型</label><select class="input" id="ch-type">
  ${['openai','deepseek','anthropic','gemini','qwen','zhipu','moonshot','doubao','ollama','custom'].map(t=>`<option value="${t}" ${ch&&ch.type===t?'selected':''}>${t.charAt(0).toUpperCase()+t.slice(1)}</option>`).join('')}
  </select></div>
  <div class="input-group"><label class="input-label">Base URL</label><input class="input" id="ch-url" value="${ch?esc(ch.baseUrl||''):''}" placeholder="https://api.openai.com/v1"></div>
  <div class="input-group"><label class="input-label">API Key</label><input class="input" type="password" id="ch-key" value="${ch?esc(ch.apiKey||''):''}" placeholder="sk-..."><div class="input-hint">供应商密钥，加密存储</div></div>
  <div class="input-group"><label class="input-label">模型列表</label><input class="input" id="ch-models" value="${ch?(ch.models||[]).join(', '):''}" placeholder="gpt-4o, gpt-4o-mini"><div class="input-hint">逗号分隔多个模型</div></div>
  <div style="display:grid;grid-template-columns:1fr 1fr;gap:12px">
    <div class="input-group"><label class="input-label">优先级</label><input class="input" type="number" id="ch-pri" value="${ch?ch.priority:0}"><div class="input-hint">数字越大越优先</div></div>
    <div class="input-group"><label class="input-label">权重</label><input class="input" type="number" id="ch-wt" value="${ch?ch.weight:1}"><div class="input-hint">同优先级负载分配</div></div>
  </div>
  </div>
  <div class="modal-actions"><button class="btn btn-secondary" onclick="closeModal()">取消</button><button class="btn btn-primary" onclick="saveChannel('${id||''}')">确认</button></div>`);
}

async function saveChannel(id){
  const models=val('ch-models').split(',').map(s=>s.trim()).filter(Boolean);
  const body={name:val('ch-name'),type:val('ch-type'),baseUrl:val('ch-url'),apiKey:val('ch-key'),models:JSON.stringify(models),priority:+val('ch-pri'),weight:+val('ch-wt'),status:1};
  if(!body.name){toast('请输入渠道名称','error');return}
  const r=id?await api('/channels/'+id,{method:'PUT',body:JSON.stringify(body)}):await api('/channels',{method:'POST',body:JSON.stringify(body)});
  if(r.code==='0000'){toast(id?'渠道更新成功':'渠道添加成功');closeModal();loadChannels();loadDashboard()}else toast(r.info||'操作失败','error');
}
async function delChannel(id){const r=await api('/channels/'+id,{method:'DELETE'});if(r.code==='0000'){toast('渠道已删除');loadChannels();loadDashboard()}else toast(r.info||'删除失败','error')}
async function testChannel(id){toast('正在测试渠道连通性...','info');const r=await api('/channels/'+id+'/test',{method:'POST'});if(r.code==='0000')toast('渠道测试成功，连接正常');else toast(r.info||'测试失败','error')}
async function toggleChannelStatus(id,status){const r=await api('/channels/'+id+'/toggle',{method:'POST',body:JSON.stringify({enabled:status===1})});if(r.code==='0000'){toast(status===1?'渠道已启用':'渠道已禁用');loadChannels()}else toast(r.info||'操作失败','error')}
