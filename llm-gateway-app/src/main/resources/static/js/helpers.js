function val(id){const el=document.getElementById(id);return el?el.value:''}
function esc(s){if(s==null)return'';return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;').replace(/'/g,'&#39;')}
function fmtDate(d,full){if(!d)return'-';try{const dt=new Date(d);if(full)return dt.toLocaleString('zh-CN');return dt.toLocaleString('zh-CN',{month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit'})}catch{return d}}
function fmtTime(d){if(!d)return'--';try{return new Date(d).toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit',second:'2-digit'})}catch{return'--'}}
function fmtNum(n){if(n==null)return 0;if(n>=1e9)return(n/1e9).toFixed(1)+'B';if(n>=1e6)return(n/1e6).toFixed(1)+'M';if(n>=1e3)return(n/1e3).toFixed(1)+'K';return n}
function fmtBytes(n){if(!n)return'0 B';const u=['B','KB','MB','GB'];let i=0;while(n>=1024&&i<u.length-1){n/=1024;i++}return n.toFixed(n<10&&i>0?1:0)+' '+u[i]}
function relTime(d){if(!d)return'-';const now=Date.now();const t=new Date(d).getTime();const s=Math.floor((now-t)/1000);if(s<60)return s+'秒前';if(s<3600)return Math.floor(s/60)+'分钟前';if(s<86400)return Math.floor(s/3600)+'小时前';return Math.floor(s/86400)+'天前'}
function emptyState(title,desc,action){return`<div class="empty">${I.empty}<div class="empty-title">${esc(title)}</div><div class="empty-desc">${esc(desc||'')}</div>${action||''}</div>`}
function emptyRow(cols,title,desc){return`<tr><td colspan="${cols}">${emptyState(title,desc)}</td></tr>`}
function randomKey(){return Array.from({length:32},()=>'abcdefghijklmnopqrstuvwxyz0123456789'[Math.floor(Math.random()*36)]).join('')}
function formatJSON(str){try{return JSON.stringify(JSON.parse(str),null,2)}catch(e){return str}}
function copyText(text){navigator.clipboard?.writeText(text).then(()=>toast('已复制到剪贴板')).catch(()=>toast('复制失败','error'))}
function riskBadge(r){
  if(!r||r==='Clean'||r==='LOW'||r==='Low')return'<span class="badge badge-green">Clean</span>';
  if(r==='MEDIUM'||r==='Medium')return'<span class="badge badge-amber">'+esc(r)+'</span>';
  if(r==='High'||r==='HIGH'||r==='Critical'||r==='CRITICAL')return'<span class="badge badge-red">'+esc(r)+'</span>';
  return'<span class="badge badge-slate">'+esc(r||'Clean')+'</span>';
}
function securityActionBadge(a){
  if(!a||a==='Allow')return'<span class="badge badge-green">Allow</span>';
  if(a==='Sanitize')return'<span class="badge badge-amber">Sanitize</span>';
  if(a==='Block')return'<span class="badge badge-red">Block</span>';
  return'<span class="badge badge-slate">'+esc(a)+'</span>';
}
function docStatusBadge(s){
  if(s==='ready')return'badge-green';
  if(s==='error')return'badge-red';
  if(s==='processing'||s==='building')return'badge-amber';
  return'badge-slate';
}
function statusBadge(on){return on?'<span class="badge badge-green">启用</span>':'<span class="badge badge-slate">禁用</span>'}
function codeBadge(code){return code===200?'<span class="badge badge-green">'+esc(code)+'</span>':'<span class="badge badge-red">'+esc(code)+'</span>'}
function truncate(s,n){s=s||'';return s.length>n?s.substring(0,n)+'...':s}

// Markdown 渲染（增强版：支持表格/引用/有序列表/嵌套/图片/分隔线/高亮）
function renderMd(text){
  if(!text)return'';
  let html=esc(text);

  // 1. 代码块 ```lang\ncode\n```
  html=html.replace(/```(\w*)\n?([\s\S]*?)```/g,(m,lang,code)=>{
    return '\u0000CODEBLOCK'+(lang||'')+'\u0000'+code.replace(/\n$/,'')+'\u0000END\u0000';
  });

  // 2. 行内代码 `code`
  html=html.replace(/`([^`]+)`/g,'<code class="md-code-inline">$1</code>');

  // 3. 标题
  html=html.replace(/^#### (.+)$/gm,'<h4 class="md-h4">$1</h4>');
  html=html.replace(/^### (.+)$/gm,'<h3 class="md-h3">$1</h3>');
  html=html.replace(/^## (.+)$/gm,'<h2 class="md-h2">$1</h2>');
  html=html.replace(/^# (.+)$/gm,'<h2 class="md-h2">$1</h2>');

  // 4. 水平线
  html=html.replace(/^---+$/gm,'<hr class="md-hr">');

  // 5. 图片 ![alt](url)
  html=html.replace(/!\[([^\]]*)\]\(([^)]+)\)/g,'<img src="$2" alt="$1" class="md-img" loading="lazy">');

  // 6. 链接 [text](url)
  html=html.replace(/\[([^\]]+)\]\(([^)]+)\)/g,'<a href="$2" target="_blank" rel="noopener" class="md-link">$1</a>');

  // 7. 粗体 **text** / 斜体 *text* / 删除线 ~~text~~ / 高亮 ==text==
  html=html.replace(/\*\*([^*]+)\*\*/g,'<strong>$1</strong>');
  html=html.replace(/~~([^~]+)~~/g,'<del>$1</del>');
  html=html.replace(/==([^=]+)==/g,'<mark>$1</mark>');
  html=html.replace(/\*([^*]+)\*/g,'<em>$1</em>');

  // 8. 表格 | a | b |\n|---|---|\n| 1 | 2 |
  html=html.replace(/((?:^\|.+\|\n?)+)/gm,(m)=>{
    const lines=m.trim().split('\n').filter(l=>l.trim());
    if(lines.length<2)return m;
    // 检查第二行是否是分隔行
    if(!/^\|[-:\s|]+$/.test(lines[1]))return m;
    const parseRow=(l)=>l.replace(/^\||\|$/g,'').split('|').map(c=>c.trim());
    const headers=parseRow(lines[0]);
    let table='<table class="md-table"><thead><tr>'+headers.map(h=>'<th>'+h+'</th>').join('')+'</tr></thead><tbody>';
    for(let i=2;i<lines.length;i++){const cells=parseRow(lines[i]);table+='<tr>'+cells.map(c=>'<td>'+c+'</td>').join('')+'</tr>'}
    table+='</tbody></table>';
    return table;
  });

  // 9. 引用块 > text
  html=html.replace(/^&gt; (.+)$/gm,'<blockquote class="md-quote">$1</blockquote>');
  html=html.replace(/(<\/blockquote>\n)(?=&gt; )/g,'$1');
  html=html.replace(/(?:<blockquote[^>]*>.*?<\/blockquote>\n?)+/gs,(m)=>{
    const content=m.replace(/<\/?blockquote[^>]*>/g,'').replace(/\n/g,'<br>');
    return '<blockquote class="md-quote">'+content+'</blockquote>';
  });

  // 10. 有序列表 1. item
  html=html.replace(/^(\d+)\. (.+)$/gm,'<li class="md-oli" data-num="$1">$2</li>');
  html=html.replace(/((?:<li class="md-oli"[^>]*>.*?<\/li>\n?)+)/g,(m)=>{
    return '<ol class="md-ol">'+m.replace(/\n/g,'')+'</ol>';
  });

  // 11. 无序列表 - item / * item
  html=html.replace(/^- (.+)$/gm,'<li class="md-li">$1</li>');
  html=html.replace(/((?:<li class="md-li">.*?<\/li>\n?)+)/g,(m)=>{
    return '<ul class="md-ul">'+m.replace(/\n/g,'')+'</ul>';
  });

  // 12. 段落换行
  html=html.replace(/\n\n/g,'</p><p class="md-p">');
  html=html.replace(/\n/g,'<br>');

  // 13. 修复块级元素内的换行
  html=html.replace(/<br>(?=<\w+>)/g,'');
  html=html.replace(/(<\/(?:pre|h[2-4]|ul|ol|li|table|blockquote|hr|img)>)/g,'$1');

  // 14. 还原代码块
  html=html.replace(/\u0000CODEBLOCK(\w*)\u0000([\s\S]*?)\u0000END\u0000/g,(m,lang,code)=>{
    return '<pre class="md-code-block'+(lang?' lang-'+lang:'')+'"><code>'+code+'</code></pre>';
  });

  return '<div class="md-content"><p class="md-p">'+html+'</p></div>';
}

// Toast 通知
function toast(msg,type='success'){
  const c=document.getElementById('toastContainer');
  const t=document.createElement('div');
  t.className='toast toast-'+type;
  const icon=type==='success'?'✓':type==='error'?'✕':'ℹ';
  t.innerHTML='<span style="font-weight:700">'+icon+'</span><span>'+esc(msg)+'</span>';
  c.appendChild(t);
  setTimeout(()=>{t.style.animation='slideOut .25s forwards';setTimeout(()=>t.remove(),250)},3000);
}

// Modal 弹窗
function showModal(html){
  document.getElementById('modalContent').innerHTML=html;
  document.getElementById('modalOverlay').classList.add('show');
}
function closeModal(){document.getElementById('modalOverlay').classList.remove('show')}

// 确认删除
function confirmDel(type,name,callback){
  showModal(`<div class="modal-header"><div class="modal-title">确认删除</div></div>
  <div class="modal-body">
  <div style="font-size:14px;color:var(--muted-foreground);margin-bottom:8px">确定要删除${type} <strong style="color:var(--foreground)">${esc(name)}</strong> 吗？</div>
  <div style="font-size:12px;color:var(--muted-foreground)">此操作不可撤销。</div>
  </div>
  <div class="modal-actions"><button class="btn btn-secondary" onclick="closeModal()">取消</button><button class="btn btn-danger" onclick="confirmDelOk()">确认删除</button></div>`);
  window._delCallback=callback;
}
function confirmDelOk(){closeModal();if(window._delCallback){window._delCallback();window._delCallback=null}}

// 复制文本到剪贴板
function copyText(text){
  navigator.clipboard.writeText(text).then(()=>toast("已复制","success")).catch(()=>{const ta=document.createElement("textarea");ta.value=text;document.body.appendChild(ta);ta.select();document.execCommand("copy");document.body.removeChild(ta);toast("已复制","success")});
}

// 退出（当前版本无登录鉴权，刷新页面回到管理后台）
async function doLogout(){
  try{
    await fetch(API_BASE+'/api/v1/logout',{method:'POST',headers:{'Content-Type':'application/json'}});
  }catch(e){}
  window.location.reload();
}

// 搜索结果关键词高亮
function highlightText(escapedHtml,query){
  if(!query||!query.trim())return escapedHtml;
  const words=query.trim().split(/\s+/).filter(w=>w.length>=2);
  if(!words.length)return escapedHtml;
  let result=escapedHtml;
  for(const w of words){
    // 转义正则特殊字符
    const safe=w.replace(/[.*+?^${}()|[\]\\]/g,"\\$&");
    result=result.replace(new RegExp("("+safe+")","gi"),"<mark>$1</mark>");
  }
  return result;
}
