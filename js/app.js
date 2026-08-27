let splashTerminou = false;
let loginGoogleIniciado = false;
let restauracaoSessaoEmCurso = null;

function limparSessaoLocal() {
  try {
    localStorage.removeItem('cofrinho_sessiontoken');
    sessionStorage.removeItem('cofrinho_sessiontoken');
  } catch (erro) {
    console.warn('Não foi possível limpar a sessão local.', erro);
  }

  sessionToken = null;
  googleIdToken = null;
}

function mostrarErroLogin(mensagem, tipo = 'erro') {
  const erro = document.getElementById('loginErro');

  if (!erro) return;

  erro.textContent = mensagem;
  erro.style.color = tipo === 'erro' ? 'var(--over)' : 'var(--muted)';
  erro.style.display = 'block';
}

function esconderErroLogin() {
  const erro = document.getElementById('loginErro');

  if (!erro) return;

  erro.textContent = '';
  erro.style.display = 'none';
}

function mostrarConteudo() {
  const gate = document.getElementById('gateGoogle');
  const conteudo = document.getElementById('conteudoPrincipal');

  if (gate) {
    gate.style.display = 'none';
  }

  if (conteudo) {
    conteudo.style.display = 'block';
  }
}

function mostrarLogin() {
  const gate = document.getElementById('gateGoogle');
  const conteudo = document.getElementById('conteudoPrincipal');

  if (conteudo) {
    conteudo.style.display = 'none';
  }

  if (gate) {
    gate.style.display = 'flex';
  }
}

async function restaurarSessaoSalva() {
  if (!sessionToken) {
    return false;
  }

  if (restauracaoSessaoEmCurso) {
    return restauracaoSessaoEmCurso;
  }

  restauracaoSessaoEmCurso = (async () => {
    try {
      mostrarErroLogin('Carregando seus dados...', 'info');

      await carregarDados();
      esconderErroLogin();
      mostrarConteudo();

      return true;
    } catch (erro) {
      console.warn('A sessão salva não pôde ser restaurada.', erro);

      limparSessaoLocal();

      if (splashTerminou) {
        mostrarLogin();
      }

      return false;
    } finally {
      restauracaoSessaoEmCurso = null;
    }
  })();

  return restauracaoSessaoEmCurso;
}

function iniciarLoginGoogle() {
  if (
    !window.google ||
    !window.google.accounts ||
    !window.google.accounts.id
  ) {
    mostrarErroLogin('Não foi possível carregar o login do Google.');
    return;
  }

  if (!GOOGLECLIENTID || GOOGLECLIENTID.includes('COLESEU')) {
    mostrarErroLogin('Falta configurar o Client ID do Google.');
    return;
  }

  const botaoGoogle = document.getElementById('googleButton');

  if (!botaoGoogle) {
    console.error('Elemento #googleButton não encontrado.');
    mostrarErroLogin('Não foi possível preparar o botão de login.');
    return;
  }

  if (loginGoogleIniciado) {
    return;
  }

  loginGoogleIniciado = true;

  google.accounts.id.initialize({
    client_id: GOOGLECLIENTID,
    auto_select: true,
    cancel_on_tap_outside: false,

    callback: async resposta => {
      googleIdToken = resposta.credential;

      try {
        mostrarErroLogin('Confirmando seu acesso...', 'info');

        const acesso = await chamarAppsScript({
          action: 'auth'
        });

        sessionToken = acesso.session_token;

        if (!sessionToken) {
          throw new Error('Não foi possível guardar seu acesso.');
        }

        try {
          localStorage.removeItem('cofrinho_sessiontoken');
          sessionStorage.removeItem('cofrinho_sessiontoken');

          const manterAcesso =
            document.getElementById('lembrarAcesso')?.checked ?? true;

          const destino = manterAcesso ? localStorage : sessionStorage;

          destino.setItem('cofrinho_sessiontoken', sessionToken);
        } catch (erro) {
          console.warn('Não foi possível salvar a sessão localmente.', erro);
        }

        mostrarErroLogin('Carregando seus dados...', 'info');

        await carregarDados();

        esconderErroLogin();
        mostrarConteudo();
      } catch (erro) {
        limparSessaoLocal();
        mostrarErroLogin(erro.message || 'Não foi possível entrar.');
      }
    }
  });

  botaoGoogle.innerHTML = '';

  google.accounts.id.renderButton(botaoGoogle, {
    theme: 'outline',
    size: 'large',
    text: 'signin_with',
    shape: 'pill',
    locale: 'pt-BR',
    width: 240
  });

  if (splashTerminou && !sessionToken) {
    google.accounts.id.prompt();
  }
}

