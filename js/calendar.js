document.getElementById('btnAbrirNovaNota')?.addEventListener('click', () => {
  const form = document.getElementById('notaFormCard');
  const toggle = document.getElementById('btnAbrirNovaNota');
  if (form) form.style.display = 'block';
  if (toggle) toggle.style.display = 'none';
});

function fecharFormularioNota() {
  const form = document.getElementById('notaFormCard');
  const toggle = document.getElementById('btnAbrirNovaNota');

  document.getElementById('notaTitulo').value = '';
  document.getElementById('notaTexto').value = '';
  document.getElementById('notaEhLembrete').checked = false;
  document.getElementById('notaTemGasto').checked = false;
  document.getElementById('notaData').value = '';
  document.getElementById('notaValor').value = '';
  document.getElementById('notaLembreteOpcoes').style.display = 'none';
  document.getElementById('notaGastoOpcoes').style.display = 'none';

  if (form) form.style.display = 'none';
  if (toggle) toggle.style.display = '';
}

document.getElementById('notaEhLembrete')?.addEventListener('change', evento => {
  const opcoes = document.getElementById('notaLembreteOpcoes');
  if (opcoes) opcoes.style.display = evento.target.checked ? 'block' : 'none';

  if (!evento.target.checked) {
    document.getElementById('notaTemGasto').checked = false;
    document.getElementById('notaGastoOpcoes').style.display = 'none';
  }
});

document.getElementById('notaTemGasto')?.addEventListener('change', evento => {
  const opcoes = document.getElementById('notaGastoOpcoes');
  if (opcoes) opcoes.style.display = evento.target.checked ? 'grid' : 'none';
});

document.getElementById('btnNovaNota')?.addEventListener('click', async () => {
  const titulo = document.getElementById('notaTitulo').value.trim();
  const texto = document.getElementById('notaTexto').value.trim();
  const ehLembrete = document.getElementById('notaEhLembrete').checked;
  const temGasto = document.getElementById('notaTemGasto').checked;
  const data = ehLembrete ? document.getElementById('notaData').value : '';
  const valor = temGasto ? Number(document.getElementById('notaValor').value || 0) : 0;
  const categoria = document.getElementById('notaCategoria').value;

  if (!titulo) {
    alert('Digite o título da nota.');
    return;
  }

  if (ehLembrete && !data) {
    alert('Escolha a data do lembrete.');
    return;
  }

  if (temGasto && valor <= 0) {
    alert('Digite quanto esse evento vai custar.');
    return;
  }

  try {
    await chamarAppsScript({
      action: 'saveNote',
      titulo,
      texto,
      data,
      valor,
      categoria
    });

    fecharFormularioNota();
    await carregarDados();
  } catch (erro) {
    alert(erro.message);
  }
});

function renderCalendar() {
  const grid = document.getElementById('calGrid');
  if (!grid) return;

  const ano = viewDate.getFullYear();
  const mes = viewDate.getMonth();

  const primeiroDiaMes = new Date(ano, mes, 1);
  const diasNoMes = new Date(ano, mes + 1, 0).getDate();
  const diaSemanaInicio = primeiroDiaMes.getDay();

  const hoje = new Date();
  const ehHoje = d =>
    d.getDate() === hoje.getDate() &&
    d.getMonth() === hoje.getMonth() &&
    d.getFullYear() === hoje.getFullYear();

  const cells = [];
  for (let i = 0; i < diaSemanaInicio; i++) {
    cells.push('<div class="cal-cell empty-cell"></div>');
  }

  for (let dia = 1; dia <= diasNoMes; dia++) {
    const dataIso = `${ano}-${String(mes + 1).padStart(2, '0')}-${String(dia).padStart(2, '0')}`;
    const temConta = contas.some(c => c.dia === dia);
    const temEvento = eventos.some(e => e.data === dataIso);

    const classes = ['cal-cell'];
    if (ehHoje(new Date(ano, mes, dia))) classes.push('today');

    const dots = [];
    if (temConta) {
      const pago = pagamentos.has(`${dataIso}-conta`);
      dots.push(`<span class="dot conta${pago ? ' paid' : ''}"></span>`);
    }
    if (temEvento) {
      const ev = eventos.find(e => e.data === dataIso);
      dots.push(`<span class="dot evento${ev?.pago ? ' paid' : ''}"></span>`);
    }

    cells.push(`
      <div class="${classes.join(' ')}">
        <span class="cal-daynum">${dia}</span>
        <div class="cal-dots">${dots.join('')}</div>
      </div>
    `);
  }

  grid.innerHTML = cells.join('');
}

function sincronizarLembretesNotas() {
  eventos = eventos.filter(evento => evento.origem !== 'nota');
  notas.filter(nota => nota.data).forEach(nota => {
    eventos.push({
      id: `nota-${nota.id}`,
      nome: nota.titulo,
      data: nota.data,
      valor: Number(nota.valor) || null,
      pago: false,
      categoria: nota.categoria || 'Outros',
      gastoId: nota.gastoId || null,
      origem: 'nota'
    });
  });
}

function renderNotas() {
  const lista = document.getElementById('notasLista');
  if (!lista) return;
  lista.innerHTML = notas.length ? notas.map(nota => `
    <div class="row">
      <div class="row-left">
        <p class="row-item">${esc(nota.titulo)}</p>
        <p class="row-date">${nota.data ? esc(fmtData(nota.data)) : 'Nota'}${nota.texto ? ' • ' + esc(nota.texto) : ''}</p>
      </div>
      <div class="row-right">
        <button class="icon-btn" data-del-nota="${esc(nota.id)}" type="button">Excluir</button>
      </div>
    </div>
  `).join('') : '<p class="empty">Nenhuma nota ainda.</p>';

  lista.querySelectorAll('[data-del-nota]').forEach(botao => {
    botao.addEventListener('click', async () => {
      if (!confirm('Excluir esta nota?')) return;
      try {
        await chamarAppsScript({ action: 'deleteNote', id: botao.dataset.delNota });
        await carregarDados();
      } catch (erro) {
        alert(erro.message);
      }
    });
  });
}
