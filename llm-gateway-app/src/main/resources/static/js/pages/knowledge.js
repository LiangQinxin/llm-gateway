let docPollTimer=null,sourcePollTimer=null,indexPollTimer=null;
let uploadInProgress=false;

function pgKb(){
  return`<div class="page-shell"><div class="page-header"><div><div class="page-title">知识库</div><div class="page-subtitle">RAG 文档管理 · 向量索引 · 智能问答</div></div><div style="display:flex;gap:8px"><button class="btn btn-secondary" onclick="showImportModal()">${I.import}导入来源</button><button class="btn btn-primary" onclick="showKbModal()">${I.plus}创建知识库</button></div></div>
  <div class="kb-layout">
    <div class="kb-sidebar">
      <div class="kb-panel" style="height:100%">
        <div class="kb-panel-header"><span style="font-weight:600;font-size:13px">知识库列表</span><span class="badge badge-slate">${(state.data.kb||[]).length}</span></div>
        <div class="kb-panel-body">
          ${(state.data.kb||[]).map(kb=>{
            let tagsArr=[];
            if(kb.tags){try{tagsArr=JSON.parse(kb.tags)}catch(e){tagsArr=[]}}
            return `<div class="kbItem${state.currentKbId===kb.id?' active':''}" onclick="selectKb('${kb.id}')">
              <div style="display:flex;justify-content:space-between;align-items:start">
                <div style="flex:1;min-width:0">
                  <div style="font-weight:600;font-size:13px">${esc(kb.name)}</div>
                  <div style="font-size:11px;color:var(--muted-foreground);margin-top:3px;line-height:1.4">${esc(kb.description||'无描述')}</div>
                </div>
                <button class="btn btn-ghost btn-sm" onclick="event.stopPropagation();confirmDel('知识库','${esc(kb.name)}',()=>delKb('${kb.id}'))" style="flex-shrink:0;margin-left:4px;padding:4px 8px" title="删除">${I.trash}</button>
              </div>
              <div class="kb-item-info">
                <span class="badge badge-slate" style="font-size:10px">${esc(kb.embeddingModel||'默认')}</span>
                ${kb.indexStatus?`<span class="badge ${kb.indexStatus==='ready'?'badge-green':kb.indexStatus==='building'?'badge-amber':'badge-slate'}">${esc(kb.indexStatus)}</span>`:''}
                ${kb.docCount!=null?`<span style="color:var(--border-strong)">${kb.docCount} 文档 · ${kb.chunkCount||0} 块</span>`:''}
              </div>
              ${tagsArr.length?`<div class="kb-item-tags">${tagsArr.slice(0,4).map(t=>`<span class="tag-mini">${esc(t)}</span>`).join('')}${tagsArr.length>4?`<span class="tag-mini" style="background:var(--slate-l);color:var(--muted-foreground);border-color:var(--border)">+${tagsArr.length-4}</span>`:''}</div>`:''}
            </div>`;
          }).join('')||`<div class="kb-empty-mini"><div class="kb-empty-mini-title">暂无知识库</div><div class="kb-empty-mini-desc">点击右上角创建</div></div>`}
        </div>
      </div>
    </div>
    <div class="kb-main">
      <div class="kb-panel">
        <div class="kb-panel-header">
          ${state.currentKbId?`<div class="kb-tabs">
            <button class="kb-tab ${state.currentKbTab==='docs'?'active':''}" onclick="switchKbTab('docs')">文档</button>
            <button class="kb-tab ${state.currentKbTab==='chat'?'active':''}" onclick="switchKbTab('chat')">问答</button>
            <button class="kb-tab ${state.currentKbTab==='search'?'active':''}" onclick="switchKbTab('search')">搜索</button>
            <button class="kb-tab ${state.currentKbTab==='index'?'active':''}" onclick="switchKbTab('index')">索引</button>
            <button class="kb-tab ${state.currentKbTab==='tags'?'active':''}" onclick="switchKbTab('tags')">标签</button>
            <button class="kb-tab ${state.currentKbTab==='sources'?'active':''}" onclick="switchKbTab('sources')">来源</button>
          </div>`:''}
          ${state.currentKbId&&state.currentKbTab==='docs'?`<button class="btn btn-primary btn-sm" onclick="showDocModal()">${I.upload}上传文档</button>`:''}
          ${state.currentKbId&&state.currentKbTab==='tags'?`<button class="btn btn-secondary btn-sm" onclick="refreshKbTags()">${I.refresh}刷新</button>`:''}
        </div>
        <div class="kb-panel-body" id="kb-main-content">
          ${state.currentKbId?renderKbContent():emptyState('选择知识库','从左侧选择或创建新知识库')}
        </div>
        ${state.currentKbId&&state.currentKbTab==='chat'?`<div class="kb-chat-footer">
          <div class="chat-toolbar">
            <div class="chat-toolbar-left">
              <span class="chat-toolbar-label">模型</span>
              <select class="input chat-toolbar-select" id="kbAskModel" onchange="state.kbAskModel=this.value">
                ${kbModelOptions()}
              </select>
            </div>
            <div class="chat-toolbar-right">
              <button class="btn btn-secondary btn-sm" id="kbDeepResearchBtn" onclick="kbDeepResearch()" title="多轮深度检索研究">🔬 深度研究</button>
            </div>
          </div>
          <div class="chat-input-wrap">
            <textarea class="kb-chat-input" id="kbAskInput" placeholder="输入问题，RAG 检索回答..." rows="1" onkeydown="if(event.key==='Enter'&&!event.shiftKey){event.preventDefault();kbAsk()}" oninput="this.style.height='auto';this.style.height=Math.min(this.scrollHeight,120)+'px'"></textarea>
            <button class="chat-send-btn" onclick="kbAsk()">${I.send}</button>
          </div>
          <div class="chat-input-meta">
            <span class="chat-input-hint"><kbd>Enter</kbd> 发送 · <kbd>Shift+Enter</kbd> 换行 · 点击「深度研究」进行多轮检索</span>
          </div>
        </div>`:''}
        ${state.currentKbId&&state.currentKbTab==='search'?`<div class="kb-panel-footer">
          <div class="search-input-wrap">
            <select class="input search-mode-select" id="kbSearchMode">
              <option value="hybrid">混合搜索</option>
              <option value="vector">向量搜索</option>
              <option value="keyword">关键词搜索</option>
            </select>
            <input class="input search-input" id="kbSearchInput" placeholder="搜索关键词..." onkeydown="if(event.key==='Enter')kbSearch()">
            <button class="btn btn-primary search-btn" onclick="kbSearch()">${I.search}搜索</button>
          </div>
        </div>`:''}
      </div>
    </div>
  </div>`;
}

