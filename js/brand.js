(function(){
  async function aplicarMarcaTematica(){
    const alvos=[...document.querySelectorAll('img[src$="assets/logos/logo-v2.svg"]')];
    if(!alvos.length)return;
    try{
      const texto=await fetch('assets/logos/logo-v2.svg').then(r=>{if(!r.ok)throw new Error('logo');return r.text()});
      alvos.forEach(img=>{
        const svg=new DOMParser().parseFromString(texto,'image/svg+xml').documentElement;
        const grupos=[...svg.children].filter(el=>el.tagName.toLowerCase()==='g');
        const marca=grupos[0], assinatura=grupos[1];
        if(marca?.children[1])marca.children[1].classList.add('piggu-lettering');
        assinatura?.classList.add('piggu-lettering');
        const estilo=document.createElementNS('http://www.w3.org/2000/svg','style');
        estilo.textContent='.piggu-lettering .cls-2{fill:var(--theme-strong)!important}';
        svg.prepend(estilo);svg.setAttribute('class',((img.getAttribute('class')||'')+' piggu-logo-inline').trim());svg.setAttribute('role','img');svg.setAttribute('aria-label',img.alt||'Piggu');
        img.replaceWith(svg);
      });
    }catch(erro){console.warn('A marca original foi mantida como imagem.',erro)}
  }
  document.addEventListener('DOMContentLoaded',aplicarMarcaTematica);
})();
