let agentChatLoading=false;

function pgAgents(){
  if(state.currentAgentId)return renderAgentChatPage();
  return renderAgentListPage();
}

function renderAgentListPage(){
  const agents=state.data.agents||[];
  return`<div class="page-shell">
  <div class="page-header"><div><div class="page-title">Agent 管理</div><div class="page-subtitle">配置 AI 智能体 · 绑定模型 · 多轮对话</div></div>
    <div class="page-header-actions"><button class="btn btn-primary" onclick="showAgentModal()">${I.plus}添加 Agent</button></div>
  </div>
  ${agents.length?`<div class="grid grid-3">
    ${agents.map(a=>{
      let cfg={};if(a.configJson){try{cfg=JSON.parse(a.configJson)}catch(e){}}
      const enabled=a.status===1||a.enabled===1;
      return `<div class="surface agent-card" style="padding:22px" onclick="openAgentChat('${a.agentId||a.id}')">
      <div class="agent-card-header">
        <div style="min-width:0">
          <div class="agent-card-name">${esc(a.agentName||cfg.name)}</div>
          <div class="agent-card-id">${esc(a.agentId||a.id)}</div>
        </div>
        ${statusBadge(enabled)}
      </div>
      ${cfg.model?`<div style="margin-top:10px"><span class="badge badge-blue">${esc(cfg.model)}</span></div>`:''}
      ${(a.agentDesc||cfg.description)?`<div class="agent-card-desc">${esc(a.agentDesc||cfg.description)}</div>`:''}
      ${cfg.instruction?`<div class="agent-card-desc" style="margin-top:6px;font-size:12px;color:var(--muted-foreground)">${esc(truncate(cfg.instruction,100))}</div>`:''}
      <div class="agent-card-actions" onclick="event.stopPropagation()">
        <button class="btn btn-primary btn-sm" onclick="openAgentChat('${a.agentId||a.id}')">${I.chat}对话</button>
        <button class="btn btn-secondary btn-sm btn-icon" onclick="showAgentModal('${a.agentId||a.id}')" title="编辑">${I.edit}</button>
        <button class="btn btn-danger btn-sm btn-icon" onclick="confirmDel('Agent','${esc(a.agentName||cfg.name)}',()=>delAgent('${a.agentId||a.id}'))" title="删除">${I.trash}</button>
      </div>
    </div>`;
    }).join('')}
  </div>`:`<div class="surface" style="padding:0"><div class="empty"><div>${I.agents}</div><div class="empty-title">暂无 Agent</div><div class="empty-desc">创建一个 Agent，配置模型与系统提示词，即可开始多轮智能对话</div><button class="btn btn-primary" style="margin-top:16px" onclick="showAgentModal()">${I.plus}添加 Agent</button></div></div>`}
  </div>`;
}

