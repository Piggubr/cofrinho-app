function salvarEstado() {
  try {
    localStorage.setItem('cofrinho_contas', JSON.stringify(contas));
    localStorage.setItem('cofrinho_eventos', JSON.stringify(eventos));
    localStorage.setItem('cofrinho_pagamentos', JSON.stringify([...pagamentos]));
    localStorage.setItem('cofrinho_fotos', JSON.stringify(fotos));
    localStorage.setItem('cofrinho_notas', JSON.stringify(notas));
    localStorage.setItem('cofrinho_cofrinhoMovimentos', JSON.stringify(cofrinhoMovimentos));
    localStorage.setItem('cofrinho_nextContaId', String(nextContaId));
    localStorage.setItem('cofrinho_nextEventoId', String(nextEventoId));
    localStorage.setItem('cofrinho_nextFotoId', String(nextFotoId));
    localStorage.setItem('cofrinho_nextNotaId', String(nextNotaId));
  } catch (e) {
    console.warn('Não foi possível salvar estado local:', e);
  }
}

function carregarEstado() {
  try {
    const c = localStorage.getItem('cofrinho_contas');
    const e = localStorage.getItem('cofrinho_eventos');
    const p = localStorage.getItem('cofrinho_pagamentos');
    const f = localStorage.getItem('cofrinho_fotos');
    const n = localStorage.getItem('cofrinho_notas');
    const m = localStorage.getItem('cofrinho_cofrinhoMovimentos');
    const nci = localStorage.getItem('cofrinho_nextContaId');
    const nei = localStorage.getItem('cofrinho_nextEventoId');
    const nfi = localStorage.getItem('cofrinho_nextFotoId');
    const nni = localStorage.getItem('cofrinho_nextNotaId');

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