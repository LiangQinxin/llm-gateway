function pgSecurity(){
  const builtinRules=state.data.security.builtin||[];
  const customRules=state.data.security.custom||[];
  const findings=state.data.security.findings||[];
  const secCount=builtinRules.filter(r=>r.enabled).length;
  return`<div class="page-shell">
  <div class="page-header"><div><div class="page-title">安全审计</div><div class="page-subtitle">安全策略 · 规则管理 · 审计日志</div></div>
    <div class="page-header-actions"><button class="btn btn-secondary btn-sm" onclick="loadSecurity()">${I.refresh}刷新</button></div>
  </div>
  <div class="grid grid-4" style="margin-bottom:16px">
    <div class="card stat-card"><div class="stat-icon" style="background:var(--green-soft);color:var(--green)">${I.security}</div><div class="stat-value" style="margin-top:10px">${secCount}</div><div class="stat-label">启用规则</div></div>
    <div class="card stat-card"><div class="stat-icon" style="background:var(--primary-soft);color:var(--primary)">${I.plus}</div><div class="stat-value" style="margin-top:10px">${customRules.length}</div><div class="stat-label">自定义规则</div></div>
    <div class="card stat-card"><div class="stat-icon" style="background:var(--amber-soft);color:var(--amber)">${I.logs}</div><div class="stat-value" style="margin-top:10px">${findings.length}</div><div class="stat-label">安全发现</div></div>
    <div class="card stat-card"><div class="stat-icon" style="background:var(--red-soft);color:var(--red)">${I.test}</div><div class="stat-value" style="margin-top:10px">${findings.filter(f=>f.severity==='CRITICAL'||f.severity==='HIGH').length}</div><div class="stat-label">高危发现</div></div>
  </div>
  <div class="grid grid-2" style="margin-bottom:16px">
    <div class="surface" style="padding:24px">
      <div style="font-weight:700;margin-bottom:14px">安全策略配置</div>
      <div>
        ${secToggleRow('安全审计总开关','audit',true)}
        ${secToggleRow('Unicode 混淆扫描','unicode',true)}
        ${secToggleRow('网络请求扫描','network',true)}
        ${secToggleRow('工具调用风险扫描','tool',true)}
        ${secToggleRow('响应内容扫描','response',true)}
        ${secToggleRow('密钥自动脱敏','mask',true)}
      </div>
    </div>
    <div class="surface" style="padding:24px">
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:14px">
        <span style="font-weight:700">自定义规则</span>
        <button class="btn btn-primary btn-sm" onclick="showSecRuleModal()">${I.plus}添加规则</button>
      </div>
      <div>
        ${customRules.length?customRules.map(r=>`<div class="rule-item">
          <div style="min-width:0"><span class="badge badge-blue">${esc((r.ruleType||r.type)+' · '+(r.category||''))}</span><span style="margin-left:8px;font-size:13px;font-weight:500">${esc(r.name||r.pattern)}</span></div>
          <div style="display:flex;align-items:center;gap:8px;flex-shrink:0">
            <span class="badge ${r.severity==='CRITICAL'?'badge-red':r.severity==='HIGH'?'badge-amber':'badge-slate'}">${esc(r.severity)}</span>
            <span class="badge badge-slate">${esc(r.action||'跟随全局')}</span>
            <button class="btn btn-danger btn-sm btn-icon" onclick="confirmDel('规则','${esc(r.name||r.pattern)}',()=>delCustomRule('${r.id}'))" title="删除">${I.trash}</button>
          </div>
        </div>`).join(''):emptyState('暂无自定义规则','点击右上角添加')}
      </div>
    </div>
  </div>
  <div class="surface" style="margin-bottom:16px;overflow:hidden">
    <div style="padding:14px 16px;border-bottom:1px solid var(--border);font-weight:700;font-size:13px;background:var(--slate-l);display:flex;align-items:center;gap:8px">内置规则 <span class="badge badge-slate">${builtinRules.length}</span></div>
    <div class="table-wrap"><table class="table"><thead><tr><th>规则名称</th><th>类别</th><th>严重度</th><th>动作</th><th>状态</th><th>操作</th></tr></thead><tbody>
    ${builtinRules.length?builtinRules.map(r=>`<tr>
      <td><div style="font-weight:600">${esc(r.name||r.ruleName)}</div>${r.description?`<div style="font-size:12px;color:var(--muted-foreground);margin-top:2px;max-width:380px">${esc(r.description)}</div>`:''}</td>
      <td><span class="badge badge-slate">${esc(r.category||r.type)}</span></td>
      <td><span class="badge ${r.severity==='CRITICAL'?'badge-red':r.severity==='HIGH'?'badge-amber':r.severity==='MEDIUM'?'badge-blue':'badge-slate'}">${esc(r.severity)}</span></td>
      <td><span class="badge badge-slate">${esc(r.action||'跟随全局')}</span></td>
      <td><div class="switch ${r.enabled?'on':''}" onclick="toggleBuiltinRule('${r.ruleId||r.id}',${!r.enabled})" title="${r.enabled?'点击禁用':'点击启用'}"><div class="switch-knob"></div></div></td>
      <td><div class="row-actions"><button class="btn btn-secondary btn-sm btn-icon" onclick="showBuiltinRuleModal('${r.ruleId||r.id}')" title="编辑">${I.edit}</button></div></td>
    </tr>`).join(''):emptyRow(6,'暂无内置规则','')}
    </tbody></table></div>
  </div>
  <div class="surface" style="overflow:hidden">
    <div style="padding:14px 16px;border-bottom:1px solid var(--border);font-weight:700;font-size:13px;background:var(--slate-l);display:flex;align-items:center;gap:8px">安全发现 <span class="badge badge-slate">${findings.length}</span></div>
    <div class="table-wrap"><table class="table"><thead><tr><th>时间</th><th>规则</th><th>严重度</th><th>内容</th><th>处理动作</th></tr></thead><tbody>
    ${findings.length?findings.map(f=>`<tr>
      <td style="font-size:12px;color:var(--muted-foreground);white-space:nowrap">${fmtDate(f.createdAt||f.timestamp)}</td>
      <td style="font-weight:600">${esc(f.title||f.ruleName||f.ruleId||'-')}</td>
      <td><span class="badge ${f.severity==='CRITICAL'?'badge-red':f.severity==='HIGH'?'badge-amber':'badge-slate'}">${esc(f.severity)}</span></td>
      <td style="font-size:12px;max-width:320px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:var(--muted-foreground)">${esc(f.description||f.evidenceMasked||f.content||f.detail||'-')}</td>
      <td><span class="badge badge-slate">${esc(f.action||f.resolution||'audit')}</span></td>
    </tr>`).join(''):emptyRow(5,'暂无安全发现记录','')}
    </tbody></table></div>
  </div></div>`;
}

