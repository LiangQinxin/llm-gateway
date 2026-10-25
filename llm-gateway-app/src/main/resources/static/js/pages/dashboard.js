function pgDashboard(){
  const d=state.data.dashboard||{};
  const logs=state.data.logs||[];
  const channels=state.data.channels||[];
  const apiKeys=state.data.apiKeys||[];
  const availability=channels.length>0?Math.round(channels.filter(c=>c.status===1).length/channels.length*100):100;
  const errRate=d.totalRequests?((d.totalErrors/d.totalRequests*100).toFixed(1)):'0';

  const metrics=[
    {label:'总请求数',value:fmtNum(d.totalRequests??0),icon:I.requests,tone:'var(--tone-blue)',fg:'var(--tone-blue-fg)'},
    {label:'总 Token',value:fmtNum(d.totalTokens??0),icon:I.tokens,tone:'var(--tone-amber)',fg:'var(--tone-amber-fg)'},
    {label:'活跃渠道',value:(d.activeChannels??0)+'/'+channels.length,icon:I.channels,tone:'var(--tone-emerald)',fg:'var(--tone-emerald-fg)'},
    {label:'活跃密钥',value:d.activeApiKeys??0,icon:I.keys,tone:'var(--tone-indigo)',fg:'var(--tone-indigo-fg)'},
    {label:'平均延迟',value:Math.round(d.avgDurationMs||0)+'ms',icon:I.active,tone:'var(--tone-violet)',fg:'var(--tone-violet-fg)'},
    {label:'错误次数',value:d.totalErrors??0,icon:I.test,tone:'var(--tone-rose)',fg:'var(--tone-rose-fg)'},
    {label:'健康分',value:d.healthScore??100,icon:I.security,tone:'var(--tone-cyan)',fg:'var(--tone-cyan-fg)'},
    {label:'健康等级',value:d.healthBadge||'excellent',icon:I.dashboard,tone:'var(--tone-teal)',fg:'var(--tone-teal-fg)'},
  ];

  const quickActions=[
    {title:'新建渠道',icon:I.plus,page:'channels'},
    {title:'管理密钥',icon:I.keys,page:'apikeys'},
    {title:'接入示例',icon:I.usage,page:'usage'},
    {title:'审计日志',icon:I.logs,page:'logs'},
    {title:'安全设置',icon:I.security,page:'security'},
  ];

  return`<div class="page-shell">
  <div style="display:flex;align-items:flex-end;justify-content:space-between;gap:24px;margin-bottom:20px;flex-wrap:wrap">
    <div>
      <h1 style="font-size:30px;font-weight:700;letter-spacing:-.03em;color:var(--foreground)">欢迎使用 LLMAPI</h1>
      <p style="margin-top:10px;font-size:14px;line-height:1.7;color:var(--muted-foreground);white-space:nowrap;overflow:hidden;text-overflow:ellipsis">在一个统一入口中管理上游模型渠道、下游密钥、请求统计与故障切换，让本地 LLM 网关更稳定、更清晰、更易运维。</p>
    </div>
    <div style="display:flex;gap:12px;flex-wrap:wrap">
      <div class="health-mini" style="border-color:${availability>=80?'rgba(16,185,129,.2)':availability>=50?'rgba(245,158,11,.2)':'rgba(244,63,94,.2)'};background:${availability>=80?'var(--tone-emerald)':availability>=50?'var(--tone-amber)':'var(--tone-rose)'}">
        ${I.security}
        <div>
          <div style="font-size:12px;color:var(--muted-foreground)">服务可用率</div>
          <div style="font-size:18px;font-weight:700;color:${availability>=80?'var(--tone-emerald-fg)':availability>=50?'var(--tone-amber-fg)':'var(--tone-rose-fg)'}">${availability}%</div>
        </div>
      </div>
      <div class="health-mini">
        ${I.tokens}
        <div>
          <div style="font-size:12px;color:var(--muted-foreground)">错误率</div>
          <div style="font-size:18px;font-weight:700;color:${parseFloat(errRate)>5?'var(--destructive)':'var(--foreground)'}">${errRate}%</div>
        </div>
      </div>
    </div>
  </div>

  <div style="display:flex;flex-wrap:wrap;gap:8px;margin-bottom:16px">
    ${quickActions.map(a=>`<button class="pill-btn" onclick="switchTab('${a.page}')">${a.icon}${a.title}</button>`).join('')}
  </div>

  <div class="grid grid-8" style="margin-bottom:16px">
    ${metrics.map(m=>`<div class="surface data-card">
      <div class="stat-icon" style="background:${m.tone};color:${m.fg}">${m.icon}</div>
      <div class="stat-value">${esc(String(m.value))}</div>
      <div class="stat-label">${m.label}</div>
    </div>`).join('')}
  </div>

  <div class="grid grid-2" style="margin-bottom:16px">
    <div class="surface" style="padding:24px">
      <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:16px">
        <div>
          <h2 style="font-size:18px;font-weight:700;color:var(--foreground)">系统健康度</h2>
          <p style="font-size:13px;color:var(--muted-foreground);margin-top:2px">综合服务运行状态评估</p>
        </div>
        <span class="badge ${healthBadgeClass(d.healthBadge)}">${esc(d.healthBadge||'excellent')}</span>
      </div>
      <div style="display:flex;align-items:center;gap:16px">
        <div style="flex:1;height:12px;background:var(--slate-l);border-radius:var(--rs-full);overflow:hidden"><div style="height:100%;width:${d.healthScore??100}%;background:${healthFill(d.healthScore??100)};border-radius:var(--rs-full);transition:width .6s ease"></div></div>
        <div style="font-size:26px;font-weight:700;min-width:48px;text-align:right">${d.healthScore??100}</div>
      </div>
      <div style="display:flex;justify-content:space-between;margin-top:16px;font-size:13px;color:var(--muted-foreground)">
        <span>平均耗时 <strong style="color:var(--foreground);font-weight:600">${Math.round(d.avgDurationMs||0)}ms</strong></span>
        <span>错误 <strong style="color:var(--foreground);font-weight:600">${d.totalErrors??0}</strong> 次</span>
      </div>
    </div>
    <div class="surface" style="padding:24px">
      <h2 style="font-size:18px;font-weight:700;color:var(--foreground);margin-bottom:16px">最近请求时间分布</h2>
      ${trendChart(logs)}
    </div>
  </div>

  <div class="grid grid-2" style="margin-bottom:16px">
    <div class="surface" style="padding:24px">
      <h2 style="font-size:18px;font-weight:700;color:var(--foreground);margin-bottom:14px">渠道调用分布</h2>
      ${channelDist(channels,logs)}
    </div>
    <div class="surface" style="padding:24px">
      <h2 style="font-size:18px;font-weight:700;color:var(--foreground);margin-bottom:14px">最近请求</h2>
      ${recentRequests(logs)}
    </div>
  </div>

  <section class="surface" style="padding:24px">
    <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:18px">
      <div>
        <h2 style="font-size:18px;font-weight:700;color:var(--foreground)">运维建议</h2>
        <p style="font-size:13px;color:var(--muted-foreground);margin-top:2px">根据当前系统状态给出的运维参考</p>
      </div>
    </div>
    <div class="grid grid-4">
      <div class="op-card"><div class="op-card-title">${I.channels}<span>渠道健康度</span></div><p class="op-card-desc">${availability>=80?'当前渠道运行正常，各线路可用。':availability>=50?'部分渠道不可用，建议检查并启用备用线路。':'活跃渠道较少，请前往渠道页测试并启用。'}</p></div>
      <div class="op-card"><div class="op-card-title">${I.keys}<span>密钥配额</span></div><p class="op-card-desc">${apiKeys.length>0?'共 '+apiKeys.length+' 个密钥，定期检查配额使用情况。':'尚未创建密钥，请前往 API 密钥页创建。'}</p></div>
      <div class="op-card"><div class="op-card-title">${I.active}<span>性能监控</span></div><p class="op-card-desc">${(d.avgDurationMs||0)<2000?'平均延迟 '+Math.round(d.avgDurationMs||0)+'ms，响应正常。':'平均延迟较高，建议查看日志排查慢请求。'}</p></div>
      <div class="op-card"><div class="op-card-title">${I.security}<span>安全审计</span></div><p class="op-card-desc">${(state.data.security?.findings?.length||0)>0?'发现 '+(state.data.security.findings.length)+' 条安全记录，请前往安全审计查看。':'当前无安全风险记录，系统运行安全。'}</p></div>
    </div>
  </section>
  </div>`;
}

