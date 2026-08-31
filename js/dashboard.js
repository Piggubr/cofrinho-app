function render() {
  const mesKey = mesKeyDe(viewDate);
  const gastosDoMes = gastos.filter(g => g.data.startsWith(mesKey));
  const total = gastosDoMes.reduce((s, g) => s + g.valor, 0);
  const despesasFinanceiras = document.getElementById('financeExpenses');
  if (despesasFinanceiras) despesasFinanceiras.textContent = fmt(total);

  const totalMesOculto = document.getElementById('mTotal');
  if (totalMesOculto) totalMesOculto.textContent = fmt(total);

  const porCategoria = {};
  gastosDoMes.forEach(g => {
    porCategoria[g.categoria] = (porCategoria[g.categoria] || 0) + g.valor;
  });

  const entradas = Object.entries(porCategoria).sort((a, b) => b[1] - a[1]);
  const categoriaLegada = document.getElementById('mCategoria');
  if (categoriaLegada) categoriaLegada.textContent = entradas.length ? entradas[0][0] : '—';
  const categoriaFinanceira = document.getElementById('financeTopCategory');
  if (categoriaFinanceira) categoriaFinanceira.textContent = entradas.length ? entradas[0][0] : 'Sem gastos';

  const dias = new Set(gastosDoMes.map(g => g.data));
  const mediaLegada = document.getElementById('mMedia');
  if (mediaLegada) mediaLegada.textContent = fmt(total / (dias.size || 1));

  const totalFixos = gastosDoMes.filter(g => g.tipo === 'Fixo').reduce((s, g) => s + g.valor, 0);
  const fixosLegado = document.getElementById('mFixos');
  if (fixosLegado) fixosLegado.textContent = fmt(totalFixos);

  renderMeta(total);
  renderOrcamentoCategorias(porCategoria);

  if (typeof renderLista === 'function') renderLista();
}

function renderOrcamentoCategorias(porCategoria) {
  const lista = document.getElementById('orcamentoLista');
  if (!lista) return;

  const nomes = Object.keys(porCategoria).filter(nome => Number(porCategoria[nome]) > 0);
  if (!nomes.length) {
    lista.innerHTML = '<p class="empty budget-empty">Nenhum gasto neste mês.</p>';
    return;
  }
  const maiorValor = Math.max(1, ...nomes.map(nome => porCategoria[nome] || 0));

  const ordenados = [...nomes].sort((a, b) => (porCategoria[b] || 0) - (porCategoria[a] || 0));

  lista.innerHTML = ordenados.map(nome => {
    const valor = porCategoria[nome] || 0;
    const cor = CATEGORIAS[nome] || CATEGORIAS.Outros;
    const percentual = Math.round((valor / maiorValor) * 100);

    return `
      <div class="budget-row">
        <div class="budget-row-top">
          <span class="budget-cat">${esc(nome)}</span>
          <span class="budget-values">${fmt(valor)}</span>
        </div>
        <div class="budget-bar-track">
          <div class="budget-bar-fill" style="width:${percentual}%;background:${cor};"></div>
        </div>
      </div>
    `;
  }).join('');
}

document.getElementById('btnAdicionarCategoria')?.addEventListener('click', () => {
  abrirModal('Criar categoria', `
    <form id="formNovaCategoria" class="category-form">
      <label for="nomeNovaCategoria">Nome da categoria</label>
      <input id="nomeNovaCategoria" name="nome" maxlength="60" autocomplete="off" required>
      <p class="field-help" id="categoriaFeedback">A categoria será usada nos próximos lançamentos.</p>
      <div class="category-form-actions">
        <button class="button-secondary" id="cancelarNovaCategoria" type="button">Cancelar</button>
        <button class="button-primary" type="submit">Salvar categoria</button>
      </div>
    </form>
  `);

  const form = document.getElementById('formNovaCategoria');
  const feedback = document.getElementById('categoriaFeedback');
  document.getElementById('cancelarNovaCategoria')?.addEventListener('click', fecharModal);
  document.getElementById('nomeNovaCategoria')?.focus();

  form?.addEventListener('submit', async evento => {
    evento.preventDefault();
    const nome = String(new FormData(form).get('nome') || '').trim();
    if (!nome) {
      feedback.textContent = 'Digite um nome para a categoria.';
      document.getElementById('nomeNovaCategoria')?.focus();
      return;
    }

    if (window.PIGGU_DEMO_MODE) {
      feedback.textContent = `Teste concluído: “${nome}” não foi salva no modo demonstração.`;
      form.querySelector('button[type="submit"]').disabled = true;
      return;
    }

    if (!sessionToken) {
      feedback.textContent = 'Entre na sua conta para salvar uma categoria.';
      return;
    }

    const salvar = form.querySelector('button[type="submit"]');
    salvar.disabled = true;
    feedback.textContent = 'Salvando categoria…';
    try {
      await chamarAppsScript({ action: 'addCategory', nome });
      await carregarDados();
      fecharModal();
    } catch (erro) {
      feedback.textContent = erro.message || 'Não foi possível salvar a categoria.';
      salvar.disabled = false;
    }
  });
});

