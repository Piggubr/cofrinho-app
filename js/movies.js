let resultadoFilmes = [];

function htmlFilme(filme, opcoes) {
  const poster = filme.poster
    ? `<img class="movie-poster" src="${esc(filme.poster)}" alt="Pôster de ${esc(filme.titulo)}" loading="lazy" decoding="async">`
    : '<div class="movie-poster"></div>';

  let acoes = '';
  if (opcoes.resultado) acoes = `<button data-salvar-filme="${opcoes.indice}">+ Quero assistir</button>`;
  if (opcoes.salvo) acoes = `<button data-visto-filme="${esc(filme.id)}">${filme.assistido ? 'Voltar à lista' : '✓ Assistido'}</button><button data-del-filme="${esc(filme.id)}">Excluir</button>`;

  const notas = filme.avaliacoes || {};
  const valoresNotas = Object.values(notas).map(Number).filter(n => n >= 1 && n <= 5);
  const media = valoresNotas.length ? valoresNotas.reduce((s, n) => s + n, 0) / valoresNotas.length : 0;
  const minhaNota = Number(notas[usuarioAtual]) || 0;

  const avaliacao = opcoes.salvo && filme.assistido
    ? `<div class="movie-rating" aria-label="Sua avaliação">${[1, 2, 3, 4, 5].map(n =>
        `<button class="${n <= minhaNota ? 'on' : ''}" data-rate-filme="${esc(filme.id)}" data-rate="${n}" aria-label="${n} estrelas"><img src="assets/icons/estrela.svg" alt=""></button>`
      ).join('')}<span>${media ? 'Média ' + media.toFixed(1) : 'Dê sua nota'}</span></div>`
    : '';

  return `
    <div class="card movie-card">
      ${poster}
      <div>
        <p class="movie-title">${esc(filme.titulo)}</p>
        <p class="movie-meta">${esc(filme.ano || 'Ano não informado')}${filme.nota ? ' · ★ ' + esc(filme.nota) : ''}</p>
        <p class="movie-overview">${esc(filme.sinopse || 'Sem sinopse disponível.')}</p>
        ${avaliacao}
        <div class="movie-actions">${acoes}</div>
      </div>
    </div>
  `;
}

function ativarAcoesFilmes() {
  document.querySelectorAll('[data-salvar-filme]').forEach(botao => {
    botao.addEventListener('click', async () => {
      const filme = resultadoFilmes[Number(botao.dataset.salvarFilme)];
      if (!filme) return;
      botao.disabled = true;
      try {
        const resposta = await chamarAppsScript({ action: 'saveMovie', filme });
        filmes.push(resposta.filme);
        renderFilmes();
        botao.textContent = 'Adicionado ✓';
      } catch (erro) {
        alert(erro.message);
        botao.disabled = false;
      }
    });
  });

  document.querySelectorAll('[data-visto-filme]').forEach(botao => {
    botao.addEventListener('click', async () => {
      const filme = filmes.find(f => String(f.id) === botao.dataset.vistoFilme);
      if (!filme) return;
      const anterior = filme.assistido;
      filme.assistido = !anterior;
      renderFilmes();
      try {
        await chamarAppsScript({ action: 'toggleMovieWatched', id: filme.id, assistido: filme.assistido });
      } catch (erro) {
        filme.assistido = anterior;
        renderFilmes();
        alert(erro.message);
      }
    });
  });

  document.querySelectorAll('[data-del-filme]').forEach(botao => {
    botao.addEventListener('click', async () => {
      if (!confirm('Excluir este filme?')) return;
      try {
        await chamarAppsScript({ action: 'deleteMovie', id: botao.dataset.delFilme });
        filmes = filmes.filter(f => String(f.id) !== botao.dataset.delFilme);
        renderFilmes();
      } catch (erro) {
        alert(erro.message);
      }
    });
  });

  document.querySelectorAll('[data-rate-filme]').forEach(botao => {
    botao.addEventListener('click', async () => {
      const filme = filmes.find(f => String(f.id) === botao.dataset.rateFilme);
      if (!filme) return;
      try {
        const resposta = await chamarAppsScript({ action: 'rateMovie', id: filme.id, nota: Number(botao.dataset.rate) });
        filme.avaliacoes = resposta.avaliacoes || {};
        renderFilmes();
      } catch (erro) {
        alert(erro.message);
      }
    });
  });
}

function renderFilmes() {
  const lista = document.getElementById('filmesLista');
  if (!lista) return;

  const pendentes = filmes.filter(f => !f.assistido);
  const assistidos = filmes.filter(f => f.assistido);

  lista.innerHTML = pendentes.length
    ? pendentes.map(f => htmlFilme(f, { salvo: true })).join('')
    : '<p class="empty">Nenhum filme na lista ainda.</p>';

  if (assistidos.length) {
    lista.innerHTML += '<p class="section-label watched-title">Já assistimos</p>';
    [5, 4, 3, 2, 1, 0].forEach(estrelas => {
      const grupo = assistidos.filter(f => {
        const notas = Object.values(f.avaliacoes || {}).map(Number).filter(n => n >= 1 && n <= 5);
        const media = notas.length ? notas.reduce((s, n) => s + n, 0) / notas.length : 0;
        return estrelas === 0 ? !media : Math.round(media) === estrelas;
      });
      if (!grupo.length) return;
      const titulo = estrelas ? estrelas + ' estrela' + (estrelas > 1 ? 's' : '') : 'Ainda sem avaliação';
      lista.innerHTML += `
        <details class="watched-group">
          <summary>${titulo} · ${grupo.length} filme${grupo.length > 1 ? 's' : ''}</summary>
          ${grupo.map(f => htmlFilme(f, { salvo: true })).join('')}
        </details>
      `;
    });
  }

  ativarAcoesFilmes();
}

function mostrarResultadosFilmes(lista) {
  resultadoFilmes = lista || [];
  const alvo = document.getElementById('filmeResultado');
  if (!alvo) return;
  alvo.innerHTML = resultadoFilmes.length
    ? resultadoFilmes.map((f, i) => htmlFilme(f, { resultado: true, indice: i })).join('')
    : '<p class="empty">Nenhum filme encontrado.</p>';
  ativarAcoesFilmes();
}

document.getElementById('btnSortearFilme')?.addEventListener('click', async () => {
  const botao = document.getElementById('btnSortearFilme');
  botao.disabled = true;
  botao.textContent = 'Sorteando...';
  try {
    const genero = document.getElementById('filmeGenero')?.value || '';
    const resposta = await chamarAppsScript({ action: 'randomMovie', genero });
    mostrarResultadosFilmes([resposta.filme]);
  } catch (erro) {
    alert(erro.message);
  } finally {
    botao.disabled = false;
    botao.textContent = 'Sortear';
  }
});

document.getElementById('btnBuscarFilme')?.addEventListener('click', buscarFilmePorNome);
document.getElementById('filmeBusca')?.addEventListener('keydown', evento => {
  if (evento.key === 'Enter') buscarFilmePorNome();
});

async function buscarFilmePorNome() {
  const busca = document.getElementById('filmeBusca')?.value.trim();
  if (!busca || busca.length < 2) return;
  const botao = document.getElementById('btnBuscarFilme');
  botao.disabled = true;
  try {
    const resposta = await chamarAppsScript({ action: 'searchMovies', busca });
    mostrarResultadosFilmes(resposta.filmes);
  } catch (erro) {
    alert(erro.message);
  } finally {
    botao.disabled = false;
  }
}