function aguardarGoogle(tentativas = 0) {
  const googleDisponivel =
    window.google &&
    window.google.accounts &&
    window.google.accounts.id;

  if (googleDisponivel) {
    iniciarLoginGoogle();
    return;
  }

  const limiteTentativas = 80;

  if (tentativas >= limiteTentativas) {
    mostrarErroLogin(
      'Não foi possível carregar o login do Google. Atualize a página.'
    );
    return;
  }

  setTimeout(() => {
    aguardarGoogle(tentativas + 1);
  }, 100);
}

function terminarSplash() {
  const splash = document.getElementById('splash');

  if (!splash) {
    splashTerminou = true;
    iniciarAplicacao();
    return;
  }

  splash.style.opacity = '0';

  setTimeout(() => {
    splash.remove();
    splashTerminou = true;
    iniciarAplicacao();
  }, 800);
}

function modoTesteLocal() {
  try {
    if (['localhost', '127.0.0.1', '::1'].includes(location.hostname)) return true;
    if (location.protocol === 'file:') return true;
    return new URLSearchParams(location.search).has('semlogin');
  } catch (erro) {
    return false;
  }
}

async function iniciarAplicacao() {
  const sessaoRestaurada = await restaurarSessaoSalva();

  if (sessaoRestaurada) {
    return;
  }

  if (modoTesteLocal()) {
    console.warn('Modo de teste local: pulando o login do Google. Sem sessão, ações que chamam o Apps Script (salvar, carregar dados etc.) vão falhar até você logar de verdade.');
    mostrarConteudo();
    atualizarTudo();
    return;
  }

  mostrarLogin();
  aguardarGoogle();
}

async function carregarDados() {
  if (carregamentoDadosEmCurso) {
    return carregamentoDadosEmCurso;
  }

  carregamentoDadosEmCurso = (async () => {
    const dados = await chamarAppsScript({
      action: 'getData'
    });

    if (Array.isArray(dados.gastos)) {
      gastos = dados.gastos;
    }

    if (Array.isArray(dados.fotos)) {
      fotos = dados.fotos;
    }

    if (dados.metas) {
      metas = dados.metas;
    }

    if (dados.fofocoins) {
      fofocoins = dados.fofocoins;
      fofocoinsSaldo = Number(dados.fofocoins.saldo) || 0;
    }

    if (Array.isArray(dados.premios)) {
      premios = dados.premios;
    }

    if (Array.isArray(dados.lugares)) {
      lugares = dados.lugares;
    }

    if (Array.isArray(dados.notas)) {
      notas = dados.notas;
    }

    if (Array.isArray(dados.compras)) {
      compras = dados.compras;
      listaMercado = dados.compras;
    }

    if (Array.isArray(dados.filmes)) {
      filmes = dados.filmes;
    }

    if (Array.isArray(dados.produtos)) {
      produtos = dados.produtos;
    }

    if (dados.cofrinho?.depositos) {
      cofrinhoMovimentos = dados.cofrinho.depositos;
    }

    if (dados.configuracoes) {
      aplicarConfiguracoes(dados.configuracoes);
    }

    if (dados.perfil) {
      perfilAtual = dados.perfil;
      const botaoAjustar = document.getElementById('btnAjustarCoins');
      const botaoPremio = document.getElementById('btnNovoPremio');
      if (botaoAjustar) botaoAjustar.style.display = perfilAtual === 'ADMIN' ? '' : 'none';
      if (botaoPremio) botaoPremio.style.display = perfilAtual === 'ADMIN' ? '' : 'none';
    }

    if (dados.usuario) {
      usuarioAtual = dados.usuario;
    }

    sincronizarLembretesNotas();

    if (typeof nextId !== 'undefined') {
      nextId = gastos.length + 1;
    }

    atualizarTudo();
    atualizarCotacaoCambio();

    if (window.matchMedia('(min-width: 900px)').matches) {
      carregarFotosDoMes();
      carregarFotosLugares();
    }

    return dados;
  })();

  try {
    return await carregamentoDadosEmCurso;
  } finally {
    carregamentoDadosEmCurso = null;
  }
}