function renderKbContent(){
  if(state.currentKbTab==='docs'){
    const docs=state.data.kbDocs||[];
    if(!docs.length)return emptyState('暂无文档','上传文档开始知识库构建');
    return docs.map(doc=>`<div class="kb-doc-item">
      <div class="kb-doc-item-header">
        <div class="kb-doc-item-info">
          <div class="kb-doc-item-name">${esc(doc.name||doc.filename)}</div>
          <div class="kb-doc-item-meta">${esc(doc.sourceType||'text')} · ${doc.chunkCount||0} 块 · ${(doc.totalTokens||0).toLocaleString()} tokens</div>
        </div>
        <div style="display:flex;align-items:center;gap:4px;flex-shrink:0;margin-left:8px">
          <span class="badge ${docStatusBadge(doc.status)}">${esc(doc.status||'pending')}</span>
          <button class="btn btn-ghost btn-sm" onclick="reindexDoc('${doc.id||doc.docId}')" title="重新索引" style="padding:4px 8px">${I.refresh}</button>
          <button class="btn btn-ghost btn-sm" onclick="confirmDel('文档','${esc(doc.name||doc.filename)}',()=>delDoc('${doc.id||doc.docId}'))" title="删除" style="padding:4px 8px">${I.trash}</button>
        </div>
      </div>
      ${doc.errorMessage?`<div style="font-size:11px;color:var(--destructive);margin-top:6px;padding:4px 8px;background:var(--destructive-l);border-radius:var(--rs-xs)">${esc(doc.errorMessage)}</div>`:''}
    </div>`).join('');
  }
  if(state.currentKbTab==='chat'){
    const convs=state.data.kbConv||[];
    if(!convs.length)return `<div style="display:flex;align-items:center;justify-content:center;height:100%;padding:24px"><div style="text-align:center;color:var(--muted-foreground);max-width:320px">
      <div style="font-size:36px;margin-bottom:12px;opacity:.25">💬</div>
      <div style="font-size:15px;font-weight:600;margin-bottom:6px;color:var(--foreground)">开始知识库问答</div>
      <div style="font-size:13px;line-height:1.6">在下方输入问题，AI 将从知识库检索相关内容并生成回答</div>
    </div></div>`;
    return`<div class="kb-chat-messages">${convs.map(c=>{
      if(c.role==='user')return`<div class="chat-msg user"><div class="chat-avatar">U</div><div class="chat-bubble">${esc(c.content||c.question||'')}</div></div>`;
      if(c.content==='__typing__')return`<div class="chat-msg asst"><div class="chat-avatar">AI</div><div class="chat-bubble"><div class="typing-indicator"><span class="typing-dot"></span><span class="typing-dot"></span><span class="typing-dot"></span></div></div></div>`;
      if(c.content==='__error__'){
        return`<div class="chat-msg asst"><div class="chat-avatar">AI</div><div class="chat-bubble error" style="max-width:520px">
          <div style="font-weight:600;margin-bottom:6px">⚠ 回答生成失败</div>
          <div style="font-size:13px;line-height:1.6">${esc(c.error||'未知错误')}</div>
          <div style="font-size:12px;color:var(--muted-foreground);margin-top:8px;padding-top:8px;border-top:1px solid var(--border)">可能是上游模型服务暂时不可用，可重试或更换模型</div>
          <button class="btn btn-primary btn-sm" style="margin-top:12px" onclick="retryKbAsk(${JSON.stringify(c.retryQ||'').replace(/"/g,'&quot;')},'${esc(c.retryModel||'')}')">${I.refresh} 重试</button>
        </div></div>`;
      }
      let msgText=c.content||c.answer||'';
      let html=`<div class="chat-msg asst"><div class="chat-avatar">AI</div><div class="chat-bubble md-content">${c.isDeepResearch?'<span class="badge badge-blue" style="margin-bottom:6px;display:inline-block">🔬 深度研究</span><br>':''}${renderMd(msgText)}<div class="chat-msg-actions"><button class="btn btn-ghost btn-sm" onclick="copyText(${JSON.stringify(msgText).replace(/</g,'\u003c').replace(/>/g,'\u003e')})" title="复制回答">📋 复制</button></div></div></div>`;
      if(c.sources&&c.sources.length){
        html+=`<div class="chat-sources">
          <div class="chat-sources-title">📚 引用来源 (${c.sources.length})</div>
          ${c.sources.slice(0,5).map(s=>`<div class="chat-source-item"><span class="chat-source-name">${esc(s.filename||s.source||'')} ${s.content?'· '+esc(s.content.substring(0,80))+(s.content.length>80?'...':''):''}</span><span class="chat-source-score">${(s.score||0).toFixed(3)}</span></div>`).join('')}
          ${c.sources.length>5?`<div style="color:var(--muted-foreground);font-size:11px;margin-top:4px;text-align:center">还有 ${c.sources.length-5} 个来源未展示</div>`:''}
        </div>`;
      }
      return html;
    }).join('')}</div>`;
  }
  if(state.currentKbTab==='search'){
    if(!state.data.kbSearchResults)return emptyState('搜索知识库','支持向量检索、关键词检索、混合检索三种模式');
    const results=state.data.kbSearchResults||[];
    if(!results.length)return emptyState('无搜索结果','尝试更换关键词或调整搜索模式');
    const hlQuery=(state.data._lastKbSearchQuery||'').trim();
    return results.map((r,i)=>{const snippet=(r.content||r.snippet||'').substring(0,300);const fullLen=(r.content||'').length;
      return`<div class="kb-search-result">
      <div class="kb-search-result-header">
        <div style="display:flex;gap:6px;align-items:center">
          <span class="badge badge-blue">#${i+1}</span>
          <span style="font-weight:600;font-size:13px">${esc(r.filename||r.source||'未知来源')}</span>
        </div>
        <span class="badge badge-slate">相似度 ${(r.score||0).toFixed(2)}</span>
      </div>
      <div class="kb-search-result-content">${highlightText(esc(snippet),hlQuery)}${fullLen>300?'...':''}</div>
    </div>`}).join('');
  }
  if(state.currentKbTab==='index'){
    const idx=state.data.kbIndexStatus;
    if(!idx||!idx.status)return`<div style="display:flex;flex-direction:column;align-items:center;justify-content:center;height:100%;padding:24px">
      <div style="font-size:36px;margin-bottom:12px;opacity:.25">📊</div>
      <div style="font-size:15px;font-weight:600;margin-bottom:6px;color:var(--foreground)">HNSW 向量索引未构建</div>
      <div style="font-size:13px;color:var(--muted-foreground);margin-bottom:16px;text-align:center;max-width:280px;line-height:1.5">构建索引后可进行向量检索和 RAG 问答</div>
      <button class="btn btn-primary" onclick="buildIndex()">${I.database} 构建索引</button>
    </div>`;
    return`<div style="padding:24px;max-width:500px;margin:0 auto">
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:20px">
        <span style="font-weight:600;font-size:15px">索引状态</span>
        <span class="badge ${idx.status==='ready'?'badge-green':idx.status==='building'?'badge-amber':'badge-slate'}" style="font-size:12px;padding:4px 10px">${esc(idx.status||'unknown')}</span>
      </div>
      <div class="kb-index-grid">
        <div class="kb-index-stat"><div class="kb-index-stat-value">${(idx.vectorCount||idx.chunkCount||0).toLocaleString()}</div><div class="kb-index-stat-label">向量数量</div></div>
        <div class="kb-index-stat"><div class="kb-index-stat-value">${idx.dimension||idx.embeddingDim||0}</div><div class="kb-index-stat-label">向量维度</div></div>
      </div>
      <div class="kb-index-info">
        <div class="kb-index-info-row"><span class="kb-index-info-label">索引算法</span><span class="kb-index-info-value">HNSW</span></div>
        <div class="kb-index-info-row"><span class="kb-index-info-label">距离度量</span><span class="kb-index-info-value">Cosine (1-cos)</span></div>
        <div class="kb-index-info-row"><span class="kb-index-info-label">maxM / efSearch</span><span class="kb-index-info-value">16 / 50</span></div>
      </div>
      <div style="display:flex;gap:8px;justify-content:center;margin-top:16px">
        <button class="btn btn-primary" onclick="buildIndex()">${I.refresh} 重建索引</button>
        <button class="btn btn-danger" onclick="confirmDel('索引','当前索引',()=>dropIndex())">${I.trash} 删除索引</button>
      </div>
    </div>`;
  }
  if(state.currentKbTab==='sources'){
    const sources=state.data.kbSources||[];
    if(!sources.length)return emptyState('暂无导入来源','点击右上角"导入来源"添加');
    return sources.map(s=>`<div class="kb-source-item">
      <div style="display:flex;justify-content:space-between;align-items:start">
        <div style="flex:1;min-width:0">
          <div style="font-weight:600;font-size:13px">${esc(s.name||s.sourceType||'source')}</div>
          <div style="font-size:11px;color:var(--muted-foreground);margin-top:4px;font-family:'SF Mono',Monaco,monospace;word-break:break-all">${esc(s.sourceType||'')} · ${esc(s.url||s.repoUrl||s.dirPath||'')}</div>
        </div>
        <div style="display:flex;align-items:center;gap:4px;flex-shrink:0;margin-left:8px">
          <span class="badge ${s.status==='completed'?'badge-green':s.status==='processing'?'badge-amber':'badge-slate'}">${esc(s.status||'pending')}</span>
          <button class="btn btn-ghost btn-sm" onclick="confirmDel('来源','${esc(s.name||s.sourceType)}',()=>delSource('${s.id||s.sourceId}'))" title="删除" style="padding:4px 8px">${I.trash}</button>
        </div>
      </div>
    </div>`).join('');
  }
  if(state.currentKbTab==='tags'){
    const tags=state.data.kbTags;
    if(tags===null)return '<div style="display:flex;align-items:center;justify-content:center;height:100%;padding:24px"><span class="loading"></span> 加载标签中...</div>';
    if(!tags.length)return emptyState('暂无标签','上传文档后自动提取关键词标签');
    const maxCount=Math.max(...tags.map(t=>t.count),1);
    return`<div style="padding:24px">
      <div style="font-size:13px;color:var(--muted-foreground);margin-bottom:16px;text-align:center">基于文档分块内容自动提取的高频关键词 · 中英文词频分析</div>
      <div class="tag-cloud" style="justify-content:center">
        ${tags.map(t=>{
          const ratio=t.count/maxCount;
          const size=ratio>0.6?'lg':ratio>0.3?'md':'sm';
          const bg=ratio>0.6?'var(--primary)':ratio>0.3?'var(--primary-l)':'var(--slate-l)';
          const color=ratio>0.3?'#fff':'var(--muted-foreground)';
          return `<span class="tag-cloud-item ${size}" style="background:${bg};color:${color};border-color:rgba(47,111,237,.12)">${esc(t.word)}<span class="tag-cloud-count">${t.count}</span></span>`;
        }).join('')}
      </div>
    </div>`;
  }
  return'';
}

