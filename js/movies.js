let filmesAssistidos = [];
let filmesQueroAssistir = [];

function renderFilmes() {
  // Implementação simplificada; adaptar conforme sua lógica original de filmes/TMDB
  const container = document.querySelector('#tab-filmes .movie-search-card');
  if (!container) return;

  container.innerHTML = `
    <div class="movie-section-head">
      <p class="section-label">O que vamos assistir?</p>
    </div>
    <div class="movie-tools">
      <select id="filmeTipoSurpresa">
        <option value="filme">Filme</option>
        <option value="serie">Série</option>
      </select>
      <button class="primary" id="btnSurpresa">Escolhe por nós</button>
    </div>
    <div id="surpriseResult" class="surprise-result" style="margin-top:16px;"></div>
  `;

  document.getElementById('btnSurpresa')?.addEventListener('click', () => {
    const tipo = document.getElementById('filmeTipoSurpresa')?.value || 'filme';
    const resultEl = document.getElementById('surpriseResult');
    if (!resultEl) return;

    resultEl.classList.add('pop');
    resultEl.innerHTML = `
      <p class="surprise-kind">${tipo === 'filme' ? 'Filme sorteado' : 'Série sorteada'}</p>
      <p class="surprise-title">Em breve...</p>
      <p class="surprise-detail">Integração com TMDB será implementada aqui.</p>
    `;

    setTimeout(() => resultEl.classList.remove('pop'), 600);
  });
}