function atualizarTudo() {
  const tituloMes = document.getElementById('mesTitulo');

  if (tituloMes) {
    tituloMes.textContent = nomeMes(viewDate);
  }

  render();
  renderCofrinho();
  renderCalendar();
  renderFeed();
  renderNotas();
  renderCompras();
  renderFilmes();
  renderProdutos();
  renderFofocoins();
  renderPremios();
  renderLugares();
}

function iniciarRelogios() {
  if (typeof atualizarRelogios !== 'function') {
    return;
  }

  atualizarRelogios();
  setInterval(atualizarRelogios, 30000);
}

let valoresOcultos = false;
try {
  valoresOcultos = localStorage.getItem('cofrinho_ocultar_valores') === '1';
} catch (erro) {
  console.warn('Não foi possível ler a preferência de privacidade.', erro);
}

const seletorValoresPessoais = [
  '#mTotal', '#mMedia', '#mFixos', '.goal-value', '.goal-status',
  '.coins-value', '#topCoinsSaldo', '#topCoinsSaldoV2', '.cofrinho-num',
  '.budget-values', '.budget-over', '.row-value', '#insightTexto',
  '.valor-privado', '.product-memory-price'
].join(',');

const textosValoresReais = new Map();
let mascaraAgendada = false;

function mascararElemento(elemento) {
  const walker = document.createTreeWalker(elemento, NodeFilter.SHOW_TEXT);
  let texto;
  while ((texto = walker.nextNode())) {
    if (textosValoresReais.has(texto) || !/\d/.test(texto.nodeValue)) continue;
    textosValoresReais.set(texto, texto.nodeValue);
    texto.nodeValue = texto.nodeValue.replace(/\d+(?:[.,]\d+)*/g, '****');
  }
}

function mascararValoresPessoais() {
  if (!valoresOcultos) return;
  document.querySelectorAll(seletorValoresPessoais).forEach(mascararElemento);
}

function restaurarValoresPessoais() {
  textosValoresReais.forEach((original, texto) => {
    if (texto.isConnected) texto.nodeValue = original;
  });
  textosValoresReais.clear();
}

new MutationObserver(() => {
  if (!valoresOcultos || mascaraAgendada) return;
  mascaraAgendada = true;
  queueMicrotask(() => {
    mascaraAgendada = false;
    mascararValoresPessoais();
  });
}).observe(document.body, { subtree: true, childList: true, characterData: true });

function aplicarPrivacidade() {
  const botao = document.getElementById('btnPrivacidade');
  const icone = document.getElementById('iconePrivacidade');
  const botaoV2 = document.getElementById('btnPrivacidadeV2');
  const iconeV2 = document.getElementById('iconePrivacidadeV2');
  const rotulo = valoresOcultos ? 'Mostrar valores' : 'Ocultar valores';
  const src = valoresOcultos ? 'assets/icons/olho-fechado.svg' : 'assets/icons/olho.svg';

  if (botao) {
    botao.setAttribute('aria-pressed', String(valoresOcultos));
    botao.setAttribute('aria-label', rotulo);
  }
  if (icone) icone.src = src;
  if (botaoV2) botaoV2.setAttribute('aria-label', rotulo);
  if (iconeV2) iconeV2.src = src;

  if (valoresOcultos) mascararValoresPessoais();
  else restaurarValoresPessoais();
}