function switchKbTab(tab){
  state.currentKbTab=tab;
  if(state.currentKbId){
    if(tab==='index'&&!state.data.kbIndexStatus)loadKbIndex(state.currentKbId);
    if(tab==='sources'&&!state.data.kbSources)loadKbSources(state.currentKbId);
    if(tab==='tags'&&!state.data.kbTags)loadKbTags(state.currentKbId);
  }
  updatePage('knowledge');
}

async function loadKb(){const r=await api('/kb');if(r.code==='0000')state.data.kb=r.data||[];updatePage('knowledge')}
// 知识库侧边栏列表局部更新（不闪动聊天区域）
function updateKbSidebar(){
  const el=document.querySelector('.kb-sidebar');
  if(!el)return;
  // 重建整个页面以更新侧边栏
  updatePage('knowledge');
}

async function selectKb(id){
  state.currentKbId=id;
  await loadKbDocs(id);await loadKbConv(id);
  state.data.kbSearchResults=null;
  if(state.currentKbTab==='index')await loadKbIndex(state.currentKbId);
  if(state.currentKbTab==='sources')await loadKbSources(state.currentKbId);
  if(state.currentKbTab==='tags')await loadKbTags(state.currentKbId);
  updatePage('knowledge');
}

async function loadKbDocs(id){const r=await api('/kb/'+id+'/documents');if(r.code==='0000'){state.data.kbDocs=r.data||[];if(state.activeTab==='knowledge'&&state.currentKbTab==='docs')updateKbContent()}}
async function loadKbConv(id){const r=await api('/kb/'+id+'/conversations');if(r.code==='0000'){state.data.kbConv=r.data||[];if(state.activeTab==='knowledge'&&state.currentKbTab==='chat')updateKbChatMessages()}}
async function loadKbIndex(id){const r=await api('/kb/'+id+'/index');if(r.code==='0000'&&r.data&&r.data.status)state.data.kbIndexStatus=r.data;else state.data.kbIndexStatus=null;if(state.activeTab==='knowledge'&&state.currentKbTab==='index')updateKbContent()}
async function loadKbSources(id){const r=await api('/kb/'+id+'/sources');if(r.code==='0000'){state.data.kbSources=r.data||[];if(state.activeTab==='knowledge'&&state.currentKbTab==='sources')updateKbContent()}}
async function loadKbTags(id){const r=await api('/kb/'+id+'/tags');if(r.code==='0000'){state.data.kbTags=r.data||[];if(state.activeTab==='knowledge'&&state.currentKbTab==='tags')updateKbContent()}}
async function refreshKbTags(){if(!state.currentKbId)return;const r=await api('/kb/'+state.currentKbId+'/tags/refresh',{method:'POST'});if(r.code==='0000'){state.data.kbTags=r.data||[];updateKbContent();}else{toast('刷新失败: '+(r.info||'未知'),'error')}}

