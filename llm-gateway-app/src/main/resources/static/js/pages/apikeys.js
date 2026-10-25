function pgApiKeys(){
  const keys=state.data.apiKeys||[];
  return`<div class="page-shell">
  <div class="page-header"><div><div class="page-title">API 密钥</div><div class="page-subtitle">管理对外提供的 API 密钥 · 额度控制</div></div>
    <div class="page-header-actions"><button class="btn btn-secondary btn-sm" onclick="loadApiKeys()">${I.refresh}刷新</button><button class="btn btn-primary" onclick="showKeyModal()">${I.plus}添加密钥</button></div>
  </div>
  <div class="surface" style="overflow:hidden"><div class="table-wrap">
    <table class="table"><thead><tr><th>名称</th><th>密钥</th><th>状态</th><th>额度使用</th><th>创建时间</th><th>操作</th></tr></thead><tbody>
    ${keys.map(k=>{
      const kid=k.id||k.keyId||'';
      const kkey=k.key||k.apiKey||'';
      const showFull=state.keyVisible[kid];
      const pct=k.quotaLimit>0?Math.min(k.quotaUsed/k.quotaLimit*100,100):0;
      const pctColor=pct>=90?'var(--red)':pct>=70?'var(--amber)':'var(--primary)';
      return `<tr>
      <td style="font-weight:600">${esc(k.name)}</td>
      <td><div style="display:flex;align-items:center;gap:6px"><span style="font-family:monospace;font-size:12px">${showFull?esc(kkey):'••••••••'+esc(kkey.slice(-4))}</span><button class="btn btn-secondary btn-sm btn-icon" onclick="toggleKey('${kid}')" title="${showFull?'隐藏':'显示'}">${showFull?I.eyeOff:I.eye}</button><button class="btn btn-secondary btn-sm btn-icon" onclick="copyKey('${kid}')" title="复制密钥">${I.copy}</button></div></td>
      <td><div class="switch ${(k.status===1?'on':'')}" onclick="toggleKeyStatus('${kid}',${k.status===1?0:1})" title="${k.status===1?'点击禁用':'点击启用'}"><div class="switch-knob"></div></div></td>
      <td><div style="display:flex;align-items:center;gap:10px"><div class="progress" style="width:90px"><div class="progress-bar" style="width:${pct}%;background:${pctColor}"></div></div><span style="font-size:11px;color:var(--muted-foreground);font-variant-numeric:tabular-nums;white-space:nowrap">${k.quotaUsed||0}/${k.quotaLimit===0?'∞':k.quotaLimit}</span></div></td>
      <td style="color:var(--muted-foreground);font-size:12px">${fmtDate(k.createdAt)}</td>
      <td><div class="row-actions">
        <button class="btn btn-secondary btn-sm btn-icon" onclick="showKeyModal('${kid}')" title="编辑">${I.edit}</button>
        <button class="btn btn-danger btn-sm btn-icon" onclick="confirmDel('密钥','${esc(k.name)}',()=>delKey('${kid}'))" title="删除">${I.trash}</button>
      </div></td>
    </tr>`;
    }).join('')||emptyRow(6,'暂无密钥','点击右上角添加密钥')}
    </tbody></table>
  </div></div></div>`;
}

async function loadApiKeys(){const r=await api('/keys');if(r.code==='0000')state.data.apiKeys=r.data||[];updatePage('apikeys')}

function showKeyModal(id){
  const k=id?(state.data.apiKeys||[]).find(x=>(x.id||x.keyId)===id):null;
  const title=id?'编辑密钥':'添加密钥';
  showModal(`<div class="modal-header"><div class="modal-title">${title}</div></div>
  <div class="modal-body">
  <div class="input-group"><label class="input-label">密钥名称</label><input class="input" id="key-name" value="${k?esc(k.name):''}" placeholder="如：测试密钥"></div>
  ${!id?`<div class="input-group"><label class="input-label">密钥</label><div style="display:flex;gap:8px;align-items:center"><input class="input" id="key-key" value="sk-llmapi-${randomKey()}" readonly style="font-family:monospace;flex:1;background:var(--slate-l)"><button class="btn btn-secondary btn-sm" onclick="document.getElementById('key-key').value='sk-llmapi-'+randomKey()">${I.refresh}</button></div><div class="input-hint">系统自动生成，可点击刷新重新生成</div></div>`:''}
  <div class="input-group"><label class="input-label">额度限制</label><input class="input" type="number" id="key-quota" value="${k?k.quotaLimit:0}" placeholder="0"><div class="input-hint">0 表示无限制</div></div>
  </div>
  <div class="modal-actions"><button class="btn btn-secondary" onclick="closeModal()">取消</button><button class="btn btn-primary" onclick="saveKey('${id||''}')">确认</button></div>`);
}

async function saveKey(id){
  const body={name:val('key-name'),status:1,quotaLimit:+val('key-quota')||0};
  if(!body.name){toast('请输入密钥名称','error');return}
  if(!id)body.key=val('key-key');
  const r=id?await api('/keys/'+id,{method:'PUT',body:JSON.stringify(body)}):await api('/keys',{method:'POST',body:JSON.stringify(body)});
  if(r.code==='0000'){toast(id?'密钥更新成功':'密钥添加成功');closeModal();loadApiKeys();loadDashboard()}else toast(r.info||'操作失败','error');
}
async function delKey(id){const r=await api('/keys/'+id,{method:'DELETE'});if(r.code==='0000'){toast('密钥已删除');loadApiKeys();loadDashboard()}else toast(r.info||'删除失败','error')}
async function toggleKeyStatus(id,status){const r=await api('/keys/'+id+'/toggle',{method:'POST',body:JSON.stringify({enabled:status===1})});if(r.code==='0000'){toast(status===1?'密钥已启用':'密钥已禁用');loadApiKeys()}else toast(r.info||'操作失败','error')}
function toggleKey(id){if(state.keyVisible[id]){delete state.keyVisible[id]}else{state.keyVisible[id]=true}updatePage('apikeys')}
function copyKey(id){
  const k=(state.data.apiKeys||[]).find(x=>(x.id||x.keyId)===id);
  if(!k)return;
  copyText(k.key||k.apiKey||'');
}