function secToggleRow(label,key,on){
  return`<div class="sec-toggle-row">
    <span style="font-size:13px;color:var(--muted-foreground);font-weight:500">${label}</span>
    <div style="display:flex;align-items:center;gap:10px">
      <span style="font-size:12px;font-weight:600;color:${on?'var(--green)':'var(--border-strong)'}">${on?'已启用':'已禁用'}</span>
      <div class="switch${on?' on':''}" onclick="toggleSecPolicy('${key}')" title="切换"><div class="switch-knob"></div></div>
    </div>
  </div>`;
}

async function loadSecurity(){
  const [b,c,f]=await Promise.all([api('/security/rules/builtin'),api('/security/rules/custom'),api('/security/rules/findings')]);
  if(b.code==='0000')state.data.security.builtin=b.data||[];
  if(c.code==='0000')state.data.security.custom=c.data||[];
  if(f.code==='0000')state.data.security.findings=f.data||[];
  updatePage('security');
}
async function toggleBuiltinRule(id,enabled){const r=await api('/security/rules/builtin/'+id,{method:'PUT',body:JSON.stringify({enabled})});if(r.code==='0000'){toast('规则已更新');loadSecurity()}else toast(r.info||'操作失败','error')}

function showBuiltinRuleModal(ruleId){
  const r=(state.data.security.builtin||[]).find(x=>(x.ruleId||x.id)===ruleId);
  if(!r){toast('规则不存在','error');return}
  state._brEnabled=r.enabled;
  const sev=(r.severity||'HIGH').toUpperCase();
  const sevOpts=[['CRITICAL','严重'],['HIGH','高'],['MEDIUM','中'],['LOW','低']];
  const action=(r.action||'').toLowerCase();
  const actionOpts=[['','跟随全局策略'],['warn','warn 警告'],['redact','redact 脱敏'],['block','block 阻断']];
  showModal(`<div class="modal-header"><div class="modal-title">编辑内置规则</div></div>
  <div class="modal-body">
  <div class="input-group"><label class="input-label">规则名称</label><input class="input" id="br-title" value="${esc(r.name||r.title||'')}"></div>
  <div class="input-group"><label class="input-label">规则标识</label><div style="display:flex;align-items:center;gap:8px;padding:8px 0;font-family:monospace;font-size:12px;color:var(--muted-foreground)">${esc(r.ruleId||r.id)}<span class="badge badge-slate">${esc(r.category||r.type)}</span></div><div class="input-hint">规则标识与类别由系统定义，不可修改</div></div>
  <div style="display:grid;grid-template-columns:1fr 1fr;gap:12px">
    <div class="input-group"><label class="input-label">严重度</label><select class="input" id="br-severity">${sevOpts.map(([v,l])=>`<option value="${v}" ${sev===v?'selected':''}>${l}</option>`).join('')}</select></div>
    <div class="input-group"><label class="input-label">状态</label><div style="display:flex;align-items:center;gap:10px;padding:8px 0"><div class="switch ${r.enabled?'on':''}" id="br-switch" onclick="toggleBrEnabled()"><div class="switch-knob"></div></div><span id="br-status" style="font-size:12px;font-weight:600;color:${r.enabled?'var(--green)':'var(--border-strong)'}">${r.enabled?'已启用':'已禁用'}</span></div></div>
  </div>
  <div class="input-group"><label class="input-label">命中动作</label><select class="input" id="br-action">${actionOpts.map(([v,l])=>`<option value="${v}" ${action===v?'selected':''}>${l}</option>`).join('')}</select><div class="input-hint">选择具体动作会覆盖全局策略；跟随全局时按严重度与模式矩阵处置</div></div>
  <div class="input-group"><label class="input-label">检测内容</label><textarea class="input" id="br-desc" rows="4" style="height:auto;resize:vertical;font-family:'SF Mono',Monaco,monospace;font-size:12px;line-height:1.6">${esc(r.description||'')}</textarea><div class="input-hint">描述该规则拦截/检测的对象</div></div>
  </div>
  <div class="modal-actions"><button class="btn btn-secondary" onclick="closeModal()">取消</button><button class="btn btn-primary" onclick="saveBuiltinRule('${esc(r.ruleId||r.id)}')">保存</button></div>`);
}