function showDocModal(){
  uploadInProgress=false;
  showModal(`<div class="modal-header"><div class="modal-title">上传文档</div></div>
  <div class="modal-body">
  <div class="tab-bar" id="doc-tabs">
    <div class="tab-item active" onclick="switchDocTab('file')">文件选择</div>
    <div class="tab-item" onclick="switchDocTab('text')">文本粘贴</div>
    <div class="tab-item" onclick="switchDocTab('url')">URL 抓取</div>
  </div>
  <div id="doc-tab-file">
    <div class="input-group"><label class="input-label">选择文件</label><input type="file" id="doc-file" accept=".txt,.md,.pdf,.docx" style="width:100%;padding:8px;border:1px solid var(--border);border-radius:var(--rs);font-size:13px" onchange="onFileSelected(this)"><div id="doc-file-info" style="margin-top:8px;font-size:12px;color:var(--muted-foreground)"></div></div>
  </div>
  <div id="doc-tab-text" style="display:none">
    <div class="input-group"><label class="input-label">文件名</label><input class="input" id="doc-name-text" placeholder="如：guide.md"></div>
    <div class="input-group"><label class="input-label">内容</label><textarea class="input" id="doc-content-text" rows="8" placeholder="粘贴文档内容..."></textarea></div>
  </div>
  <div id="doc-tab-url" style="display:none">
    <div class="input-group"><label class="input-label">文件名</label><input class="input" id="doc-name-url" placeholder="如：web-page.md"></div>
    <div class="input-group"><label class="input-label">URL</label><input class="input" id="doc-url" placeholder="https://example.com/article"></div>
  </div>
  </div>
  </div>
  <div class="modal-actions"><button class="btn btn-secondary" onclick="closeModal()">取消</button><button class="btn btn-primary" id="doc-upload-btn" onclick="uploadDoc()">${I.upload}上传</button></div>`);
}