function healthBadgeClass(b){
  if(b==='excellent')return'badge-green';
  if(b==='good')return'badge-blue';
  if(b==='warning')return'badge-amber';
  return'badge-red';
}
function healthFill(s){if(s>=90)return'linear-gradient(90deg,#22c55e,#10b981)';if(s>=70)return'linear-gradient(90deg,#f59e0b,#fbbf24)';return'linear-gradient(90deg,#ef4444,#f87171)'}

function trendChart(logs){
  if(!logs.length)return emptyState('暂无数据','请求后将展示趋势');
  const hours={};
  const now=new Date();
  for(let i=11;i>=0;i--){const h=new Date(now-i*3600*1000);const k=h.getHours()+':00';hours[k]=0}
  logs.forEach(l=>{try{const t=new Date(l.createdAt);const diff=(now-t)/3600000;if(diff<12){const k=t.getHours()+':00';if(k in hours)hours[k]++}}catch(e){}});
  const max=Math.max(...Object.values(hours),1);
  const peakHour=Object.entries(hours).sort((a,b)=>b[1]-a[1])[0]?.[0];
  return`<div class="dash-trend">${Object.entries(hours).map(([h,n])=>`<div class="dash-trend-bar${h===peakHour&&n>0?' peak':''}" style="height:${n/max*100}%" title="${h}: ${n}次"></div>`).join('')}</div>
  <div style="display:flex;justify-content:space-between;margin-top:8px;font-size:11px;color:var(--muted-foreground)"><span>12h 前</span><span>现在</span></div>`;
}

