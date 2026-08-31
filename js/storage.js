function salvarEstado() {
  if (window.PIGGU_DEMO_MODE) return;
  try {
    const prefixo = prefixoEstadoLocal();
    if (!prefixo) return;
    localStorage.setItem(prefixo + 'contas', JSON.stringify(contas));
    localStorage.setItem(prefixo + 'eventos', JSON.stringify(eventos));
    localStorage.setItem(prefixo + 'pagamentos', JSON.stringify([...pagamentos]));
    localStorage.setItem(prefixo + 'fotos', JSON.stringify(fotos));
    localStorage.setItem(prefixo + 'notas', JSON.stringify(notas));
    localStorage.setItem(prefixo + 'cofrinhoMovimentos', JSON.stringify(cofrinhoMovimentos));
    localStorage.setItem(prefixo + 'nextContaId', String(nextContaId));
    localStorage.setItem(prefixo + 'nextEventoId', String(nextEventoId));
    localStorage.setItem(prefixo + 'nextFotoId', String(nextFotoId));
    localStorage.setItem(prefixo + 'nextNotaId', String(nextNotaId));
  } catch (e) {
    console.warn('Não foi possível salvar estado local:', e);
  }
}

function carregarEstado() {
  if (window.PIGGU_DEMO_MODE) return;
  try {
    const prefixo = prefixoEstadoLocal();
    if (!prefixo) return;
    const c = localStorage.getItem(prefixo + 'contas');
    const e = localStorage.getItem(prefixo + 'eventos');
    const p = localStorage.getItem(prefixo + 'pagamentos');
    const f = localStorage.getItem(prefixo + 'fotos');
    const n = localStorage.getItem(prefixo + 'notas');
    const m = localStorage.getItem(prefixo + 'cofrinhoMovimentos');
    const nci = localStorage.getItem(prefixo + 'nextContaId');
    const nei = localStorage.getItem(prefixo + 'nextEventoId');
    const nfi = localStorage.getItem(prefixo + 'nextFotoId');
    const nni = localStorage.getItem(prefixo + 'nextNotaId');

    if (c) contas = JSON.parse(c);
    if (e) eventos = JSON.parse(e);
    if (p) pagamentos = new Set(JSON.parse(p));
    if (f) fotos = JSON.parse(f);
    if (n) notas = JSON.parse(n);
    if (m) cofrinhoMovimentos = JSON.parse(m);
    if (nci) nextContaId = Number(nci);
    if (nei) nextEventoId = Number(nei);
    if (nfi) nextFotoId = Number(nfi);
    if (nni) nextNotaId = Number(nni);
  } catch (e) {
    console.warn('Não foi possível carregar estado local:', e);
  }
}

function prefixoEstadoLocal() {
  const email = String(usuarioDetalhes?.email || usuarioAtual || '').trim().toLowerCase();
  if (!email) return '';
  return `cofrinho_${email.replace(/[^a-z0-9@._-]/g, '_')}_`;
}