function switchDocTab(tab){
  document.querySelectorAll('#doc-tabs .tab-item').forEach((el,i)=>{el.classList.toggle('active',i===['file','text','url'].indexOf(tab))});
  document.getElementById('doc-tab-file').style.display=tab==='file'?'':'none';
  document.getElementById('doc-tab-text').style.display=tab==='text'?'':'none';
  document.getElementById('doc-tab-url').style.display=tab==='url'?'':'none';
}

function onFileSelected(input){
  const file=input.files[0];if(!file)return;
  document.getElementById('doc-file-info').innerHTML='文件: '+esc(file.name)+' | 大小: '+(file.size/1024).toFixed(1)+'KB';
}

async function uploadDoc(){
  if(uploadInProgress)return;
  if(!state.currentKbId)return;
  
  const activeTabEl=document.querySelector('#doc-tabs .tab-item.active');
  const tabName=activeTabEl?activeTabEl.textContent.trim():'';
  let body={};
  
  if(tabName==='文件选择'){
    const fileInput=document.getElementById('doc-file');
    if(!fileInput||!fileInput.files[0]){toast('请选择文件','error');return}
    uploadInProgress=true;
    const btn=document.getElementById('doc-upload-btn');
    btn.disabled=true;btn.innerHTML='<span class="loading"></span> 上传中...';
    
    const file=fileInput.files[0];const reader=new FileReader();
    reader.onload=async function(e){
      const content=e.target.result;const isBinary=file.type.startsWith('application/');
      body={filename:file.name,content:isBinary?btoa(content):content,fileType:file.name.split('.').pop()||'txt',sourceType:'file'};
      await doUploadDoc(body);
    };
    reader.onerror=function(){
      uploadInProgress=false;
      btn.disabled=false;btn.innerHTML=I.upload+'上传';
      toast('文件读取失败','error');
    };
    if(file.type.startsWith('application/'))reader.readAsBinaryString(file);else reader.readAsText(file);
    return;
  }else if(tabName==='文本粘贴'){body={filename:val('doc-name-text'),content:val('doc-content-text'),fileType:'md',sourceType:'text'}}
  else if(tabName==='URL 抓取'){body={filename:val('doc-name-url')||'url-content.md',content:val('doc-url'),fileType:'md',sourceType:'url'}}
  if(!body.filename){toast('请输入文件名','error');return}
  if(!body.content){toast('内容不能为空','error');return}
  
  uploadInProgress=true;
  const btn=document.getElementById('doc-upload-btn');
  if(btn){btn.disabled=true;btn.innerHTML='<span class="loading"></span> 上传中...';}
  await doUploadDoc(body);
}

async function doUploadDoc(body){
  const r=await api('/kb/'+state.currentKbId+'/documents',{method:'POST',body:JSON.stringify(body)});
  uploadInProgress=false;
  if(r.code==='0000'){toast('文档上传成功，正在处理...');closeModal();await loadKbDocs(state.currentKbId);startDocPolling()}
  else{
    toast(r.info||'上传失败','error');
    const btn=document.getElementById('doc-upload-btn');
    if(btn){btn.disabled=false;btn.innerHTML=I.upload+'上传';}
  }
}