function configurarEventosBasicos() {
  document.getElementById('btnPrivacidade')?.addEventListener('click', () => {
    valoresOcultos = !valoresOcultos;
    try {
      localStorage.setItem('cofrinho_ocultar_valores', valoresOcultos ? '1' : '0');
    } catch (erro) {
      console.warn('Não foi possível salvar a preferência de privacidade.', erro);
    }
    aplicarPrivacidade();
  });

  document.getElementById('modalFechar')?.addEventListener('click', () => {
    document.getElementById('modalOverlay')?.classList.remove('show');
  });

  document.getElementById('modalOverlay')?.addEventListener('click', evento => {
    if (evento.target.id === 'modalOverlay') {
      evento.currentTarget.classList.remove('show');
    }
  });

  document.getElementById('btnMenuV2')?.addEventListener('click', () => {
    const painel = document.getElementById('navPanelV2');
    const botao = document.getElementById('btnMenuV2');

    if (!painel || !botao) return;

    const abrir = !painel.classList.contains('open');

    painel.classList.toggle('open', abrir);
    botao.setAttribute('aria-expanded', String(abrir));
  });

  document.getElementById('btnPrivacidadeV2')?.addEventListener('click', () => {
    document.getElementById('btnPrivacidade')?.click();
  });

  document.getElementById('btnPremiosV2')?.addEventListener('click', () => {
    if (window.matchMedia('(min-width: 900px)').matches) {
      document.getElementById('tab-premios')?.scrollIntoView({
        behavior: 'smooth',
        block: 'start'
      });
      return;
    }

    document.getElementById('btnTopResgatar')?.click();
  });

  document.getElementById('btnAbrirSurpresa')?.addEventListener('click', () => {
    abrirAba('surpresa');
  });

  document.getElementById('btnVoltarFilmes')?.addEventListener('click', () => {
    abrirAba('filmes');
  });

  document.getElementById('mesPrev')?.addEventListener('click', async () => {
    viewDate.setMonth(viewDate.getMonth() - 1);
    atualizarTudo();

    if (typeof feedEstaAberto === 'function' && feedEstaAberto()) {
      await carregarFotosDoMes();
    }
  });

  document.getElementById('mesNext')?.addEventListener('click', async () => {
    viewDate.setMonth(viewDate.getMonth() + 1);
    atualizarTudo();

    if (typeof feedEstaAberto === 'function' && feedEstaAberto()) {
      await carregarFotosDoMes();
    }
  });
}

document.addEventListener('DOMContentLoaded', () => {
  const gate = document.getElementById('gateGoogle');
  const conteudo = document.getElementById('conteudoPrincipal');

  if (gate) {
    gate.style.display = 'none';
  }

  if (conteudo) {
    conteudo.style.display = 'none';
  }

  if (typeof atualizarSaudacaoV2 === 'function') {
    atualizarSaudacaoV2();
  }

  if (typeof posicionarInformacoesCabecalho === 'function') {
    posicionarInformacoesCabecalho();

    window.addEventListener(
      'resize',
      posicionarInformacoesCabecalho,
      { passive: true }
    );
  }

  if (typeof popularSelectCategorias === 'function') {
    popularSelectCategorias(document.getElementById('novaCategoria'));
    popularSelectCategorias(document.getElementById('notaCategoria'));
  }

  if (typeof renderMarcadoresLugar === 'function') {
    renderMarcadoresLugar();
  }

  configurarEventosBasicos();
  aplicarPrivacidade();
  iniciarRelogios();

  // Mantém a duração do splash que existia no projeto original.
  setTimeout(terminarSplash, 2600);
});
