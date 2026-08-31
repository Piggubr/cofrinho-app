let lugares = [];

function abrirFormularioLugar() {
  const form = document.getElementById('lugarFormCard');
  const toggle = document.getElementById('btnAbrirNovoLugar');
  if (form) form.style.display = 'grid';
  if (toggle) toggle.style.display = 'none';
}

function renderLugares() {
  const lista = document.getElementById('lugaresLista');
  if (!lista) return;

  if (!lugares.length) {
    lista.innerHTML = '<p class="empty">Nenhum lugar registrado ainda.</p>';
    return;
  }

  lista.innerHTML = lugares.map(lugar => {
    const foto = lugar.url
      ? `<img src="${esc(lugar.url)}" alt="Foto de ${esc(lugar.nome)}" loading="lazy" decoding="async">`
      : (lugar.temFoto ? 'Carregando foto...' : 'Sem foto');

    const estrelas = Array.from({ length: Math.max(1, Math.min(5, Number(lugar.nota) || 0)) }, () =>
      `<img src="assets/icons/estrela.svg" alt="">`
    ).join('');

    return `
      <div class="place-item">
        <div class="place-photo">${foto}</div>
        <div class="place-info">
          <div class="place-item-top">
            <div>
              <p class="place-name">${esc(lugar.nome)}</p>
              <p class="place-meta">${esc(lugar.categoria)} ${lugar.localizacao ? '• ' + esc(lugar.localizacao) : ''} ${lugar.data ? '• ' + esc(lugar.data) : ''}</p>
              ${Number(lugar.valor) > 0 ? `<span class="valor-privado">${fmt(Number(lugar.valor))}</span>` : ''}
            </div>
            <span class="place-rating" aria-label="Nota ${lugar.nota}">${estrelas}</span>
          </div>
          ${lugar.comentario ? `<p class="place-comment">${esc(lugar.comentario)}</p>` : ''}
          <div class="place-badges">
            ${(lugar.marcacoes || []).map(m => `<span class="place-badge">${esc(m)}</span>`).join('')}
          </div>
        </div>
        <div style="display:flex;gap:8px;margin-top:8px;">
          <button class="icon-btn" data-edit-place="${lugar.id}">Editar</button>
          <button class="icon-btn" data-del-place="${lugar.id}">Excluir</button>
        </div>
      </div>
    `;
  }).join('');

  lista.querySelectorAll('[data-del-place]').forEach(botao => {
    botao.addEventListener('click', async () => {
      if (!confirm('Excluir este lugar?')) return;
      try {
        await chamarAppsScript({ action: 'deletePlace', id: botao.dataset.delPlace });
        await carregarDados();
      } catch (erro) {
        alert(erro.message);
      }
    });
  });

  lista.querySelectorAll('[data-edit-place]').forEach(botao => {
    botao.addEventListener('click', () => {
      editarLugar(botao.dataset.editPlace);
    });
  });
}

let lugarFotoSelecionada = null;
let lugarFotoBase64 = null;
let lugarFotoMime = null;
let lugarEmEdicaoId = null;

function limparFormularioLugar() {
  lugarEmEdicaoId = null;
  lugarFotoSelecionada = null;
  lugarFotoBase64 = null;
  lugarFotoMime = null;

  document.getElementById('lugarNome').value = '';
  document.getElementById('lugarCategoria').value = 'Restaurante';
  document.getElementById('lugarNota').value = '5';
  document.getElementById('lugarLocalizacao').value = '';
  document.getElementById('lugarData').value = '';
  document.getElementById('lugarValor').value = '';
  document.getElementById('lugarComentario').value = '';
  document.getElementById('lugarFoto').value = '';

  const preview = document.getElementById('lugarFotoPreview');
  if (preview) preview.style.display = 'none';

  document.querySelectorAll('#lugarMarcadores input[type="checkbox"]').forEach(caixa => {
    caixa.checked = false;
  });
}

document.getElementById('btnAbrirNovoLugar')?.addEventListener('click', () => {
  limparFormularioLugar();
  abrirFormularioLugar();
});