function startDocPolling(){
  if(docPollTimer)clearInterval(docPollTimer);
  docPollTimer=setInterval(async()=>{
    if(!state.currentKbId){clearInterval(docPollTimer);docPollTimer=null;return}
    await loadKbDocs(state.currentKbId);
    const hasProcessing=(state.data.kbDocs||[]).some(d=>d.status==='processing'||d.status==='pending');
    if(!hasProcessing){clearInterval(docPollTimer);docPollTimer=null}
    if(state.activeTab==='knowledge'&&state.currentKbTab==='docs')updateKbContent();
  },3000);
}

async function delDoc(docId){const r=await api('/kb/'+state.currentKbId+'/documents/'+docId,{method:'DELETE'});if(r.code==='0000'){toast('文档已删除');loadKbDocs(state.currentKbId)}else toast(r.info||'删除失败','error')}
async function reindexDoc(docId){
  toast('重新索引请求已发送','info');
  const r=await api('/kb/'+state.currentKbId+'/documents/'+docId+'/reindex',{method:'POST'});
  if(r.code==='0000'){toast('索引重建已触发');startDocPolling()}else toast(r.info||'操作失败','error');
}

function kbModelOptions(){
  const models=[...new Set((state.data.channels||[]).filter(c=>c.status===1).flatMap(c=>c.models||[]))];
  const cur=state.kbAskModel||'';
  if(!models.length)return '<option value="gpt-4.1">gpt-4.1 (默认)</option>';
  let html=models.map(m=>`<option value="${esc(m)}"${m===cur?' selected':''}>${esc(m)}</option>`).join('');
  if(!models.includes(cur)&&cur)html=`<option value="${esc(cur)}" selected>${esc(cur)}</option>`+html;
  return html;
}

function parseKbError(r){
  const info=r.info||'';
  try{
    const m=info.match(/\{.*\}/s);
    if(m){const j=JSON.parse(m[0]);if(j.error?.message)return j.error.message}
  }catch(e){}
  if(info.includes('504')||info.includes('upstream'))return '上游模型服务超时或连接中断（504）';
  if(info.includes('500'))return '上游服务内部错误（500）';
  if(info.includes('network is unreachable'))return '网络不可达，无法连接上游服务';
  return info||'回答生成失败，请稍后重试';
}
async function retryKbAsk(q,model){
  if(!q)return;
  const convs=state.data.kbConv||[];
  for(let i=convs.length-1;i>=0;i--){
    if(convs[i].content==='__error__'){convs[i].content='__typing__';break}
  }
  state.data.kbConv=[...convs];
  updateKbContent();scrollChatToBottom();
  const reqBody={question:q,topK:3};
  if(model)reqBody.model=model;
  const r=await api('/kb/'+state.currentKbId+'/ask',{method:'POST',body:JSON.stringify(reqBody)});
  const arr=state.data.kbConv.filter(c=>c.content!=='__typing__');
  if(r.code==='0000'){
    state.data.kbConv=[...arr,{role:'assistant',content:r.data?.answer||'无回答',sources:r.data?.sources||[],tokensUsed:r.data?.tokensUsed||0}];
  } else {
    state.data.kbConv=[...arr,{role:'assistant',content:'__error__',error:parseKbError(r),retryQ:q,retryModel:model}];
  }
  updateKbContent();scrollChatToBottom();
}

async function kbAsk(){
  const inputEl=document.getElementById('kbAskInput');
  const q=inputEl.value.trim();if(!q)return;
  inputEl.value='';
  inputEl.style.height='auto';
  state.data.kbConv=[...state.data.kbConv,{role:'user',content:q}];
  state.data.kbConv=[...state.data.kbConv,{role:'assistant',content:'__typing__'}];
  // 先全量渲染 panel-body 以创建 .kb-chat-messages 容器（首次对话时需要）
  updateKbContent();
  scrollChatToBottom();
  inputEl.focus();
  
  const askModel=state.kbAskModel||'';
  const reqBody={question:q,topK:3};
  if(askModel)reqBody.model=askModel;
  const r=await api('/kb/'+state.currentKbId+'/ask',{method:'POST',body:JSON.stringify(reqBody)});
  state.data.kbConv=state.data.kbConv.filter(c=>c.content!=='__typing__');
  if(r.code==='0000'){
    state.data.kbConv=[...state.data.kbConv,{role:'assistant',content:r.data?.answer||'无回答',sources:r.data?.sources||[],tokensUsed:r.data?.tokensUsed||0}];
  } else {
    state.data.kbConv=[...state.data.kbConv,{role:'assistant',content:'__error__',error:parseKbError(r),retryQ:q,retryModel:askModel}];
  }
  updateKbContent();
  scrollChatToBottom();
}

function scrollChatToBottom(){
  requestAnimationFrame(()=>{
    requestAnimationFrame(()=>{
      const panel=document.querySelector('.kb-panel-body');
      const msgs=document.querySelector('.kb-chat-messages');
      if(panel)panel.scrollTop=panel.scrollHeight;
      if(msgs)msgs.scrollTop=msgs.scrollHeight;
      // 兜底：再延迟一次确保图片/内容渲染完成
      setTimeout(()=>{
        if(panel)panel.scrollTop=panel.scrollHeight;
      },100);
    });
  });
}

