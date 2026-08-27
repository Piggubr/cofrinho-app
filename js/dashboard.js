function render() {
  const mesKey = mesKeyDe(viewDate);
  const gastosDoMes = gastos.filter(g => g.data.startsWith(mesKey));
  const total = gastosDoMes.reduce((s, g) => s + g.valor, 0);

  document.getElementById('mTotal').textContent = fmt(total);

  const porCategoria = {};
  gastosDoMes.forEach(g => {
    porCategoria[g.categoria] = (porCategoria[g.categoria] || 0) + g.valor;
  });

  const entradas = Object.entries(porCategoria).sort((a, b) => b[1] - a[1]);
  document.getElementById('mCategoria').textContent = entradas.length ? entradas[0][0] : '—';

  const dias = new Set(gastosDoMes.map(g => g.data));
  document.getElementById('mMedia').textContent = fmt(total / (dias.size || 1));

  const totalFixos = gastosDoMes.filter(g => g.tipo === 'Fixo').reduce((s, g) => s + g.valor, 0);
  document.getElementById('mFixos').textContent = fmt(totalFixos);

  renderMeta(total);

  if (typeof renderLista === 'function') renderLista();
}

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

  const vaiAbrir = card.style.display === 'none';
  card.style.display = vaiAbrir ? 'block' : 'none';
  botao.setAttribute('aria-expanded', String(vaiAbrir));
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