function renderMeta(totalGasto) {
  const metaEl = document.getElementById('metaValor');
  const barraEl = document.getElementById('metaBarra');
  const statusEl = document.getElementById('metaStatus');

  if (!metaEl || !barraEl || !statusEl) return;

  const limite = Number(metas[mesKeyDe(viewDate)]) || 0;
  const saldo = limite - totalGasto;

  metaEl.textContent = limite ? fmt(saldo) : 'Ainda não definida';

  const percentualRestante = limite ? (saldo / limite) * 100 : 0;
  barraEl.style.width = Math.max(0, Math.min(percentualRestante, 100)) + '%';
  barraEl.classList.toggle('over', limite > 0 && totalGasto > limite);

  if (!limite) {
    statusEl.textContent = 'Clique em Editar para definir quanto está disponível.';
  } else if (saldo >= 0) {
    statusEl.textContent = `${fmt(totalGasto)} gastos · saldo positivo de ${fmt(saldo)}`;
  } else {
    statusEl.textContent = `${fmt(totalGasto)} gastos · saldo negativo de ${fmt(Math.abs(saldo))}`;
  }
}

function renderCofrinho() {
  const totalDepositado = cofrinhoMovimentos.reduce((soma, movimento) =>
    soma + (Number(movimento.valor) || 0), 0);

  const inicio = cofrinhoMovimentos.length
    ? cofrinhoMovimentos.map(m => m.registrado_em).filter(Boolean).sort()[0]
    : '';

  const totalGasto = inicio
    ? gastos.filter(g => g.registrado_em && g.registrado_em >= inicio)
        .reduce((soma, g) => soma + (Number(g.valor) || 0), 0)
    : 0;

  const saldo = totalDepositado - totalGasto;

  const alvoTotal = document.getElementById('cofrinhoTotal');
  if (alvoTotal) alvoTotal.textContent = (saldo < 0 ? '-' : '') + Math.abs(saldo).toFixed(2).replace('.', ',');
  const entradasEl = document.getElementById('financeIncome');
  if (entradasEl) entradasEl.textContent = fmt(totalDepositado);

  const historico = document.getElementById('cofrinhoHistorico');
  if (!historico) return;

  const ordenado = [...cofrinhoMovimentos].sort((a, b) => b.data.localeCompare(a.data));

  const resumoGastos = `
    <div class="row" style="padding:8px 0;">
      <div class="row-left"><p class="row-item">Gastos confirmados</p><p class="row-date">Desconto automático</p></div>
      <div class="row-right"><span class="row-value" style="color:var(--over);">-${fmt(totalGasto)}</span></div>
    </div>
  `;

  historico.innerHTML = resumoGastos + ordenado.map(m => `
    <div class="row" style="padding:8px 0;">
      <div class="row-left"><p class="row-item">Depósito</p><p class="row-date">${fmtData(m.data)}</p></div>
      <div class="row-right">
        <span class="row-value" style="color:var(--ok);">+${fmt(Math.abs(Number(m.valor) || 0))}</span>
        <button class="icon-btn" data-delcofre="${esc(m.id)}" aria-label="Excluir">✕</button>
      </div>
    </div>
  `).join('');

  historico.querySelectorAll('[data-delcofre]').forEach(botao => {
    botao.addEventListener('click', async () => {
      if (!confirm('Excluir este depósito?')) return;
      try {
        await chamarAppsScript({ action: 'deleteDeposit', id: botao.dataset.delcofre });
        cofrinhoMovimentos = cofrinhoMovimentos.filter(m => String(m.id) !== botao.dataset.delcofre);
        renderCofrinho();
      } catch (erro) {
        alert(erro.message);
      }
    });
  });

  renderInsightCofrinho(totalDepositado, totalGasto);
}

