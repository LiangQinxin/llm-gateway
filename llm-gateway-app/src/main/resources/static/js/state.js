// 全局状态
const state={
  activeTab:'dashboard',
  currentKbId:null,
  currentKbTab:'chat',
  expandedLogId:null,
  keyVisible:{},
  currentAgentId:null,
  agentMessages:[],
  agentSessionId:null,
  agentChatLoading:false,
  usageProtocol:'chat',
  usageLang:'curl',
  loading:{},
  data:{
    dashboard:{},channels:[],apiKeys:[],agents:[],logs:[],
    kb:[],kbDocs:[],kbConv:[],kbSources:[],kbIndexStatus:null,
    kbSearchResults:null,kbTags:[],
    wikiData:{projects:[],pages:[],sources:[],tags:[],conv:[],searchResults:null,currentProjectId:null,currentTab:'pages',graphData:null,viewPagePath:null},
    security:{builtin:[],custom:[],findings:[]},
    secPolicy:{}
  }
};

// 页面切换
function switchTab(id){
  state.activeTab=id;
  document.querySelectorAll('.nav-item').forEach(el=>{
    el.classList.toggle('active',el.dataset.tab===id);
  });
  document.querySelectorAll('.page-container').forEach(el=>{
    el.style.display=el.dataset.page===id?'':'none';
  });
  // 移动端：切换后收起侧边栏
  if(window.innerWidth<=860){
    document.getElementById('sidebar').classList.remove('open');
    document.getElementById('backdrop').classList.remove('show');
  }
  const loaders={
    dashboard:loadDashboard,channels:loadChannels,apikeys:loadApiKeys,
    logs:loadLogs,
    security:loadSecurity,usage:loadUsageData
  };
  if(loaders[id])loaders[id]();
}

// 局部更新：只更新当前页面内容
function updatePage(id){
  const container=document.querySelector(`.page-container[data-page="${id}"]`);
  if(!container)return;
  const renderers={
    dashboard:pgDashboard,channels:pgChannels,apikeys:pgApiKeys,
    logs:pgLogs,security:pgSecurity,usage:pgUsage
  };
  const r=renderers[id];
  if(r)container.innerHTML=r();
}

// 局部更新：只更新指定区域，避免输入框失焦和页面闪动
function updatePartial(selector,html){
  const el=document.querySelector(selector);
  if(el)el.innerHTML=html;
}

// 更新知识库内容区域（不触发输入框区域重渲染）
function updateKbContent(){
  const el=document.getElementById('kb-main-content');
  if(el)el.innerHTML=renderKbContent();
}

// 更新 Agent 聊天消息区域（不触输入框区域重渲染）
function updateAgentMessages(){
  const el=document.getElementById('agent-chat-body');
  if(el){
    const msgs=state.agentMessages||[];
    if(!msgs.length){
      el.innerHTML=`<div class="agent-empty"><div class="agent-empty-icon">${I.chat}</div><div style="font-size:16px;font-weight:700;color:var(--foreground)">开始对话</div><div style="font-size:13px;color:var(--muted-foreground)">在下方输入消息，与 Agent 智能交互</div></div>`;
    } else {
      el.innerHTML=`<div class="agent-chat-messages">${msgs.map(m=>{
        if(m.role==='user')return`<div class="chat-msg user"><div class="chat-avatar">U</div><div class="chat-bubble">${esc(m.content)}</div></div>`;
        if(m.content==='__typing__')return`<div class="chat-msg asst"><div class="chat-avatar">AI</div><div class="chat-bubble"><div class="typing-indicator"><span class="typing-dot"></span><span class="typing-dot"></span><span class="typing-dot"></span></div></div></div>`;
        const isErr=m.content?.startsWith('❌');
        return`<div class="chat-msg asst"><div class="chat-avatar">AI</div><div class="chat-bubble${isErr?' error':''}">${esc(m.content)}</div></div>`;
      }).join('')}</div>`;
    }
  }
}

// 更新知识库聊天消息区域（不触发输入框区域重渲染）
function updateKbChatMessages(){
  const el=document.querySelector('.kb-chat-messages');
  if(!el)return;
  const convs=state.data.kbConv||[];
  if(!convs.length){
    el.innerHTML=`<div style="display:flex;align-items:center;justify-content:center;height:100%"><div style="text-align:center;color:var(--muted-foreground)">
      <div style="font-size:32px;margin-bottom:8px;opacity:.3">💬</div>
      <div style="font-size:14px;font-weight:500;margin-bottom:4px">开始知识库问答</div>
      <div style="font-size:13px">在下方输入问题，AI 将从知识库检索相关内容并回答</div>
    </div></div>`;
    return;
  }
  el.innerHTML=convs.map(c=>{
    if(c.role==='user')return`<div class="chat-msg user"><div class="chat-avatar">U</div><div class="chat-bubble">${esc(c.content||c.question||'')}</div></div>`;
    if(c.content==='__typing__')return`<div class="chat-msg asst"><div class="chat-avatar">AI</div><div class="chat-bubble"><div class="typing-indicator"><span class="typing-dot"></span><span class="typing-dot"></span><span class="typing-dot"></span></div></div></div>`;
    if(c.content==='__error__'){
      return`<div class="chat-msg asst"><div class="chat-avatar">AI</div><div class="chat-bubble error" style="max-width:520px">
        <div style="font-weight:600;margin-bottom:6px">${I.test} 回答生成失败</div>
        <div style="font-size:13px;line-height:1.6">${esc(c.error||'未知错误')}</div>
        <div style="font-size:12px;color:var(--muted-foreground);margin-top:8px;padding-top:8px;border-top:1px solid var(--border)">可能是上游模型服务暂时不可用，可重试或更换模型</div>
        <button class="btn btn-primary btn-sm" style="margin-top:12px" onclick="retryKbAsk(${JSON.stringify(c.retryQ||'').replace(/"/g,'&quot;')},'${esc(c.retryModel||'')}')">${I.refresh} 重试</button>
      </div></div>`;
    }
    let html=`<div class="chat-msg asst"><div class="chat-avatar">AI</div><div class="chat-bubble md-content">${renderMd(c.content||c.answer||'')}</div></div>`;
    if(c.sources&&c.sources.length){
      html+=`<div class="chat-msg asst"><div class="chat-avatar"></div><div class="chat-sources">
        <div class="chat-sources-title">📚 引用来源 (${c.sources.length})</div>
        ${c.sources.slice(0,5).map(s=>`<div class="chat-source-item"><span class="chat-source-name">${esc(s.filename||s.source||'')} ${s.content?'· '+esc(s.content.substring(0,60))+(s.content.length>60?'...':''):''}</span><span class="chat-source-score">${(s.score||0).toFixed(3)}</span></div>`).join('')}
        ${c.sources.length>5?`<div style="color:var(--border-strong);font-size:11px;margin-top:4px">还有 ${c.sources.length-5} 个来源</div>`:''}
      </div></div>`;
    }
    return html;
  }).join('');
}