function toggleBrEnabled(){state._brEnabled=!state._brEnabled;const sw=document.getElementById('br-switch');const st=document.getElementById('br-status');if(sw){sw.classList.toggle('on',state._brEnabled);st.textContent=state._brEnabled?'已启用':'已禁用';st.style.color=state._brEnabled?'var(--green)':'var(--border-strong)';}}

async function saveBuiltinRule(ruleId){
  const body={title:val('br-title'),description:val('br-desc'),severity:val('br-severity'),action:val('br-action'),enabled:state._brEnabled};
  if(!body.title){toast('规则名称不能为空','error');return}
  const r=await api('/security/rules/builtin/'+encodeURIComponent(ruleId),{method:'PUT',body:JSON.stringify(body)});
  if(r.code==='0000'){toast('规则已保存');closeModal();loadSecurity()}else toast(r.info||'保存失败','error');
}
function showSecRuleModal(){
  showModal(`<div class="modal-header"><div class="modal-title">添加自定义规则</div></div>
  <div class="modal-body">
  <div class="input-group"><label class="input-label">规则名称</label><input class="input" id="sr-name" placeholder="如：敏感路径检测"></div>
  <div style="display:grid;grid-template-columns:1fr 1fr;gap:12px">
    <div class="input-group"><label class="input-label">规则类型</label><select class="input" id="sr-ruletype"><option value="blacklist">黑名单</option><option value="whitelist">白名单</option></select></div>
    <div class="input-group"><label class="input-label">规则类别</label><select class="input" id="sr-category"><option value="keyword">关键词 keyword</option><option value="domain">域名 domain</option><option value="tool">工具 tool</option><option value="path">路径 path</option></select></div>
  </div>
  <div class="input-group"><label class="input-label">匹配模式</label><input class="input" id="sr-pattern" placeholder="如：/etc/passwd" style="font-family:monospace"><div class="input-hint">子串匹配，非正则</div></div>
  <div style="display:grid;grid-template-columns:1fr 1fr;gap:12px">
    <div class="input-group"><label class="input-label">严重度</label><select class="input" id="sr-severity"><option value="LOW">Low</option><option value="MEDIUM">Medium</option><option value="HIGH">High</option><option value="CRITICAL">Critical</option></select></div>
    <div class="input-group"><label class="input-label">命中动作</label><select class="input" id="sr-action"><option value="">跟随全局策略</option><option value="warn">warn 警告</option><option value="redact">redact 脱敏</option><option value="block">block 阻断</option></select></div>
  </div>
  </div>
  <div class="modal-actions"><button class="btn btn-secondary" onclick="closeModal()">取消</button><button class="btn btn-primary" onclick="addCustomRule()">添加</button></div>`);
}
async function addCustomRule(){
  const body={name:val('sr-name'),ruleType:val('sr-ruletype'),category:val('sr-category'),pattern:val('sr-pattern'),severity:val('sr-severity'),action:val('sr-action'),enabled:true};
  if(!body.name||!body.pattern){toast('请填写完整','error');return}
  const r=await api('/security/rules/custom',{method:'POST',body:JSON.stringify(body)});
  if(r.code==='0000'){toast('规则添加成功');closeModal();loadSecurity()}else toast(r.info||'添加失败','error');
}
async function delCustomRule(id){const r=await api('/security/rules/custom/'+id,{method:'DELETE'});if(r.code==='0000'){toast('规则已删除');loadSecurity()}else toast(r.info||'删除失败','error')}
function toggleSecPolicy(key){toast('安全策略切换需要后端支持','info')}