document.getElementById('lugarFoto')?.addEventListener('change', evento => {
  const arquivo = evento.target.files?.[0];
  if (!arquivo) return;

  const leitor = new FileReader();
  leitor.onload = () => {
    const dataUrl = String(leitor.result || '');
    const [, mime, base64] = dataUrl.match(/^data:(.+?);base64,(.*)$/) || [];

    lugarFotoBase64 = base64 || null;
    lugarFotoMime = mime || arquivo.type || 'image/jpeg';
    lugarFotoSelecionada = dataUrl;

    const preview = document.getElementById('lugarFotoPreview');
    if (preview) {
      preview.src = dataUrl;
      preview.style.display = 'block';
    }
  };
  leitor.readAsDataURL(arquivo);
});

function editarLugar(id) {
  const lugar = lugares.find(l => String(l.id) === String(id));
  if (!lugar) return;

  limparFormularioLugar();
  abrirFormularioLugar();

  lugarEmEdicaoId = lugar.id;
  document.getElementById('lugarNome').value = lugar.nome || '';
  document.getElementById('lugarCategoria').value = lugar.categoria || 'Restaurante';
  document.getElementById('lugarNota').value = lugar.nota || 5;
  document.getElementById('lugarLocalizacao').value = lugar.localizacao || '';
  document.getElementById('lugarData').value = lugar.data || '';
  document.getElementById('lugarValor').value = lugar.valor || '';
  document.getElementById('lugarComentario').value = lugar.comentario || '';

  (lugar.marcacoes || []).forEach(marcacao => {
    const caixa = document.querySelector(`#lugarMarcadores input[value="${CSS.escape(marcacao)}"]`);
    if (caixa) caixa.checked = true;
  });

  lugarFotoSelecionada = lugar.url || null;
  const preview = document.getElementById('lugarFotoPreview');
  if (preview) {
    preview.style.display = lugarFotoSelecionada ? 'block' : 'none';
    if (lugarFotoSelecionada) preview.src = lugarFotoSelecionada;
  }
}

document.getElementById('btnSalvarLugar')?.addEventListener('click', async () => {
  const nome = document.getElementById('lugarNome').value.trim();
  const categoria = document.getElementById('lugarCategoria').value;
  const nota = Number(document.getElementById('lugarNota').value);
  const localizacao = document.getElementById('lugarLocalizacao').value.trim();
  const data = document.getElementById('lugarData').value;
  const valor = Number(document.getElementById('lugarValor').value || 0);
  const comentario = document.getElementById('lugarComentario').value.trim();
  const marcacoes = Array.from(
    document.querySelectorAll('#lugarMarcadores input[type="checkbox"]:checked')
  ).map(caixa => caixa.value);

  if (!nome) {
    alert('Digite o nome do lugar.');
    return;
  }

  if (!data) {
    alert('Escolha a data da visita.');
    return;
  }

  const payload = {
    action: 'savePlace',
    nome,
    categoria,
    nota,
    localizacao,
    data,
    valor,
    comentario,
    marcacoes
  };

  if (lugarEmEdicaoId) payload.id = lugarEmEdicaoId;
  if (lugarFotoBase64) {
    payload.image_base64 = lugarFotoBase64;
    payload.mime_type = lugarFotoMime;
  }

  try {
    await chamarAppsScript(payload);
    limparFormularioLugar();
    document.getElementById('lugarFormCard').style.display = 'none';
    document.getElementById('btnAbrirNovoLugar').style.display = '';
    await carregarDados();
  } catch (erro) {
    alert(erro.message);
  }
});

document.getElementById('btnAdicionarMarcador')?.addEventListener('click', async () => {
  const nome = prompt('Nome da nova caixinha (marcador):');
  if (!nome || !nome.trim()) return;

  const marcadosAntes = Array.from(
    document.querySelectorAll('#lugarMarcadores input[type="checkbox"]:checked')
  ).map(caixa => caixa.value);

  try {
    await chamarAppsScript({ action: 'addPlaceTag', nome: nome.trim() });
    await carregarDados();

    marcadosAntes.forEach(valor => {
      const caixa = document.querySelector(`#lugarMarcadores input[value="${CSS.escape(valor)}"]`);
      if (caixa) caixa.checked = true;
    });
  } catch (erro) {
    alert(erro.message);
  }
});

async function carregarFotosLugares() {
  const pendentes = lugares.filter(lugar => lugar.temFoto && !lugar.url);
  await Promise.all(pendentes.map(async lugar => {
    try {
      const resposta = await chamarAppsScript({ action: 'getPlacePhoto', id: lugar.id });
      lugar.url = resposta.url || resposta.image_base64 || '';
    } catch (erro) {
      console.warn('Não foi possível carregar a foto do lugar.', erro);
    }
  }));
  renderLugares();
}
