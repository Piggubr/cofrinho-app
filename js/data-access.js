(function(){
  function safeUser(user){ return String(user||'').trim().toLowerCase().replace(/[^a-z0-9@._-]/g,'_'); }
  function local(namespace,user){
    const owner=safeUser(user), key='piggu_'+namespace+'_v1_'+owner;
    return {
      async read(fallback){ if(!owner||['pending','demo'].includes(owner)) return fallback; try{return JSON.parse(localStorage.getItem(key))??fallback}catch(_){return fallback} },
      async write(value){ if(!owner||['pending','demo'].includes(owner)) throw new Error('É preciso uma sessão válida para salvar.'); localStorage.setItem(key,JSON.stringify(value)); return value },
      async remove(){ if(!owner||['pending','demo'].includes(owner)) return; localStorage.removeItem(key) }
    };
  }
  window.PigguData={local,async command(action,payload={}){return chamarAppsScript({action,...payload})},isReadOnly(){return Boolean(window.PIGGU_DEMO_MODE)}};
})();