async function kbSearch(){
  const q=document.getElementById('kbSearchInput').value.trim();if(!q)return;
  const modeEl=document.getElementById('kbSearchMode');
  const mode=modeEl?modeEl.value:'hybrid';
  document.getElementById('kbSearchInput').value='';
  // 记录查询词用于高亮
  state.data._lastKbSearchQuery=q;
  // 显示搜索中状态
  state.data.kbSearchResults=null;
  updateKbContent();
  const el=document.querySelector('.kb-panel-body');
  if(el)el.innerHTML='<div style="display:flex;align-items:center;justify-content:center;height:100%;padding:24px"><span class="loading"></span> 搜索中...</div>';
  const r=await api('/kb/'+state.currentKbId+'/search',{method:'POST',body:JSON.stringify({query:q,searchMode:mode,topK:5})});
  if(r.code==='0000'){state.data.kbSearchResults=r.data||[];if(!state.data.kbSearchResults.length)toast('无搜索结果','info')}
  else{toast(r.info||'搜索失败','error')}
  updateKbContent();
}

async function buildIndex(){
  if(!state.currentKbId)return;
  toast('正在构建索引...','info');
  const r=await api('/kb/'+state.currentKbId+'/index',{method:'POST'});
  if(r.code==='0000'){toast('索引构建已触发');await loadKbIndex(state.currentKbId);startIndexPolling()}
  else toast(r.info||'构建失败','error');
}
function startSourcePolling(){
  if(sourcePollTimer)clearInterval(sourcePollTimer);
  sourcePollTimer=setInterval(async()=>{
    if(!state.currentKbId){clearInterval(sourcePollTimer);sourcePollTimer=null;return}
    await loadKbSources(state.currentKbId);
    const hasProcessing=(state.data.kbSources||[]).some(s=>s.status==='processing'||s.status==='pending');
    if(!hasProcessing){clearInterval(sourcePollTimer);sourcePollTimer=null}
    if(state.activeTab==='knowledge'&&state.currentKbTab==='sources')updateKbContent();
  },3000);
}
function startIndexPolling(){
  if(indexPollTimer)clearInterval(indexPollTimer);
  indexPollTimer=setInterval(async()=>{
    if(!state.currentKbId){clearInterval(indexPollTimer);indexPollTimer=null;return}
    await loadKbIndex(state.currentKbId);
    const building=state.data.kbIndexStatus&&state.data.kbIndexStatus.status==='building';
    if(!building){clearInterval(indexPollTimer);indexPollTimer=null}
    if(state.activeTab==='knowledge'&&state.currentKbTab==='index')updateKbContent();
  },3000);
}
async function dropIndex(){
  if(!state.currentKbId)return;
  const r=await api('/kb/'+state.currentKbId+'/index',{method:'DELETE'});
  if(r.code==='0000'){toast('索引已删除');state.data.kbIndexStatus=null;updateKbContent()}
  else toast(r.info||'删除失败','error');
}

function showKbModal(){
  showModal(`<div class="modal-header"><div class="modal-title">创建知识库</div></div>
  <div class="modal-body">
  <div class="input-group"><label class="input-label">名称</label><input class="input" id="kb-name" placeholder="如：产品文档库"></div>
  <div class="input-group"><label class="input-label">描述</label><input class="input" id="kb-desc" placeholder="知识库描述"></div>
  <div class="input-group"><label class="input-label">Embedding 模型</label><input class="input" id="kb-emb" value="text-embedding-3-small"></div>
  <div style="display:grid;grid-template-columns:1fr 1fr;gap:12px">
    <div class="input-group"><label class="input-label">分块大小</label><input class="input" type="number" id="kb-cs" value="512"></div>
    <div class="input-group"><label class="input-label">重叠</label><input class="input" type="number" id="kb-co" value="64"></div>
  </div>
  </div>
  </div>
  <div class="modal-actions"><button class="btn btn-secondary" onclick="closeModal()">取消</button><button class="btn btn-primary" onclick="addKb()">创建</button></div>`);
}

async function addKb(){
  const body={name:val('kb-name'),description:val('kb-desc'),embeddingModel:val('kb-emb'),chunkSize:+val('kb-cs'),chunkOverlap:+val('kb-co')};
  if(!body.name){toast('请输入知识库名称','error');return}
  const r=await api('/kb',{method:'POST',body:JSON.stringify(body)});
  if(r.code==='0000'){toast('知识库创建成功');closeModal();loadKb()}else toast(r.info||'创建失败','error');
}
async function delKb(id){const r=await api('/kb/'+id,{method:'DELETE'});if(r.code==='0000'){toast('知识库已删除');state.currentKbId=null;state.data.kbDocs=[];state.data.kbConv=[];loadKb()}else toast(r.info||'删除失败','error')}