function renderAgentChatPage(){
  const a=(state.data.agents||[]).find(x=>(x.agentId||x.id)===state.currentAgentId);
  let cfg={};if(a&&a.configJson){try{cfg=JSON.parse(a.configJson)}catch(e){}}
  const msgs=state.agentMessages||[];
  const name=a?a.agentName||cfg.name:state.currentAgentId;
  return`<div class="page-shell">
  <div class="page-header"><div><div class="page-title">Agent 对话</div><div class="page-subtitle">${esc(name)} · ${esc(cfg.model||'未指定模型')}</div></div>
    <div class="page-header-actions"><button class="btn btn-secondary btn-sm" onclick="showAgentModal('${state.currentAgentId}')">${I.edit}编辑</button><button class="btn btn-secondary btn-sm" onclick="clearAgentChat()">${I.refresh}新对话</button><button class="btn btn-secondary btn-sm" onclick="backToAgentList()">${I.close}返回列表</button></div>
  </div>
  <div class="agent-page">
    <div class="agent-sidebar">
      <div class="surface" style="padding:20px;margin-bottom:12px">
        <div style="font-weight:700;font-size:15px;margin-bottom:10px">${esc(name)}</div>
        ${cfg.model?`<div style="margin-bottom:10px"><span class="badge badge-blue">${esc(cfg.model)}</span></div>`:''}
        ${(a&&a.agentDesc)||cfg.description?`<div style="font-size:13px;color:var(--muted-foreground);margin-bottom:10px;line-height:1.5">${esc(a?.agentDesc||cfg.description)}</div>`:''}
        ${cfg.instruction?`<div style="font-size:12px;color:var(--muted-foreground);padding:12px 14px;background:var(--slate-l);border-radius:var(--rs);line-height:1.6;border:1px solid var(--border)"><div style="font-weight:600;color:var(--foreground);margin-bottom:4px">系统提示词</div>${esc(truncate(cfg.instruction,200))}</div>`:''}
      </div>
      <div class="surface" style="padding:20px">
        <div style="font-weight:600;font-size:13px;margin-bottom:12px;color:var(--foreground)">其他 Agent</div>
        ${(state.data.agents||[]).filter(x=>(x.agentId||x.id)!==state.currentAgentId).map(x=>{
          let xc={};if(x.configJson){try{xc=JSON.parse(x.configJson)}catch(e){}}
          return `<div style="padding:12px;border-radius:var(--rs);cursor:pointer;transition:var(--ease);margin-bottom:6px;border:1px solid transparent" onmouseover="this.style.background='var(--slate-l)'" onmouseout="this.style.background=''" onclick="openAgentChat('${x.agentId||x.id}')">
            <div style="font-weight:500;font-size:13px;color:var(--foreground)">${esc(x.agentName||xc.name)}</div>
            <div style="font-size:11px;color:var(--muted-foreground);margin-top:3px">${esc(xc.model||'')}</div>
          </div>`;
        }).join('')||'<div style="font-size:12px;color:var(--muted-foreground)">暂无其他 Agent</div>'}
      </div>
    </div>
    <div class="agent-chat-area">
      <div class="agent-chat-body" id="agent-chat-body">
        ${msgs.length?`<div class="agent-chat-messages">${msgs.map(m=>{
          if(m.role==='user')return`<div class="chat-msg user"><div class="chat-avatar">U</div><div class="chat-bubble">${esc(m.content)}</div></div>`;
          if(m.content==='__typing__')return`<div class="chat-msg asst"><div class="chat-avatar">AI</div><div class="chat-bubble"><div class="typing-indicator"><span class="typing-dot"></span><span class="typing-dot"></span><span class="typing-dot"></span></div></div></div>`;
          const isErr=m.content?.startsWith('❌');
          return`<div class="chat-msg asst"><div class="chat-avatar">AI</div><div class="chat-bubble${isErr?' error':''}">${esc(m.content)}</div></div>`;
        }).join('')}</div>`:`<div class="agent-empty"><div class="agent-empty-icon">${I.chat}</div><div style="font-size:16px;font-weight:700;color:var(--foreground)">开始对话</div><div style="font-size:13px;color:var(--muted-foreground)">在下方输入消息，与 Agent 智能交互</div></div>`}
      </div>
      <div class="chat-input-area">
        <div class="chat-input-wrap">
          <textarea class="chat-input" id="agent-chat-input" placeholder="输入消息..." rows="1" onkeydown="if(event.key==='Enter'&&!event.shiftKey){event.preventDefault();sendAgentChat()}" oninput="this.style.height='auto';this.style.height=Math.min(this.scrollHeight,140)+'px'"></textarea>
          <button class="chat-send-btn" id="agent-send-btn" onclick="sendAgentChat()">${I.send}</button>
        </div>
        <div class="chat-input-meta">
          <span class="chat-input-hint"><kbd>Enter</kbd> 发送 · <kbd>Shift+Enter</kbd> 换行</span>
        </div>
      </div>
    </div>
  </div></div>`;
}

function showAgentModal(id){
  const a=id?(state.data.agents||[]).find(x=>(x.agentId||x.id)===id):null;
  let cfg={};
  if(a&&a.configJson){try{cfg=JSON.parse(a.configJson)}catch(e){}}
  const title=id?'编辑 Agent':'添加 Agent';
  const channelModels=[...new Set((state.data.channels||[]).flatMap(c=>c.models||[]))];
  showModal(`<div class="modal-header"><div class="modal-title">${title}</div></div>
  <div class="modal-body">
  <div class="input-group"><label class="input-label">Agent 名称</label><input class="input" id="ag-name" value="${a?esc(a.agentName||cfg.name||''):''}" placeholder="如：代码助手"></div>
  <div class="input-group"><label class="input-label">Agent ID</label><input class="input" id="ag-id" value="${a?esc(a.agentId||a.id):''}" placeholder="如：code-assistant" ${a?'readonly':''}><div class="input-hint">唯一标识，创建后不可修改</div></div>
  <div class="input-group"><label class="input-label">应用名称</label><input class="input" id="ag-app" value="${a?esc(a.appName||'waliapi'):''}" placeholder="如：waliapi"></div>
  <div class="input-group"><label class="input-label">绑定模型</label>${channelModels.length?`<select class="input" id="ag-model">${channelModels.map(m=>`<option value="${esc(m)}" ${cfg.model===m?'selected':''}>${esc(m)}</option>`).join('')}</select><div class="input-hint">从已配置的渠道中选择模型</div>`:`<input class="input" id="ag-model" value="${esc(cfg.model||'')}" placeholder="如：gpt-4o"><div class="input-hint">手动输入模型名称</div>`}</div>
  <div class="input-group"><label class="input-label">描述</label><input class="input" id="ag-desc" value="${a?esc(a.agentDesc||cfg.description||''):''}" placeholder="简短描述 Agent 的用途"></div>
  <div class="input-group"><label class="input-label">系统提示词</label><textarea class="input" id="ag-prompt" rows="5" placeholder="你是一个专业的代码助手，擅长...">${esc(cfg.instruction||'')}</textarea><div class="input-hint">定义 Agent 的角色和行为，会作为 system message 注入对话</div></div>
  </div>
  <div class="modal-actions"><button class="btn btn-secondary" onclick="closeModal()">取消</button><button class="btn btn-primary" onclick="saveAgent('${id||''}')">确认</button></div>`);
}

