// 后端 API 地址推导：
// - 页面由后端 8091 提供时（同源）→ 空串，走相对路径
// - 页面由其他来源打开（Live Server / 静态服务器 / file://）→ 指向后端默认地址
const API_BASE=(function(){
  var host=location.hostname||'127.0.0.1';
  var port=parseInt(location.port,10)||(location.protocol==='https:'?443:80);
  return port===8091?'':'http://'+host+':8091';
})();

async function api(path,opts={}){
  try{
    const r=await fetch(API_BASE+'/admin/api'+path,{headers:{'Content-Type':'application/json'},...opts});
    if(!r.ok){
      let msg='请求失败 ('+r.status+')';
      try{const j=await r.json();if(j.info)msg=j.info}catch(e){}
      return{code:'ERR',info:msg};
    }
    return await r.json();
  }catch(e){
    return{code:'ERR',info:'网络错误：'+e.message};
  }
}