function showImportModal(){
  showModal(`<div class="modal-header"><div class="modal-title">导入来源</div></div>
  <div class="modal-body">
  <div class="input-group"><label class="input-label">来源类型</label><select class="input" id="imp-type" onchange="toggleImpFields()">
    <option value="git">Git 仓库</option><option value="url">URL 抓取</option><option value="dir">本地目录</option>
  </select></div>
  <div id="imp-git-fields">
    <div class="input-group"><label class="input-label">仓库 URL</label><input class="input" id="imp-repo-url" placeholder="https://github.com/user/repo.git"></div>
    <div style="display:grid;grid-template-columns:1fr 1fr;gap:12px">
      <div class="input-group"><label class="input-label">分支</label><input class="input" id="imp-branch" value="main"></div>
      <div class="input-group"><label class="input-label">Token (可选)</label><input class="input" type="password" id="imp-token" placeholder="私有仓库需要"></div>
    </div>
  </div>
  <div id="imp-url-fields" style="display:none">
    <div class="input-group"><label class="input-label">URL</label><input class="input" id="imp-url" placeholder="https://example.com/docs"></div>
  </div>
  <div id="imp-dir-fields" style="display:none">
    <div class="input-group"><label class="input-label">目录路径</label><input class="input" id="imp-dir" placeholder="/path/to/docs"></div>
  </div>
  <div class="input-group"><label class="input-label">排除目录 (逗号分隔)</label><input class="input" id="imp-excluded" value=".git,node_modules,target,dist"></div>
  </div>
  </div>
  <div class="modal-actions"><button class="btn btn-secondary" onclick="closeModal()">取消</button><button class="btn btn-primary" onclick="importSource()">导入</button></div>`);
}

function toggleImpFields(){
  const t=val('imp-type');
  document.getElementById('imp-git-fields').style.display=t==='git'?'':'none';
  document.getElementById('imp-url-fields').style.display=t==='url'?'':'none';
  document.getElementById('imp-dir-fields').style.display=t==='dir'?'':'none';
}

async function importSource(){
  if(!state.currentKbId){toast('请先选择知识库','error');return}
  const type=val('imp-type');
  const body={sourceType:type==='dir'?'local_dir':type,excludedDirs:val('imp-excluded').split(',').map(s=>s.trim()).filter(Boolean)};
  if(type==='git'){body.repoUrl=val('imp-repo-url');body.branch=val('imp-branch')||'main';body.token=val('imp-token')}
  else if(type==='url'){body.url=val('imp-url')}
  else if(type==='dir'){body.dirPath=val('imp-dir')}
  const r=await api('/kb/'+state.currentKbId+'/sources',{method:'POST',body:JSON.stringify(body)});
  if(r.code==='0000'){toast('导入任务已创建','info');closeModal();await loadKbSources(state.currentKbId);startSourcePolling()}
  else toast(r.info||'导入失败','error');
}
async function delSource(sourceId){
  if(!state.currentKbId)return;
  const r=await api('/kb/'+state.currentKbId+'/sources/'+sourceId,{method:'DELETE'});
  if(r.code==='0000'){toast('来源已删除');await loadKbSources(state.currentKbId)}
  else toast(r.info||'删除失败','error');
}

// 深度研究：多轮迭代检索
async function kbDeepResearch(){
  const inputEl=document.getElementById('kbAskInput');
  const q=inputEl?inputEl.value.trim():'';
  if(!q){toast('请先输入问题再点击深度研究','error');return}
  if(!state.currentKbId)return;
  inputEl.value='';
  inputEl.style.height='auto';

  const askModel=state.kbAskModel||'';
  const reqBody={question:q,topK:5};
  if(askModel)reqBody.model=askModel;

  // 添加用户消息 + 研究中指示
  state.data.kbConv=[...state.data.kbConv,
    {role:'user',content:q},
    {role:'assistant',content:'__typing__',researchMode:true}
  ];
  updateKbContent();scrollChatToBottom();
  inputEl.focus();

  const btn=document.getElementById('kbDeepResearchBtn');
  if(btn){btn.disabled=true;btn.innerHTML='<span class="loading"></span> 研究中...';}

  try{
    const r=await api('/kb/'+state.currentKbId+'/deep-research',{method:'POST',body:JSON.stringify(reqBody)});
    state.data.kbConv=state.data.kbConv.filter(c=>!(c.content==='__typing__'&&c.researchMode));
    if(r.code==='0000'){
      const d=r.data||{};
      state.data.kbConv=[...state.data.kbConv,{
        role:'assistant',
        content:d.answer||'深度研究未返回结果',
        sources:d.sources||[],
        tokensUsed:d.tokensUsed||0,
        isDeepResearch:true
      }];
      toast('深度研究完成','success');
    } else {
      state.data.kbConv=[...state.data.kbConv,{role:'assistant',content:'__error__',error:parseKbError(r),retryQ:q,retryModel:askModel}];
      toast('深度研究失败','error');
    }
  }catch(e){
    state.data.kbConv=state.data.kbConv.filter(c=>!(c.content==='__typing__'&&c.researchMode));
    state.data.kbConv=[...state.data.kbConv,{role:'assistant',content:'__error__',error:'网络错误：'+e.message,retryQ:q,retryModel:askModel}];
    toast('深度研究失败','error');
  }finally{
    if(btn){btn.disabled=false;btn.innerHTML='🔬 深度研究';}
    updateKbContent();scrollChatToBottom();
  }
}