function channelDist(channels,logs){
  if(!channels.length&&!logs.length)return emptyState('暂无渠道','添加渠道后查看分布');
  const byName={};
  logs.forEach(l=>{const n=l.channelName||'未知';byName[n]=(byName[n]||0)+1});
  const entries=Object.entries(byName);
  if(!entries.length){
    const byType={};channels.forEach(c=>{byType[c.type]=(byType[c.type]||0)+1});
    const total=channels.length||1;
    const colors=['#2f6fed','#10b981','#f59e0b','#7c3aed','#f43f5e','#06b6d4'];
    return Object.entries(byType).map(([t,n],i)=>`<div class="dist-row"><span class="dist-dot" style="background:${colors[i%colors.length]}"></span><span style="flex:1">${esc(t)}</span><div class="dist-bar-wrap"><div class="dist-bar-fill" style="width:${n/total*100}%;background:${colors[i%colors.length]}"></div></div><span class="dist-count">${n}</span></div>`).join('')||emptyState('暂无数据','');
  }
  const total=logs.length;
  const colors=['#2f6fed','#10b981','#f59e0b','#7c3aed','#f43f5e','#06b6d4','#ec4899'];
  return entries.sort((a,b)=>b[1]-a[1]).map(([t,n],i)=>`<div class="dist-row"><span class="dist-dot" style="background:${colors[i%colors.length]}"></span><span style="flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">${esc(t)}</span><div class="dist-bar-wrap"><div class="dist-bar-fill" style="width:${n/total*100}%;background:${colors[i%colors.length]}"></div></div><span class="dist-count">${n}</span></div>`).join('');
}

function recentRequests(logs){
  const list=(logs||[]).slice(0,8);
  if(!list.length)return emptyState('暂无请求记录','请求后将在此展示');
  return`<div style="display:flex;flex-direction:column;gap:2px">${list.map(l=>`<div class="recent-item">
    <span class="recent-time">${fmtTime(l.createdAt)}</span>
    <span class="recent-model">${esc(l.model||'-')}</span>
    ${codeBadge(l.statusCode)}
    <span class="recent-dur">${(l.durationMs||0)+'ms'}</span>
  </div>`).join('')}</div>`;
}

async function loadDashboard(){
  const r=await api('/dashboard');if(r.code==='0000')state.data.dashboard=r.data;
  const rl=await api('/logs?page=1&size=50');if(rl.code==='0000')state.data.logs=rl.data?.records||[];
  const rc=await api('/channels');if(rc.code==='0000')state.data.channels=rc.data||[];
  const rk=await api('/keys');if(rk.code==='0000')state.data.apiKeys=rk.data||[];
  updatePage('dashboard');
}