async function saveAgent(id){
  const name=val('ag-name'),agentId=val('ag-id'),appName=val('ag-app')||'waliapi';
  const model=val('ag-model')||'gpt-4o',desc=val('ag-desc'),instruction=val('ag-prompt');
  if(!name){toast('请输入 Agent 名称','error');return}
  if(!agentId){toast('请输入 Agent ID','error');return}
  const configJson=JSON.stringify({type:'llm',name,model,instruction,description:desc||name});
  const body={agentName:name,agentId,appName,agentDesc:desc,configJson,status:1};
  const r=id?await api('/agents/'+id,{method:'PUT',body:JSON.stringify(body)}):await api('/agents',{method:'POST',body:JSON.stringify(body)});
  if(r.code==='0000'){toast(id?'Agent 更新成功':'Agent 添加成功');closeModal();loadAgents()}else toast(r.info||'操作失败','error');
}
async function loadAgents(){const r=await api('/agents');if(r.code==='0000')state.data.agents=r.data||[];updatePage('agents')}
async function delAgent(id){const r=await api('/agents/'+id,{method:'DELETE'});if(r.code==='0000'){toast('Agent 已删除');if(state.currentAgentId===id){state.currentAgentId=null;state.agentMessages=[];}loadAgents()}else toast(r.info||'删除失败','error')}

function openAgentChat(id){
  state.currentAgentId=id;
  state.agentMessages=[];
  state.agentSessionId=null;
  updatePage('agents');
  setTimeout(()=>scrollAgentChat(),50);
}
function backToAgentList(){state.currentAgentId=null;state.agentMessages=[];state.agentSessionId=null;updatePage('agents')}
function clearAgentChat(){state.agentMessages=[];state.agentSessionId=null;updatePage('agents')}

async function sendAgentChat(){
  if(agentChatLoading)return;
  const input=document.getElementById('agent-chat-input');
  const msg=input.value.trim();if(!msg)return;
  input.value='';input.style.height='auto';
  state.agentMessages=[...(state.agentMessages||[]),{role:'user',content:msg},{role:'assistant',content:'__typing__'}];
  agentChatLoading=true;
  const btn=document.getElementById('agent-send-btn');
  if(btn){btn.disabled=true}
  updateAgentMessages();
  scrollAgentChat();
  input.focus();
  const body={message:msg,userId:'web'};
  if(state.agentSessionId){body.sessionId=state.agentSessionId;}
  const r=await api('/agents/'+state.currentAgentId+'/chat',{method:'POST',body:JSON.stringify(body)});
  state.agentMessages=state.agentMessages.filter(m=>m.content!=='__typing__');
  if(r.code==='0000'){
    if(r.data?.sessionId){state.agentSessionId=r.data.sessionId;}
    state.agentMessages=[...state.agentMessages,{role:'assistant',content:r.data?.response||r.data?.answer||'无回答'}];
  } else {
    state.agentMessages=[...state.agentMessages,{role:'assistant',content:'❌ 错误: '+(r.info||'未知')}];
  }
  agentChatLoading=false;
  if(btn){btn.disabled=false}
  updateAgentMessages();
  scrollAgentChat();
  input.focus();
}
function scrollAgentChat(){setTimeout(()=>{const el=document.getElementById('agent-chat-body');if(el)el.scrollTop=el.scrollHeight},50)}
