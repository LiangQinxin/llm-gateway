// Wiki 页面 - 知识库 Wiki 系统
let wikiSourcePollTimer=null;

// 简单 Markdown 渲染
function renderMd(text){
  if(!text)return'';
  let html=esc(text);
  // 代码块 ```lang\n...```
  html=html.replace(/```(\w*)\n?([\s\S]*?)```/g,(m,lang,code)=>{
    return'<pre class="wiki-md-code"><code>'+code.replace(/\n$/,'')+'</code></pre>';
  });
  // 行内代码 `code`
  html=html.replace(/`([^`]+)`/g,'<code class="wiki-md-inline">$1</code>');
  // 标题 ### / ## / #
  html=html.replace(/^###\s+(.+)$/gm,'<h4 class="wiki-md-h4">$1</h4>');
  html=html.replace(/^##\s+(.+)$/gm,'<h3 class="wiki-md-h3">$1</h3>');
  html=html.replace(/^#\s+(.+)$/gm,'<h2 class="wiki-md-h2">$1</h2>');
  // 粗体 **text**
  html=html.replace(/\*\*([^*]+)\*\*/g,'<strong>$1</strong>');
  // 斜体 *text*
  html=html.replace(/\*([^*]+)\*/g,'<em>$1</em>');
  // 链接 [text](url)
  html=html.replace(/\[([^\]]+)\]\(([^)]+)\)/g,'<a href="$2" target="_blank" class="wiki-md-link">$1</a>');
  // 无序列表 - item
  html=html.replace(/^- (.+)$/gm,'<li class="wiki-md-li">$1</li>');
  html=html.replace(/(<li class="wiki-md-li">[\s\S]*?<\/li>)/g,'<ul class="wiki-md-ul">$1</ul>');
  // 有序列表 1. item
  html=html.replace(/^\d+\.\s+(.+)$/gm,'<li class="wiki-md-oli">$1</li>');
  // 段落换行
  html=html.replace(/\n\n/g,'</p><p class="wiki-md-p">');
  html=html.replace(/\n/g,'<br>');
  return'<div class="wiki-md"><p class="wiki-md-p">'+html+'</p></div>';
}

function pgWiki(){
  const w=state.data.wikiData||{projects:[],pages:[],sources:[],tags:[],conv:[],searchResults:null,currentProjectId:null,currentTab:'pages'};
  return`<div class="page-shell"><div class="page-header"><div><div class="page-title">Wiki</div><div class="page-subtitle">知识库 Wiki · 文档管理 · 智能问答</div></div><div style="display:flex;gap:8px"><button class="btn btn-secondary" onclick="showWikiSourceModal()">${I.import}添加来源</button><button class="btn btn-primary" onclick="showWikiProjectModal()">${I.plus}创建项目</button></div></div>
  <div class="kb-layout">
    <div class="kb-sidebar">
      <div class="kb-panel" style="height:100%">
        <div class="kb-panel-header"><span style="font-weight:600;font-size:13px">Wiki 项目</span><span class="badge badge-slate">${(w.projects||[]).length}</span></div>
        <div class="kb-panel-body">
          ${(w.projects||[]).map(p=>{
            return`<div class="kbItem${w.currentProjectId===p.id?' active':''}" onclick="selectWikiProject('${p.id}')">
              <div style="display:flex;justify-content:space-between;align-items:start">
                <div style="flex:1;min-width:0">
                  <div style="font-weight:600;font-size:13px">${esc(p.name)}</div>
                  <div style="font-size:11px;color:var(--muted-foreground);margin-top:3px;line-height:1.4">${esc(p.description||'无描述')}</div>
                </div>
                <button class="btn btn-ghost btn-sm" onclick="event.stopPropagation();confirmDel('Wiki项目','${esc(p.name)}',()=>delWikiProject('${p.id}'))" style="flex-shrink:0;margin-left:4px;padding:4px 8px" title="删除">${I.trash}</button>
              </div>
              <div class="kb-item-info">
                ${p.pageCount!=null?`<span style="color:var(--border-strong)">${p.pageCount} 页面</span>`:''}
                ${p.sourceCount!=null?`<span style="color:var(--border-strong)">${p.sourceCount} 来源</span>`:''}
              </div>
            </div>`;
          }).join('')||`<div class="kb-empty-mini"><div class="kb-empty-mini-title">暂无 Wiki 项目</div><div class="kb-empty-mini-desc">点击右上角创建</div></div>`}
        </div>
      </div>
    </div>
    <div class="kb-main">
      <div class="kb-panel">
        <div class="kb-panel-header">
          ${w.currentProjectId?`<div class="kb-tabs">
            <button class="kb-tab ${w.currentTab==='pages'?'active':''}" onclick="switchWikiTab('pages')">页面</button>
            <button class="kb-tab ${w.currentTab==='search'?'active':''}" onclick="switchWikiTab('search')">搜索</button>
            <button class="kb-tab ${w.currentTab==='chat'?'active':''}" onclick="switchWikiTab('chat')">问答</button>
            <button class="kb-tab ${w.currentTab==='graph'?'active':''}" onclick="switchWikiTab('graph')">图谱</button>
            <button class="kb-tab ${w.currentTab==='sources'?'active':''}" onclick="switchWikiTab('sources')">来源</button>
            <button class="kb-tab ${w.currentTab==='tags'?'active':''}" onclick="switchWikiTab('tags')">标签</button>
          </div>`:''}
          ${w.currentProjectId&&w.currentTab==='sources'?`<button class="btn btn-secondary btn-sm" onclick="showWikiSourceModal()">${I.plus}添加来源</button>`:''}
        </div>
        <div class="kb-panel-body" id="wiki-main-content">
          ${w.currentProjectId?renderWikiContent():emptyState('选择 Wiki 项目','从左侧选择或创建新项目')}
        </div>
        ${w.currentProjectId&&w.currentTab==='chat'?`<div class="kb-chat-footer">
          <div class="chat-input-wrap">
            <textarea class="kb-chat-input" id="wikiAskInput" placeholder="输入问题，Wiki 检索回答..." rows="1" onkeydown="if(event.key==='Enter'&&!event.shiftKey){event.preventDefault();wikiAsk()}" oninput="this.style.height='auto';this.style.height=Math.min(this.scrollHeight,120)+'px'"></textarea>
            <button class="chat-send-btn" onclick="wikiAsk()">${I.send}</button>
          </div>
          <div class="chat-input-meta">
            <span class="chat-input-hint"><kbd>Enter</kbd> 发送 · <kbd>Shift+Enter</kbd> 换行</span>
          </div>
        </div>`:''}
        ${w.currentProjectId&&w.currentTab==='search'?`<div class="kb-panel-footer">
          <div class="search-input-wrap">
            <input class="input search-input" id="wikiSearchInput" placeholder="搜索 Wiki 页面..." onkeydown="if(event.key==='Enter')wikiSearch()">
            <button class="btn btn-primary search-btn" onclick="wikiSearch()">${I.search}搜索</button>
          </div>
        </div>`:''}
      </div>
    </div>
  </div>`;
}

function renderWikiContent(){
  const w=state.data.wikiData;
  if(w.currentTab==='pages'){
    const pages=w.pages||[];
    if(!pages.length)return emptyState('暂无页面','添加来源并摄入后将自动生成页面');
    // 查看单个页面
    if(w.viewPagePath){
      const pg=pages.find(p=>(p.path||p.slug)===w.viewPagePath);
      if(!pg){w.viewPagePath=null;return emptyState('页面未找到','')}
      return`<div class="kb-doc-detail">
        <div class="kb-doc-detail-header">
          <button class="btn btn-ghost btn-sm" onclick="state.data.wikiData.viewPagePath=null;updatePage('wiki')" style="margin-right:8px">← 返回</button>
          <div style="flex:1">
            <div style="font-weight:700;font-size:16px">${esc(pg.title||pg.slug||'未命名')}</div>
            <div style="font-size:12px;color:var(--muted-foreground);margin-top:4px">${esc(pg.path||pg.slug||'')} · ${(pg.tokenCount||0).toLocaleString()} tokens${pg.tags?` · 标签: ${esc(Array.isArray(pg.tags)?pg.tags.join(', '):pg.tags)}`:''}</div>
          </div>
          <div style="display:flex;gap:6px">
            <button class="btn btn-ghost btn-sm" onclick="editWikiPage('${esc(pg.path||pg.slug||'') }') " title="编辑页面">✏ 编辑</button>
            <button class="btn btn-ghost btn-sm" onclick="copyText(${JSON.stringify(pg.content||'').replace(/"/g,'&quot;')})" title="复制内容">📋 复制</button>
          </div>
        </div>
        <div class="kb-doc-detail-body md-content" style="padding:16px 20px;max-height:none">${renderMd(pg.content||'')}</div>
      </div>`;
    }
    return pages.map(pg=>`<div class="kb-doc-item" style="cursor:pointer" onclick="viewWikiPage('${esc(pg.path||pg.slug||'')}'  )">
      <div class="kb-doc-item-header">
        <div class="kb-doc-item-info">
          <div class="kb-doc-item-name">${esc(pg.title||pg.slug||'未命名')}</div>
          <div class="kb-doc-item-meta">${esc(pg.pageType||pg.sourceType||'')} · ${pg.chunkCount||0} 块 · ${(pg.tokenCount||0).toLocaleString()} tokens</div>
        </div>
        <div style="display:flex;align-items:center;gap:4px;flex-shrink:0;margin-left:8px">
          ${pg.status?`<span class="badge ${docStatusBadge(pg.status)}">${esc(pg.status)}</span>`:''}
          ${pg.tags?`<span class="badge badge-slate">${esc(Array.isArray(pg.tags)?pg.tags.length:1)} tags</span>`:''}
        </div>
      </div>
      ${pg.summary?`<div style="font-size:12px;color:var(--muted-foreground);margin-top:6px;line-height:1.5">${esc(truncate(pg.summary,200))}</div>`:''}
    </div>`).join('');
  }
  if(w.currentTab==='chat'){
    const convs=w.conv||[];
    if(!convs.length)return `<div style="display:flex;align-items:center;justify-content:center;height:100%;padding:24px"><div style="text-align:center;color:var(--muted-foreground);max-width:320px">
      <div style="font-size:36px;margin-bottom:12px;opacity:.25">💬</div>
      <div style="font-size:15px;font-weight:600;margin-bottom:6px;color:var(--foreground)">开始 Wiki 问答</div>
      <div style="font-size:13px;line-height:1.6">在下方输入问题，AI 将从 Wiki 检索相关内容并生成回答</div>
    </div></div>`;
    return`<div class="kb-chat-messages">${renderWikiChatMessages(convs)}</div>`;
  }
  if(w.currentTab==='search'){
    if(w._searching)return `<div style="display:flex;align-items:center;justify-content:center;height:100%"><div style="text-align:center;color:var(--muted-foreground)"><div class="typing-indicator" style="margin-bottom:12px"><span class="typing-dot"></span><span class="typing-dot"></span><span class="typing-dot"></span></div><div style="font-size:13px">正在搜索 "${esc(w._lastSearchQuery||'')}"...</div></div></div>`;
    if(!w.searchResults)return emptyState('搜索 Wiki','输入关键词搜索 Wiki 页面内容');
    const results=w.searchResults||[];
    if(!results.length)return emptyState('无搜索结果','尝试更换关键词');
    const wikiHlQuery=(w._lastSearchQuery||'').trim();
    return results.map((r,i)=>{const rawSnippet=r.snippet||r.content||'';const snippet=rawSnippet.substring(0,300);const fullLen=rawSnippet.length;
      const displaySnippet=snippet?highlightText(esc(snippet),wikiHlQuery):'<span style="color:var(--muted-foreground);font-size:12px">'+esc(r.path||'')+'</span>';
      return`<div class="kb-search-result">
      <div class="kb-search-result-header">
        <div style="display:flex;gap:6px;align-items:center">
          <span class="badge badge-blue">#${i+1}</span>
          <span style="font-weight:600;font-size:13px">${esc(r.title||r.filename||r.source||'未知来源')}</span>
        </div>
        <span class="badge badge-slate">相似度 ${(r.score||0).toFixed(2)}</span>
      </div>
      <div class="kb-search-result-content">${displaySnippet}${fullLen>300?'...':''}</div>
    </div>`}).join('');
  }
  if(w.currentTab==='sources'){
    const sources=w.sources||[];
    if(!sources.length)return emptyState('暂无来源','点击右上角"添加来源"导入');
    return sources.map(s=>`<div class="kb-source-item">
      <div style="display:flex;justify-content:space-between;align-items:start">
        <div style="flex:1;min-width:0">
          <div style="font-weight:600;font-size:13px">${esc(s.name||s.sourceType||'source')}</div>
          <div style="font-size:11px;color:var(--muted-foreground);margin-top:4px;font-family:'SF Mono',Monaco,monospace;word-break:break-all">${esc(s.sourceType||'')} · ${esc(s.url||s.repoUrl||s.dirPath||'')}</div>
        </div>
        <div style="display:flex;align-items:center;gap:4px;flex-shrink:0;margin-left:8px">
          <span class="badge ${s.status==='completed'||s.status==='ready'?'badge-green':s.status==='processing'||s.status==='ingesting'?'badge-amber':'badge-slate'}">${esc(s.status||'pending')}</span>
          ${(s.status==='pending'||s.status==='ready')?`<button class="btn btn-ghost btn-sm" onclick="ingestWikiSource('${w.currentProjectId}','${s.id||s.sourceId}')" title="摄入" style="padding:4px 8px">${I.database}</button>`:''}
          <button class="btn btn-ghost btn-sm" onclick="confirmDel('来源','${esc(s.name||s.sourceType)}',()=>delWikiSource('${w.currentProjectId}','${s.id||s.sourceId}'))" title="删除" style="padding:4px 8px">${I.trash}</button>
        </div>
      </div>
    </div>`).join('');
  }
  if(w.currentTab==='graph'){
    const g=w.graphData;
    if(!g)return emptyState('图谱数据加载中','点击下方按钮加载或重建图谱');
    const nodes=g.nodes||[];
    const edges=g.edges||[];
    if(!nodes.length)return emptyState('暂无图谱数据','添加来源并摄入后将自动生成图谱');
    // 统计信息栏 + Canvas 力导向动画图
    const edgeTypes={};
    edges.forEach(e=>{const t=e.edgeType||e.type||'unknown';edgeTypes[t]=(edgeTypes[t]||0)+1;});
    const typeColors={'wikilink':'#3b82f6','tag_share':'#10b981','same_type':'#f59e0b','reference':'#8b5cf6','unknown':'#6b7280'};
    let legendHtml='';
    for(const[t,c]of Object.entries(edgeTypes)){legendHtml+=`<div class="kb-graph-legend-item"><span class="kb-graph-legend-dot" style="background:${typeColors[t]||'#6b7280'}"></span><span>${esc(t)}</span><span class="kb-graph-legend-count">${c}</span></div>`;}
    return `<div class="kb-graph-wrap">
      <div class="kb-graph-toolbar">
        <div class="kb-graph-stats">
          <span class="kb-graph-stat"><strong>${nodes.length}</strong> 节点</span>
          <span class="kb-graph-stat"><strong>${edges.length}</strong> 边</span>
          <span class="kb-graph-stat"><strong>${Object.keys(edgeTypes).length}</strong> 类型</span>
        </div>
        <div class="kb-graph-legend">${legendHtml}</div>
        <div class="kb-graph-actions">
          <button class="btn btn-secondary btn-sm" onclick="loadWikiGraph('${w.currentProjectId}')">🔄 刷新</button>
          <button class="btn btn-secondary btn-sm" onclick="rebuildWikiGraph('${w.currentProjectId}')">🔧 重建</button>
          <button class="btn btn-ghost btn-sm" onclick="resetGraphLayout()" title="重置布局">⚡ 重排</button>
          <span class="kb-graph-zoom-group">
            <button class="btn btn-ghost btn-sm" onclick="wikiGraphZoom(1.2)" title="放大">➕</button>
            <button class="btn btn-ghost btn-sm" onclick="wikiGraphZoom(0.8)" title="缩小">➖</button>
            <button class="btn btn-ghost btn-sm" onclick="wikiGraphResetView()" title="复原">🎯</button>
          </span>
        </div>
      </div>
      <div class="kb-graph-container">
        <canvas id="wikiGraphCanvas"></canvas>
        <div class="kb-graph-tooltip" id="wikiGraphTooltip" style="display:none"></div>
      </div>
    </div>`;
  }
  if(w.currentTab==='tags'){
    const tags=w.tags||[];
    if(!tags.length)return emptyState('暂无标签','摄入来源后自动提取关键词标签');
    const maxCount=Math.max(...tags.map(t=>t.count),1);
    return`<div style="padding:24px">
      <div style="font-size:13px;color:var(--muted-foreground);margin-bottom:16px;text-align:center">Wiki 页面自动提取的关键词标签</div>
      <div class="tag-cloud" style="justify-content:center">
        ${tags.map(t=>{
          const ratio=t.count/maxCount;
          const size=ratio>0.6?'lg':ratio>0.3?'md':'sm';
          const bg=ratio>0.6?'var(--primary)':ratio>0.3?'var(--primary-l)':'var(--slate-l)';
          const color=ratio>0.3?'#fff':'var(--muted-foreground)';
          return`<span class="tag-cloud-item ${size}" style="background:${bg};color:${color};border-color:rgba(47,111,237,.12)">${esc(t.word||t.name||t.tag)}<span class="tag-cloud-count">${t.count}</span></span>`;
        }).join('')}
      </div>
    </div>`;
  }
  return'';
}

function switchWikiTab(tab){
  const w=state.data.wikiData;
  w.currentTab=tab;
  // 停止上一个 tab 的动画
  if(w._graphAnim){cancelAnimationFrame(w._graphAnim);w._graphAnim=null;}
  if(w.currentProjectId){
    if(tab==='sources')loadWikiSources(w.currentProjectId);
    if(tab==='tags')loadWikiTags(w.currentProjectId);
    if(tab==='graph')loadWikiGraph(w.currentProjectId);
  }
  updatePage('wiki');
}

// 查看单个 Wiki 页面内容
function viewWikiPage(path){
  const w=state.data.wikiData;
  w.viewPagePath=path;
  // 如果页面内容未加载，先获取
  const pg=(w.pages||[]).find(p=>(p.path||p.slug)===path);
  if(pg&&!pg.content){
    wikiFetch('/projects/'+w.currentProjectId+'/pages/'+encodeURIComponent(path)).then(r=>{
      if(!r.__error){
        pg.content=r.content||r.body||'';
        updatePage('wiki');
      }
    });
  }
  updatePage('wiki');
}

// 编辑 Wiki 页面
function editWikiPage(path){
  const w=state.data.wikiData;
  const pg=(w.pages||[]).find(p=>(p.path||p.slug)===path);
  const content=pg?(pg.content||''):'';
  const title=pg?(pg.title||pg.slug||''):'';
  const tags=pg&&pg.tags?(Array.isArray(pg.tags)?pg.tags.join(', '):pg.tags):'';
  showModal(`<div class="modal-header"><div class="modal-title">编辑页面: ${esc(title)}</div></div>
  <div class="modal-body" style="max-height:70vh;overflow-y:auto">
    <div class="input-group"><label class="input-label">标题</label><input class="input" id="wiki-edit-title" value="${esc(title)}"></div>
    <div class="input-group"><label class="input-label">标签（逗号分隔）</label><input class="input" id="wiki-edit-tags" value="${esc(tags)}"></div>
    <div class="input-group"><label class="input-label">内容（Markdown）</label>
    <div class="md-toolbar" style="display:flex;gap:4px;margin-bottom:6px;flex-wrap:wrap">
      <button class="btn btn-ghost btn-sm" type="button" onclick="mdWrap('**','**')" title="粗体">B</button>
      <button class="btn btn-ghost btn-sm" type="button" onclick="mdWrap('*','*')" title="斜体">I</button>
      <button class="btn btn-ghost btn-sm" type="button" onclick="mdWrap('~~','~~')" title="删除线">S</button>
      <button class="btn btn-ghost btn-sm" type="button" onclick="mdWrap('\`','\`')" title="行内代码">Code</button>
      <button class="btn btn-ghost btn-sm" type="button" onclick="mdPrefix('## ')" title="二级标题">H2</button>
      <button class="btn btn-ghost btn-sm" type="button" onclick="mdPrefix('- ')" title="列表项">List</button>
      <button class="btn btn-ghost btn-sm" type="button" onclick="mdPrefix('> ')" title="引用">Quote</button>
      <button class="btn btn-ghost btn-sm" type="button" onclick="mdInsert('\\n---\\n')" title="分隔线">HR</button>
      <button class="btn btn-ghost btn-sm" type="button" onclick="mdInsert('| 列1 | 列2 |\\n|---|---|\\n| | |')" title="表格">Table</button>
    </div>
    <textarea class="input" id="wiki-edit-content" rows="16" style="font-family:'SF Mono',Monaco,monospace;font-size:12px;resize:vertical;width:100%;min-height:300px" placeholder="Markdown 内容...">${esc(content)}</textarea>
    </div>
  </div>
  <div class="modal-actions"><button class="btn btn-secondary" onclick="closeModal()">取消</button><button class="btn btn-primary" onclick="saveWikiPage('${esc(path)}')">保存</button></div>`);
}

// Markdown 编辑器辅助函数
function mdWrap(before,after){
  const ta=document.getElementById('wiki-edit-content');if(!ta)return;
  const s=ta.selectionStart,e=ta.selectionEnd;
  const sel=ta.value.substring(s,e)||'文本';
  ta.value=ta.value.substring(0,s)+before+sel+after+ta.value.substring(e);
  ta.selectionStart=s+before.length;ta.selectionEnd=e+before.length;ta.focus();
}
function mdPrefix(prefix){
  const ta=document.getElementById('wiki-edit-content');if(!ta)return;
  const s=ta.selectionStart;
  const lineStart=ta.value.lastIndexOf('\\n',s-1)+1;
  ta.value=ta.value.substring(0,lineStart)+prefix+ta.value.substring(lineStart);
  ta.selectionStart=ta.selectionEnd=s+prefix.length;ta.focus();
}
function mdInsert(text){
  const ta=document.getElementById('wiki-edit-content');if(!ta)return;
  const s=ta.selectionStart;
  ta.value=ta.value.substring(0,s)+text+ta.value.substring(ta.selectionEnd);
  ta.selectionStart=ta.selectionEnd=s+text.length;ta.focus();
}

// 保存编辑后的 Wiki 页面
async function saveWikiPage(path){
  const w=state.data.wikiData;
  const title=val('wiki-edit-title');
  const content=document.getElementById('wiki-edit-content')?.value||'';
  const tagsStr=val('wiki-edit-tags')||'';
  const tags=tagsStr.split(',').map(t=>t.trim()).filter(Boolean);
  if(!title){toast('请输入标题','error');return}
  const body={content,title};
  if(tags.length)body.tags=tags;
  const r=await wikiFetch('/projects/'+w.currentProjectId+'/pages/'+encodeURIComponent(path),{method:'PUT',body:JSON.stringify(body)});
  if(r.__error){toast(r.msg||'保存失败','error');return}
  toast('页面已保存','success');closeModal();
  // 更新本地数据
  const pg=(w.pages||[]).find(p=>(p.path||p.slug)===path);
  if(pg){pg.title=title;pg.content=content;pg.tags=tags;}
  updatePage('wiki');
}
async function loadWikiGraph(projectId){
  if(!projectId)return;
  const r=await wikiFetch('/projects/'+projectId+'/graph');
  if(r.__error){
    state.data.wikiData.graphData=null;
    toast('图谱加载失败: '+(r.msg||''),'error');
  }else{
    state.data.wikiData.graphData=r;
  }
  if(state.activeTab==='wiki'&&state.data.wikiData.currentTab==='graph'){
    updatePage('wiki');
    // 启动 Canvas 力导向动画
    if(r&&!r.__error&&r.nodes&&r.nodes.length)initWikiGraphCanvas(r);
  }
}

// 重建图谱
async function rebuildWikiGraph(projectId){
  if(!projectId)return;
  toast('正在重建图谱...','info');
  const r=await wikiFetch('/projects/'+projectId+'/graph/rebuild',{method:'POST'});
  if(r.__error){
    toast('图谱重建失败: '+(r.msg||''),'error');
  }else{
    toast('图谱重建完成','success');
    await loadWikiGraph(projectId);
  }
}

// 原生 fetch 调用（Wiki 接口不包 Response）
async function wikiFetch(path,opts={}){
  try{
    const r=await fetch(API_BASE+'/api/v1/wiki'+path,{headers:{'Content-Type':'application/json'},...opts});
    if(!r.ok){
      let msg='请求失败 ('+r.status+')';
      try{const j=await r.json();if(j.message||j.error)msg=j.message||j.error}catch(e){}
      return{__error:true,msg};
    }
    return await r.json();
  }catch(e){
    return{__error:true,msg:'网络错误：'+e.message};
  }
}

async function loadWikiProjects(){
  const r=await wikiFetch('/projects');
  if(r.__error){toast(r.msg,'error');return}
  const w=state.data.wikiData;
  w.projects=Array.isArray(r)?r:[];
  updatePage('wiki');
}

async function selectWikiProject(id){
  const w=state.data.wikiData;
  if(w._graphAnim){cancelAnimationFrame(w._graphAnim);w._graphAnim=null;}
  w.currentProjectId=id;
  w.searchResults=null;
  await Promise.all([loadWikiPages(id),loadWikiConv(id)]);
  if(w.currentTab==='sources')await loadWikiSources(id);
  if(w.currentTab==='tags')await loadWikiTags(id);
  if(w.currentTab==='graph')loadWikiGraph(id);
  updatePage('wiki');
}

async function loadWikiPages(projectId){
  const r=await wikiFetch('/projects/'+projectId+'/pages');
  if(r.__error)return;
  state.data.wikiData.pages=Array.isArray(r)?r:[];
  if(state.activeTab==='wiki'&&state.data.wikiData.currentTab==='pages')updateWikiContent();
}

async function loadWikiSources(projectId){
  const r=await wikiFetch('/projects/'+projectId+'/sources');
  if(r.__error)return;
  state.data.wikiData.sources=Array.isArray(r)?r:[];
  if(state.activeTab==='wiki'&&state.data.wikiData.currentTab==='sources')updateWikiContent();
}

async function loadWikiTags(projectId){
  const r=await wikiFetch('/projects/'+projectId+'/tags');
  if(r.__error)return;
  state.data.wikiData.tags=Array.isArray(r)?r:[];
  if(state.activeTab==='wiki'&&state.data.wikiData.currentTab==='tags')updateWikiContent();
}

async function loadWikiConv(projectId){
  const r=await wikiFetch('/projects/'+projectId+'/conversations');
  if(r.__error)return;
  state.data.wikiData.conv=Array.isArray(r)?r:[];
  if(state.activeTab==='wiki'&&state.data.wikiData.currentTab==='chat')updateWikiChatMessages();
}

function updateWikiContent(){
  if(state.activeTab!=='wiki')return;
  const el=document.getElementById('wiki-main-content');
  if(el)el.innerHTML=renderWikiContent();
}

function updateWikiChatMessages(){
  const container=document.querySelector('.page-container[data-page="wiki"]');
  if(!container)return;
  const el=container.querySelector('.kb-chat-messages');
  if(!el)return;
  const convs=state.data.wikiData.conv||[];
  if(!convs.length){
    el.innerHTML=`<div style="display:flex;align-items:center;justify-content:center;height:100%"><div style="text-align:center;color:var(--muted-foreground)">
      <div style="font-size:32px;margin-bottom:8px;opacity:.3">💬</div>
      <div style="font-size:14px;font-weight:500;margin-bottom:4px">开始 Wiki 问答</div>
      <div style="font-size:13px">在下方输入问题，AI 将从 Wiki 检索相关内容并回答</div>
    </div></div>`;
    return;
  }
  el.innerHTML=renderWikiChatMessages(convs);
  scrollWikiChatToBottom();
}

function renderWikiChatMessages(convs){
  return convs.map(c=>{
    if(c.role==='user')return`<div class="chat-msg user"><div class="chat-avatar">U</div><div class="chat-bubble">${esc(c.content||c.question||'')}</div></div>`;
    if(c.content==='__typing__')return`<div class="chat-msg asst"><div class="chat-avatar">AI</div><div class="chat-bubble"><div class="typing-indicator"><span class="typing-dot"></span><span class="typing-dot"></span><span class="typing-dot"></span></div></div></div>`;
    if(c.content==='__error__'){
      return`<div class="chat-msg asst"><div class="chat-avatar">AI</div><div class="chat-bubble error" style="max-width:520px">
        <div style="font-weight:600;margin-bottom:6px">⚠ 回答生成失败</div>
        <div style="font-size:13px;line-height:1.6">${esc(c.error||'未知错误')}</div>
        <button class="btn btn-primary btn-sm" style="margin-top:12px" onclick="retryWikiAsk(${JSON.stringify(c.retryQ||'').replace(/"/g,'&quot;')})">${I.refresh} 重试</button>
      </div></div>`;
    }
    let wikiMsgText=c.content||c.answer||'';
    let html=`<div class="chat-msg asst"><div class="chat-avatar">AI</div><div class="chat-bubble md-content">${renderMd(wikiMsgText)}<div class="chat-msg-actions"><button class="btn btn-ghost btn-sm" onclick="copyText(${JSON.stringify(wikiMsgText).replace(/</g,'\u003c').replace(/>/g,'\u003e')})" title="复制回答">📋 复制</button></div></div></div>`;
    if(c.sources&&c.sources.length){
      html+=`<div class="chat-sources">
        <div class="chat-sources-title">📚 引用来源 (${c.sources.length})</div>
        ${c.sources.slice(0,5).map(s=>`<div class="chat-source-item"><span class="chat-source-name">${esc(s.title||s.filename||s.source||'')} ${s.content?'· '+esc(s.content.substring(0,80))+(s.content.length>80?'...':''):''}</span><span class="chat-source-score">${(s.score||0).toFixed(3)}</span></div>`).join('')}
        ${c.sources.length>5?`<div style="color:var(--muted-foreground);font-size:11px;margin-top:4px;text-align:center">还有 ${c.sources.length-5} 个来源未展示</div>`:''}
      </div>`;
    }
    return html;
  }).join('');
}

function scrollWikiChatToBottom(){
  requestAnimationFrame(()=>{
    requestAnimationFrame(()=>{
      const panel=document.getElementById('wiki-main-content');
      const msgs=document.querySelector('.page-container[data-page="wiki"] .kb-chat-messages');
      if(panel)panel.scrollTop=panel.scrollHeight;
      if(msgs)msgs.scrollTop=msgs.scrollHeight;
      setTimeout(()=>{if(panel)panel.scrollTop=panel.scrollHeight},100);
    });
  });
}

async function wikiAsk(){
  const inputEl=document.getElementById('wikiAskInput');
  const q=inputEl?inputEl.value.trim():'';
  if(!q)return;
  inputEl.value='';
  inputEl.style.height='auto';
  const w=state.data.wikiData;
  w.conv=[...w.conv,{role:'user',content:q},{role:'assistant',content:'__typing__'}];
  updateWikiChatMessages();
  scrollWikiChatToBottom();
  if(inputEl)inputEl.focus();
  const r=await wikiFetch('/projects/'+w.currentProjectId+'/ask',{method:'POST',body:JSON.stringify({question:q})});
  w.conv=w.conv.filter(c=>c.content!=='__typing__');
  if(r.__error){
    w.conv=[...w.conv,{role:'assistant',content:'__error__',error:r.msg||'回答生成失败',retryQ:q}];
  }else{
    w.conv=[...w.conv,{role:'assistant',content:r.answer||r.content||'无回答',sources:r.sources||[]}];
  }
  updateWikiChatMessages();
  scrollWikiChatToBottom();
}

async function retryWikiAsk(q){
  if(!q)return;
  const w=state.data.wikiData;
  const convs=w.conv||[];
  for(let i=convs.length-1;i>=0;i--){
    if(convs[i].content==='__error__'){convs[i].content='__typing__';break}
  }
  w.conv=[...convs];
  updateWikiChatMessages();scrollWikiChatToBottom();
  const r=await wikiFetch('/projects/'+w.currentProjectId+'/ask',{method:'POST',body:JSON.stringify({question:q})});
  const arr=w.conv.filter(c=>c.content!=='__typing__');
  if(r.__error){
    w.conv=[...arr,{role:'assistant',content:'__error__',error:r.msg||'回答生成失败',retryQ:q}];
  }else{
    w.conv=[...arr,{role:'assistant',content:r.answer||r.content||'无回答',sources:r.sources||[]}];
  }
  updateWikiChatMessages();scrollWikiChatToBottom();
}

async function wikiSearch(){
  const inputEl=document.getElementById('wikiSearchInput');
  const q=inputEl?inputEl.value.trim():'';
  if(!q)return;
  inputEl.value='';
  const w=state.data.wikiData;
  w._lastSearchQuery=q;
  w._searching=true;
  w.searchResults=null;
  updateWikiContent();
  const r=await wikiFetch('/projects/'+w.currentProjectId+'/search?q='+encodeURIComponent(q)+'&limit=10');
  w._searching=false;
  if(r.__error){toast(r.msg||'搜索失败','error');updateWikiContent();return}
  w.searchResults=Array.isArray(r)?r:[];
  if(!w.searchResults.length)toast('无搜索结果','info');
  updateWikiContent();
}

function showWikiProjectModal(){
  showModal(`<div class="modal-header"><div class="modal-title">创建 Wiki 项目</div></div>
  <div class="modal-body">
  <div class="input-group"><label class="input-label">名称</label><input class="input" id="wiki-proj-name" placeholder="如：项目文档Wiki"></div>
  <div class="input-group"><label class="input-label">描述</label><input class="input" id="wiki-proj-desc" placeholder="项目描述"></div>
  </div>
  <div class="modal-actions"><button class="btn btn-secondary" onclick="closeModal()">取消</button><button class="btn btn-primary" onclick="addWikiProject()">创建</button></div>`);
}

async function addWikiProject(){
  const body={name:val('wiki-proj-name'),description:val('wiki-proj-desc')};
  if(!body.name){toast('请输入项目名称','error');return}
  const r=await wikiFetch('/projects',{method:'POST',body:JSON.stringify(body)});
  if(r.__error){toast(r.msg||'创建失败','error');return}
  toast('Wiki 项目创建成功');closeModal();loadWikiProjects();
}

async function delWikiProject(id){
  const r=await wikiFetch('/projects/'+id,{method:'DELETE'});
  if(r.__error){toast(r.msg||'删除失败','error');return}
  toast('项目已删除');
  const w=state.data.wikiData;
  w.currentProjectId=null;w.pages=[];w.sources=[];w.tags=[];w.conv=[];w.searchResults=null;
  loadWikiProjects();
}

function showWikiSourceModal(){
  const w=state.data.wikiData;
  if(!w.currentProjectId){toast('请先选择 Wiki 项目','error');return}
  showModal(`<div class="modal-header"><div class="modal-title">添加来源</div></div>
  <div class="modal-body">
  <div class="input-group"><label class="input-label">来源类型</label><select class="input" id="wiki-src-type" onchange="toggleWikiSrcFields()">
    <option value="upload">文件上传</option><option value="text">文本粘贴</option><option value="git">Git 仓库</option><option value="url">URL 抓取</option><option value="dir">本地目录</option>
  </select></div>
  <div id="wiki-src-upload-fields">
    <div class="input-group"><label class="input-label">选择文件</label><input type="file" id="wiki-src-file" accept=".md,.txt,.markdown,.json,.yaml,.yml,.csv,.html" style="width:100%;padding:8px;border:1px solid var(--border);border-radius:6px;background:var(--card)">
    <div style="font-size:11px;color:var(--muted-foreground);margin-top:4px">支持 .md .txt .json .yaml .csv .html 等文本文件</div>
    </div>
  </div>
  <div id="wiki-src-text-fields" style="display:none">
    <div class="input-group"><label class="input-label">文件名</label><input class="input" id="wiki-src-text-name" placeholder="my-doc.md"></div>
    <div class="input-group"><label class="input-label">内容</label><textarea class="input" id="wiki-src-text-content" rows="10" placeholder="粘贴 Markdown 或文本内容..." style="font-family:'SF Mono',Monaco,monospace;font-size:12px;resize:vertical"></textarea></div>
  </div>
  <div id="wiki-src-git-fields" style="display:none">
    <div class="input-group"><label class="input-label">仓库 URL</label><input class="input" id="wiki-src-repo-url" placeholder="https://github.com/user/repo.git"></div>
    <div style="display:grid;grid-template-columns:1fr 1fr;gap:12px">
      <div class="input-group"><label class="input-label">分支</label><input class="input" id="wiki-src-branch" value="main"></div>
      <div class="input-group"><label class="input-label">Token (可选)</label><input class="input" type="password" id="wiki-src-token" placeholder="私有仓库需要"></div>
    </div>
  </div>
  <div id="wiki-src-url-fields" style="display:none">
    <div class="input-group"><label class="input-label">URL</label><input class="input" id="wiki-src-url" placeholder="https://example.com/docs"></div>
  </div>
  <div id="wiki-src-dir-fields" style="display:none">
    <div class="input-group"><label class="input-label">目录路径</label><input class="input" id="wiki-src-dir" placeholder="/path/to/docs"></div>
  </div>
  <div class="input-group"><label class="input-label">名称 (可选)</label><input class="input" id="wiki-src-name" placeholder="来源名称"></div>
  </div>
  <div class="modal-actions"><button class="btn btn-secondary" onclick="closeModal()">取消</button><button class="btn btn-primary" onclick="addWikiSource()">添加</button></div>`);
}

function toggleWikiSrcFields(){
  const t=val('wiki-src-type');
  const fields=['upload','text','git','url','dir'];
  fields.forEach(f=>{const el=document.getElementById('wiki-src-'+f+'-fields');if(el)el.style.display=t===f?'':'none'});
}

async function addWikiSource(projectId){
  const w=state.data.wikiData;
  const pid=projectId||w.currentProjectId;
  if(!pid){toast('请先选择 Wiki 项目','error');return}
  const type=val('wiki-src-type');
  if(type==='upload'){
    const fileInput=document.getElementById('wiki-src-file');
    if(!fileInput||!fileInput.files||!fileInput.files[0]){toast('请选择文件','error');return}
    const file=fileInput.files[0];
    const formData=new FormData();
    formData.append('file',file);
    try{
      const res=await fetch(API_BASE+'/api/v1/wiki/projects/'+pid+'/upload',{method:'POST',body:formData});
      const r=await res.json();
      if(r.sourceId){
        toast('文件已上传，正在摄入...');
        closeModal();
        await loadWikiSources(pid);
        // 自动触发 ingest
        await wikiFetch('/projects/'+pid+'/sources/'+r.sourceId+'/ingest',{method:'POST'});
        await loadWikiSources(pid);
        startWikiSourcePolling();
      }else{toast(r.message||r.error||'上传失败','error')}
    }catch(e){toast('上传失败: '+e.message,'error')}
    return;
  }
  if(type==='text'){
    const name=val('wiki-src-text-name')||'pasted-text.md';
    const content=val('wiki-src-text-content');
    if(!content||!content.trim()){toast('内容不能为空','error');return}
    const body={sourceType:'text',filename:name,content:content};
    const r=await wikiFetch('/projects/'+pid+'/sources',{method:'POST',body:JSON.stringify(body)});
    if(r.__error){toast(r.msg||'添加失败','error');return}
    toast('文本已添加，正在摄入...');
    closeModal();await loadWikiSources(pid);
    await wikiFetch('/projects/'+pid+'/sources/'+r.sourceId+'/ingest',{method:'POST'});
    await loadWikiSources(pid);startWikiSourcePolling();
    return;
  }
  const body={sourceType:type==='dir'?'local_dir':type,name:val('wiki-src-name')};
  if(type==='git'){body.repoUrl=val('wiki-src-repo-url');body.branch=val('wiki-src-branch')||'main';body.token=val('wiki-src-token')}
  else if(type==='url'){body.url=val('wiki-src-url')}
  else if(type==='dir'){body.dirPath=val('wiki-src-dir')}
  const r=await wikiFetch('/projects/'+pid+'/sources',{method:'POST',body:JSON.stringify(body)});
  if(r.__error){toast(r.msg||'添加失败','error');return}
  toast('来源已添加');closeModal();await loadWikiSources(pid);startWikiSourcePolling();
}

async function ingestWikiSource(projectId,sourceId){
  toast('正在摄入来源...','info');
  const r=await wikiFetch('/projects/'+projectId+'/sources/'+sourceId+'/ingest',{method:'POST'});
  if(r.__error){toast(r.msg||'摄入失败','error');return}
  toast('摄入任务已触发');await loadWikiSources(projectId);startWikiSourcePolling();
}

function startWikiSourcePolling(){
  if(wikiSourcePollTimer)clearInterval(wikiSourcePollTimer);
  wikiSourcePollTimer=setInterval(async()=>{
    const w=state.data.wikiData;
    if(!w.currentProjectId){clearInterval(wikiSourcePollTimer);wikiSourcePollTimer=null;return}
    await loadWikiSources(w.currentProjectId);
    const hasProcessing=(w.sources||[]).some(s=>s.status==='processing'||s.status==='ingesting'||s.status==='pending');
    if(!hasProcessing){
      clearInterval(wikiSourcePollTimer);wikiSourcePollTimer=null;
      // 摄入完成后重新加载页面和标签
      loadWikiPages(w.currentProjectId);
      loadWikiTags(w.currentProjectId);
    }
    if(state.activeTab==='wiki'&&w.currentTab==='sources')updateWikiContent();
  },3000);
}

async function delWikiSource(projectId,sourceId){
  const r=await wikiFetch('/projects/'+projectId+'/sources/'+sourceId,{method:'DELETE'});
  if(r.__error){toast(r.msg||'删除失败','error');return}
  toast('来源已删除');await loadWikiSources(projectId);
}

async function clearWikiConv(projectId){
  const r=await wikiFetch('/projects/'+projectId+'/conversations',{method:'DELETE'});
  if(r.__error){toast(r.msg||'清空失败','error');return}
  toast('对话已清空');state.data.wikiData.conv=[];updateWikiChatMessages();
}

// ==================== Wiki 图谱 Canvas 力导向动画 ====================
function initWikiGraphCanvas(gData){
  const w=state.data.wikiData;
  if(w._graphAnim){cancelAnimationFrame(w._graphAnim);w._graphAnim=null;}
  const canvas=document.getElementById('wikiGraphCanvas');
  if(!canvas)return;
  const ctx=canvas.getContext('2d');
  const tooltip=document.getElementById('wikiGraphTooltip');
  const dpr=window.devicePixelRatio||1;

  function resize(){
    const rect=canvas.parentElement.getBoundingClientRect();
    canvas.width=rect.width*dpr;
    canvas.height=rect.height*dpr;
    canvas.style.width=rect.width+'px';
    canvas.style.height=rect.height+'px';
    ctx.setTransform(dpr,0,0,dpr,0,0);
  }
  resize();

  const nodes=(gData.nodes||[]).map(n=>({
    id:n.id||n.path||'',
    label:n.label||n.id||n.path||'',
    type:n.nodeType||'note',
    linkCount:n.linkCount||0,
    x:0,y:0,vx:0,vy:0,
    radius:0,
    color:'#3b82f6',
  }));
  const nodeMap={};nodes.forEach(n=>nodeMap[n.id]=n);
  const edges=(gData.edges||[]).map(e=>({
    source:nodeMap[e.source||e.from],
    target:nodeMap[e.target||e.to],
    type:e.edgeType||e.type||'unknown',
    weight:e.weight||1,
  })).filter(e=>e.source&&e.target);

  // 节点类型/边类型颜色
  const typeColors={'wikilink':'#3b82f6','tag_share':'#10b981','same_type':'#f59e0b','reference':'#8b5cf6','unknown':'#6b7280'};
  const nodeTypeColors={'note':'#3b82f6','doc':'#10b981','guide':'#f59e0b','api':'#8b5cf6','concept':'#ec4899','default':'#6b7280'};
  // 按边类型为节点着色
  const nodeEdgeTypes={};
  edges.forEach(e=>{
    const st=nodeEdgeTypes[e.source.id]||{};
    st[e.type]=(st[e.type]||0)+1;
    nodeEdgeTypes[e.source.id]=st;
    const tt=nodeEdgeTypes[e.target.id]||{};
    tt[e.type]=(tt[e.type]||0)+1;
    nodeEdgeTypes[e.target.id]=tt;
  });
  nodes.forEach(n=>{
    const ets=nodeEdgeTypes[n.id];
    if(ets){const top=Object.entries(ets).sort((a,b)=>b[1]-a[1])[0];n.color=typeColors[top[0]]||nodeTypeColors['default'];}
    else n.color=nodeTypeColors[n.type]||nodeTypeColors['default'];
  });

  // 初始圆形布局 — 加大初始半径，让节点初始就更分散
  const cx=canvas.width/(2*dpr),cy=canvas.height/(2*dpr);
  const R=Math.max(Math.min(cx,cy)*0.85,200);
  nodes.forEach((n,i)=>{
    const a=(i/nodes.length)*Math.PI*2;
    n.x=cx+Math.cos(a)*R+(Math.random()-0.5)*40;
    n.y=cy+Math.sin(a)*R+(Math.random()-0.5)*40;
    // 给初始速度，帮助展开
    n.vx=(Math.random()-0.5)*3;
    n.vy=(Math.random()-0.5)*3;
  });

  // 节点半径：基于连接数
  const maxLinks=Math.max(...nodes.map(n=>n.linkCount),1);
  nodes.forEach(n=>{n.radius=6+((n.linkCount/maxLinks)*16);});

  // 交互状态
  let hoverNode=null,dragNode=null,mouseX=0,mouseY=0;
  let isMouseDown=false,dragOffX=0,dragOffY=0;
  // 缩放与平移
  let scale=1,panX=0,panY=0;
  let isPanning=false,panStartX=0,panStartY=0,panStartPanX=0,panStartPanY=0;

  // 坐标转换：屏幕坐标 → 逻辑坐标
  function screenToWorld(sx,sy){
    return {x:(sx-panX)/scale, y:(sy-panY)/scale};
  }
  // 坐标转换：逻辑坐标 → 屏幕坐标
  function worldToScreen(wx,wy){
    return {x:wx*scale+panX, y:wy*scale+panY};
  }

  function getNodeAt(x,y){
    // x,y 已经是逻辑坐标，节点半径也在逻辑坐标系
    for(let i=nodes.length-1;i>=0;i--){
      const n=nodes[i];
      const dx=x-n.x,dy=y-n.y;
      if(dx*dx+dy*dy<=n.radius*n.radius)return n;
    }
    return null;
  }

  // 缩放（以鼠标位置为中心）
  function applyZoom(deltaScale,cxScreen,cyScreen){
    const oldScale=scale;
    scale=Math.max(0.2,Math.min(5,scale*deltaScale));
    if(scale===oldScale)return;
    // 缩放后保持鼠标点对应的世界坐标不变
    const wx=(cxScreen-panX)/oldScale;
    const wy=(cyScreen-panY)/oldScale;
    panX=cxScreen-wx*scale;
    panY=cyScreen-wy*scale;
  }

  canvas.onmousemove=function(e){
    const rect=canvas.getBoundingClientRect();
    mouseX=e.clientX-rect.left;
    mouseY=e.clientY-rect.top;
    // 平移拖拽
    if(isPanning){
      panX=panStartPanX+(mouseX-panStartX);
      panY=panStartPanY+(mouseY-panStartY);
      return;
    }
    const w2=screenToWorld(mouseX,mouseY);
    if(dragNode){
      dragNode.x=w2.x-dragOffX;
      dragNode.y=w2.y-dragOffY;
      dragNode.vx=0;dragNode.vy=0;
      return;
    }
    hoverNode=getNodeAt(w2.x,w2.y);
    canvas.style.cursor=hoverNode?'pointer':(isPanning?'grabbing':'grab');
    if(hoverNode){
      tooltip.innerHTML=`<div class="kb-graph-tip-title">${esc(hoverNode.label)}</div>
        <div class="kb-graph-tip-meta">${hoverNode.type} · ${hoverNode.linkCount} 连接</div>`;
      tooltip.style.display='block';
      const tr=canvas.getBoundingClientRect();
      let tx=mouseX+12,ty=mouseY+12;
      if(tx+200>tr.width)tx=mouseX-200-12;
      tooltip.style.left=tx+'px';
      tooltip.style.top=ty+'px';
    }else{
      tooltip.style.display='none';
    }
  };

  canvas.onmousedown=function(e){
    const rect=canvas.getBoundingClientRect();
    const sx=e.clientX-rect.left,sy=e.clientY-rect.top;
    const w2=screenToWorld(sx,sy);
    const n=getNodeAt(w2.x,w2.y);
    if(n){
      dragNode=n;dragOffX=w2.x-n.x;dragOffY=w2.y-n.y;isMouseDown=true;canvas.style.cursor='grabbing';
    }else{
      // 点击空白区域 → 平移
      isPanning=true;panStartX=sx;panStartY=sy;panStartPanX=panX;panStartPanY=panY;
      canvas.style.cursor='grabbing';
    }
  };
  canvas.onmouseup=function(){
    if(dragNode&&!isMouseDown)return;
    if(dragNode&&hoverNode&&dragNode!==hoverNode){
      // 双击查看页面
    }
    dragNode=null;isMouseDown=false;isPanning=false;
    canvas.style.cursor=hoverNode?'pointer':'default';
  };
  canvas.onmouseleave=function(){dragNode=null;isMouseDown=false;isPanning=false;hoverNode=null;tooltip.style.display='none';canvas.style.cursor='default';};

  // 滚轮缩放
  canvas.onwheel=function(e){
    e.preventDefault();
    const rect=canvas.getBoundingClientRect();
    const sx=e.clientX-rect.left,sy=e.clientY-rect.top;
    const delta=e.deltaY<0?1.1:0.9;
    applyZoom(delta,sx,sy);
  };

  canvas.ondblclick=function(e){
    const rect=canvas.getBoundingClientRect();
    const sx=e.clientX-rect.left,sy=e.clientY-rect.top;
    const w2=screenToWorld(sx,sy);
    const n=getNodeAt(w2.x,w2.y);
    if(n&&w.currentProjectId){
      if(typeof viewWikiPage==='function')viewWikiPage(n.id);
    }
  };

  // 力导向模拟参数 — 增大排斥力、减小吸引力、加长弹簧理想距离，让节点充分展开
  const REPULSION=3000;
  const ATTRACTION=0.015;
  const CENTER_FORCE=0.0015;
  const DAMPING=0.88;
  const MAX_SPEED=15;
  const edgeColorMap={'wikilink':'rgba(59,130,246,0.35)','tag_share':'rgba(16,185,129,0.3)','same_type':'rgba(245,158,11,0.25)','reference':'rgba(139,92,246,0.3)','unknown':'rgba(107,114,128,0.2)'};

  let frame=0;
  function animate(){
    frame++;
    const W=canvas.width/dpr,H=canvas.height/dpr;
    // 排斥力
    for(let i=0;i<nodes.length;i++){
      for(let j=i+1;j<nodes.length;j++){
        const a=nodes[i],b=nodes[j];
        let dx=b.x-a.x,dy=b.y-a.y;
        let dist=Math.sqrt(dx*dx+dy*dy)||1;
        const force=REPULSION/(dist*dist);
        const fx=(dx/dist)*force,fy=(dy/dist)*force;
        a.vx-=fx;a.vy-=fy;
        b.vx+=fx;b.vy+=fy;
      }
    }
    // 弹簧吸引力
    edges.forEach(e=>{
      const dx=e.target.x-e.source.x,dy=e.target.y-e.source.y;
      const dist=Math.sqrt(dx*dx+dy*dy)||1;
      const force=ATTRACTION*(dist-160)*e.weight;
      const fx=(dx/dist)*force,fy=(dy/dist)*force;
      e.source.vx+=fx;e.source.vy+=fy;
      e.target.vx-=fx;e.target.vy-=fy;
    });
    // 中心引力 + 速度更新
    nodes.forEach(n=>{
      if(n===dragNode)return;
      n.vx+=(cx-n.x)*CENTER_FORCE;
      n.vy+=(cy-n.y)*CENTER_FORCE;
      n.vx*=DAMPING;n.vy*=DAMPING;
      const sp=Math.sqrt(n.vx*n.vx+n.vy*n.vy);
      if(sp>MAX_SPEED){n.vx=(n.vx/sp)*MAX_SPEED;n.vy=(n.vy/sp)*MAX_SPEED;}
      n.x+=n.vx;n.y+=n.vy;
      // 边界
      n.x=Math.max(n.radius+4,Math.min(W-n.radius-4,n.x));
      n.y=Math.max(n.radius+4,Math.min(H-n.radius-4,n.y));
    });

    // 渲染
    ctx.clearRect(0,0,W,H);

    // 应用缩放变换
    ctx.save();
    ctx.translate(panX,panY);
    ctx.scale(scale,scale);

    // 找 hover 邻居
    const hoverNeighbors=new Set();
    if(hoverNode){
      hoverNeighbors.add(hoverNode.id);
      edges.forEach(ed=>{
        if(ed.source===hoverNode)hoverNeighbors.add(ed.target.id);
        if(ed.target===hoverNode)hoverNeighbors.add(ed.source.id);
      });
    }

    // 画边
    edges.forEach(e=>{
      const isHoverEdge=hoverNode&&(e.source===hoverNode||e.target===hoverNode);
      const baseColor=edgeColorMap[e.type]||edgeColorMap['unknown'];
      ctx.strokeStyle=isHoverEdge?baseColor.replace(/[\d.]+\)$/,'0.9)'):baseColor;
      ctx.lineWidth=(isHoverEdge?2.2:1.2)/scale;
      ctx.beginPath();
      ctx.moveTo(e.source.x,e.source.y);
      ctx.lineTo(e.target.x,e.target.y);
      ctx.stroke();
      // hover 时在边中间显示类型
      if(isHoverEdge){
        const mx=(e.source.x+e.target.x)/2,my=(e.source.y+e.target.y)/2;
        ctx.fillStyle='var(--foreground)';
        ctx.font='10px system-ui';
        ctx.textAlign='center';
        ctx.fillText(e.type,mx,my-4/scale);
      }
    });

    // 画节点
    nodes.forEach(n=>{
      const isHover=hoverNode===n;
      const isNeighbor=hoverNode&&hoverNeighbors.has(n.id)&&!isHover;
      const alpha=hoverNode?(!hoverNeighbors.has(n.id)?0.2:1):1;
      // 外发光
      if(isHover){
        ctx.beginPath();
        ctx.arc(n.x,n.y,n.radius+6,0,Math.PI*2);
        ctx.fillStyle=n.color+'20';
        ctx.fill();
      }
      ctx.beginPath();
      ctx.arc(n.x,n.y,n.radius,0,Math.PI*2);
      ctx.globalAlpha=alpha;
      ctx.fillStyle=n.color;
      ctx.fill();
      // 边框
      ctx.strokeStyle=isHover?'#fff':isNeighbor?n.color:'rgba(255,255,255,0.3)';
      ctx.lineWidth=(isHover?2.5:1)/scale;
      ctx.stroke();
      ctx.globalAlpha=1;
      // 标签
      if(n.radius>10||isHover||isNeighbor){
        ctx.globalAlpha=hoverNode?alpha:0.85;
        ctx.fillStyle='var(--foreground)';
        ctx.font=(isHover?'bold ':'')+'11px system-ui';
        ctx.textAlign='center';
        const lbl=n.label.length>14?n.label.substring(0,13)+'…':n.label;
        ctx.fillText(lbl,n.x,n.y+n.radius+14);
        ctx.globalAlpha=1;
      }
    });

    ctx.restore(); // 恢复变换

    w._graphAnim=requestAnimationFrame(animate);
  }
  animate();

  // 暴露缩放/复原函数供按钮调用
  w._graphZoom=applyZoom;
  w._graphResetView=function(){scale=1;panX=0;panY=0;};

  // resize 监听
  const resizeObserver=new ResizeObserver(()=>{resize();cx=canvas.width/(2*dpr);cy=canvas.height/(2*dpr);});
  resizeObserver.observe(canvas.parentElement);
  w._graphResizeObserver=resizeObserver;
}

function resetGraphLayout(){
  const w=state.data.wikiData;
  if(w.graphData&&w.currentTab==='graph'){
    w.graphData._resetFlag=Date.now();
    initWikiGraphCanvas(w.graphData);
  }
}

// 图谱缩放控制（按钮触发）
function wikiGraphZoom(factor){
  const canvas=document.getElementById('wikiGraphCanvas');
  if(!canvas)return;
  const rect=canvas.getBoundingClientRect();
  const cx=rect.width/2,cy=rect.height/2;
  // 通过自定义事件触发缩放，因为 applyZoom 在闭包内
  const w=state.data.wikiData;
  if(w._graphZoom){w._graphZoom(factor,cx,cy);}
}

// 图谱复原（重置缩放和平移）
function wikiGraphResetView(){
  const w=state.data.wikiData;
  if(w._graphResetView){w._graphResetView();}
}