function renderInsightCofrinho(totalDepositado, totalGasto) {
  const el = document.getElementById('insightTexto');
  if (!el) return;
  if (!cofrinhoMovimentos.length) {
    el.textContent = 'Adicione um depósito pra começar a acompanhar o cofrinho.';
    return;
  }
  const saldo = totalDepositado - totalGasto;
  el.textContent = saldo >= 0
    ? `Guardado até agora: ${fmt(saldo)}.`
    : `Os gastos já passaram os depósitos em ${fmt(Math.abs(saldo))}.`;
}

document.getElementById('btnEditarMeta')?.addEventListener('click', async () => {
  const mes = mesKeyDe(viewDate);
  const atual = Number(metas[mes]) || '';
  const digitado = prompt('Quanto está disponível para gastar em ' + nomeMes(viewDate) + '?', atual);
  if (digitado === null) return;
  const limite = Number(String(digitado).replace(',', '.'));
  if (!Number.isFinite(limite) || limite <= 0) { alert('Digite um valor válido.'); return; }
  try {
    const resposta = await chamarAppsScript({ action: 'setMonthlyGoal', mes, limite });
    metas[mes] = resposta.limite;
    render();
  } catch (erro) { alert(erro.message); }
});

document.getElementById('btnCofrinho')?.addEventListener('click', () => {
  const card = document.getElementById('cofrinhoCard');
  const botao = document.getElementById('btnCofrinho');
  if (!card || !botao) return;

  const vaiAbrir = card.hidden;
  card.hidden = !vaiAbrir;
  botao.setAttribute('aria-expanded', String(vaiAbrir));
  botao.querySelector('[aria-hidden]')?.replaceChildren(document.createTextNode(vaiAbrir ? '⌃' : '⌄'));
});

document.getElementById('financeAddIncome')?.addEventListener('click', () => {
  const card = document.getElementById('cofrinhoCard');
  if (card?.hidden) document.getElementById('btnCofrinho')?.click();
  card?.classList.add('show-income-form');
  document.getElementById('cofrinhoNovoValor')?.focus();
});
document.getElementById('financeAddExpense')?.addEventListener('click', () => {
  abrirAba('adicionar');
  window.scrollTo({ top: 0, behavior:'smooth' });
  setTimeout(() => document.getElementById('novoItem')?.focus(), 250);
});

document.getElementById('btnCofrinhoAdd')?.addEventListener('click', async () => {
  const campo = document.getElementById('cofrinhoNovoValor');
  const valor = Number(campo?.value);
  if (!Number.isFinite(valor) || valor <= 0) return;

  const botao = document.getElementById('btnCofrinhoAdd');
  botao.disabled = true;

  try {
    const resposta = await chamarAppsScript({
      action: 'addDeposit',
      valor,
      data: new Date().toISOString().slice(0, 10)
    });
    cofrinhoMovimentos.push(resposta.deposito);
    campo.value = '';
    renderCofrinho();
    animarPigguLove();
  } catch (erro) {
    alert(erro.message);
  } finally {
    botao.disabled = false;
  }
});

function animarPigguLove() {
  const palco = document.getElementById('pigguStage');
  if (!palco) return;
  palco.classList.remove('love-pop');
  void palco.offsetWidth;
  palco.classList.add('love-pop');
  setTimeout(() => palco.classList.remove('love-pop'), 1300);
}

document.getElementById('cofrinhoPorco')?.addEventListener('click', evento => {
  const porco = evento.currentTarget;
  porco.classList.remove('pig-touch');
  void porco.offsetWidth;
  porco.classList.add('pig-touch');
  setTimeout(() => porco.classList.remove('pig-touch'), 750);
});

document.getElementById('metaTrofeu')?.addEventListener('click', evento => {
  const trofeu = evento.currentTarget;
  trofeu.classList.remove('dance');
  void trofeu.offsetWidth;
  trofeu.classList.add('dance');
  setTimeout(() => trofeu.classList.remove('dance'), 600);
});

document.getElementById('coinGrande')?.addEventListener('click', evento => {
  const moeda = evento.currentTarget;
  moeda.classList.remove('coin-spin');
  void moeda.offsetWidth;
  moeda.classList.add('coin-spin');
  setTimeout(() => moeda.classList.remove('coin-spin'), 700);
});
