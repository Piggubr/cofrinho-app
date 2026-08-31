let splashTerminou = false;
let splashIniciadoEm = 0;
let loginGoogleIniciado = false;
let restauracaoSessaoEmCurso = null;

const SPLASH_DURACAO_MINIMA = 3100;

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

const CORES_PERFIL = [
  '#fae2e9',
  '#eee9fa',
  '#e3f3ef',
  '#faeadf',
  '#e4eef7',
  '#f8f0d4'
];

const FOTO_PERFIL_PADRAO = 'assets/illustrations/porquinho-perfil.svg';

function chaveFotoPerfil(email) {
  return `cofrinho_foto_perfil_${String(email || '').trim().toLowerCase()}`;
}

function aplicarCorPerfil(email, foto) {
  if (!foto) return;
  const texto = String(email || 'visitante').trim().toLowerCase();
  let codigo = 0;
  for (let i = 0; i < texto.length; i += 1) {
    codigo = ((codigo << 5) - codigo + texto.charCodeAt(i)) | 0;
  }
  const corDeFundo = CORES_PERFIL[Math.abs(codigo) % CORES_PERFIL.length];
  foto.style.setProperty('--profile-bg', corDeFundo);
}

function obterFotoPerfilSalva(email) {
  if (!email) return '';
  try {
    return localStorage.getItem(chaveFotoPerfil(email)) || '';
  } catch (erro) {
    console.warn('Não foi possível ler a foto do perfil.', erro);
    return '';
  }
}

function configurarEscolhaFotoPerfil() {
  const foto = document.getElementById('fotoPerfilV2');
  const input = document.getElementById('fotoPerfilInput');
  if (!foto || !input) return;

  const abrirSeletor = () => input.click();
  foto.addEventListener('click', abrirSeletor);
  foto.addEventListener('keydown', evento => {
    if (evento.key === 'Enter' || evento.key === ' ') {
      evento.preventDefault();
      abrirSeletor();
    }
  });

  input.addEventListener('change', () => {
    const arquivo = input.files?.[0];
    input.value = '';
    if (!arquivo) return;
    if (!arquivo.type.startsWith('image/')) {
      alert('Escolha um arquivo de imagem.');
      return;
    }
    if (arquivo.size > 40 * 1024 * 1024) {
      alert('Essa foto é muito grande. Escolha uma imagem com no máximo 40 MB.');
      return;
    }

    const email = usuarioDetalhes?.email || usuarioAtual;
    if (!email) {
      alert('Entre com Google antes de escolher sua foto.');
      return;
    }

    const imagem = new Image();
    const urlTemporaria = URL.createObjectURL(arquivo);
    imagem.onload = () => {
      const tamanhoOrigem = Math.min(imagem.naturalWidth, imagem.naturalHeight);
      const origemX = (imagem.naturalWidth - tamanhoOrigem) / 2;
      const origemY = (imagem.naturalHeight - tamanhoOrigem) / 2;
      const canvas = document.createElement('canvas');
      canvas.width = 512;
      canvas.height = 512;
      canvas.getContext('2d').drawImage(
        imagem,
        origemX, origemY, tamanhoOrigem, tamanhoOrigem,
        0, 0, canvas.width, canvas.height
      );
      const fotoReduzida = canvas.toDataURL('image/jpeg', 0.86);
      try {
        localStorage.setItem(chaveFotoPerfil(email), fotoReduzida);
        foto.src = fotoReduzida;
      } catch (erro) {
        alert('Não foi possível guardar essa foto neste aparelho. Tente uma imagem menor.');
      } finally {
        URL.revokeObjectURL(urlTemporaria);
      }
    };
    imagem.onerror = () => {
      URL.revokeObjectURL(urlTemporaria);
      alert('Não foi possível abrir essa imagem. Tente outra foto.');
    };
    imagem.src = urlTemporaria;
  });
}

function aplicarUsuarioNaInterface(detalhes) {
  if (!detalhes) return;
  usuarioDetalhes = detalhes;
  window.pigguSetLocalUser?.(detalhes.email);
  const primeiroNome = String(detalhes.apelido || detalhes.primeiro_nome || detalhes.nome || '').trim().split(/\s+/)[0];
  const saudacao = document.getElementById('saudacaoV2');
  const nome = document.getElementById('nomePerfilV2');
  const foto = document.getElementById('fotoPerfilV2');
  if (saudacao) saudacao.textContent = 'Bem-vindo(a) de volta!';
  if (nome) nome.textContent = primeiroNome || 'Visitante';
  if (foto) {
    aplicarCorPerfil(detalhes.email, foto);
    foto.referrerPolicy = 'no-referrer';
    foto.onload = () => foto.classList.add('tem-foto');
    foto.onerror = () => {
      foto.classList.remove('tem-foto');
      if (!foto.src.endsWith(FOTO_PERFIL_PADRAO)) foto.src = FOTO_PERFIL_PADRAO;
    };
    const fotoSalva = obterFotoPerfilSalva(detalhes.email);
    if (fotoSalva || detalhes.foto) {
      foto.src = fotoSalva || detalhes.foto;
    } else {
      foto.classList.remove('tem-foto');
      foto.src = FOTO_PERFIL_PADRAO;
    }
  }
  document.body.dataset.perfil = String(detalhes.role || perfilAtual || '').toLowerCase();
  window.pigguApplyLocalProfile?.();
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

  esconderSplash();
  window.pigguApplyLocalProfile?.();
  window.pigguOpenOnboarding?.(false);
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

  esconderSplash();
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

      mostrarLogin();

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

// Só esconde o splash depois que o conteúdo real (login ou dashboard) já
// está montado por trás dele, e nunca antes da duração mínima da animação —
// evita o flash de fundo vazio entre o splash sumir e o conteúdo aparecer.
function esconderSplash() {
  if (splashTerminou) {
    return;
  }

  const splash = document.getElementById('splash');

  if (!splash) {
    splashTerminou = true;
    return;
  }

  const decorrido = Date.now() - splashIniciadoEm;
  const espera = Math.max(0, SPLASH_DURACAO_MINIMA - decorrido);

  setTimeout(() => {
    splash.style.opacity = '0';

    setTimeout(() => {
      splash.remove();
      splashTerminou = true;
    }, 800);
  }, espera);
}

function modoTesteLocal() {
  try {
    return new URLSearchParams(location.search).has('semlogin');
  } catch (erro) {
    return false;
  }
}

async function iniciarAplicacao() {
  if (modoTesteLocal()) {
    window.PIGGU_DEMO_MODE = true;
    window.pigguSetLocalUser?.('demo');
    usuarioAtual = '';
    usuarioDetalhes = null;
    gastos = [];
    fotos = [];
    notas = [];
    cofrinhoMovimentos = [];
    metas = {};
    fofocoins = { saldo: 0, historico: [] };
    fofocoinsSaldo = 0;
    compras = [];
    filmes = [];
    produtos = [];
    contas = [];
    eventos = [];
    pagamentos = new Set();
    const nomeDemo = document.getElementById('nomePerfilV2');
    const fotoDemo = document.getElementById('fotoPerfilV2');
    if (nomeDemo) nomeDemo.textContent = 'Visitante';
    if (fotoDemo) fotoDemo.src = FOTO_PERFIL_PADRAO;
    console.warn('Modo de demonstração local: sessão e dados privados estão bloqueados.');
    mostrarConteudo();
    atualizarTudo();
    return;
  }

  const sessaoRestaurada = await restaurarSessaoSalva();

  if (sessaoRestaurada) {
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

    if (dados.usuario_detalhes) {
      aplicarUsuarioNaInterface(dados.usuario_detalhes);
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
    tituloMes.textContent = nomeMes(viewDate).replace(/\s+\d{4}$/, '');
  }
  requestAnimationFrame(() => window.atualizarLimiteFundoCabecalho?.());

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

  // Atualiza a cotação do euro periodicamente.
  // A função já possui throttle interno de 30 min, então chamadas a cada
  // 10 min garantem frescor sem gerar requisições desnecessárias à API.
  setInterval(() => {
    if (typeof atualizarCotacaoCambio === 'function') {
      atualizarCotacaoCambio();
    }
  }, 10 * 60 * 1000);
}

let valoresOcultos = false;
try {
  valoresOcultos = localStorage.getItem('cofrinho_ocultar_valores') === '1';
} catch (erro) {
  console.warn('Não foi possível ler a preferência de privacidade.', erro);
}

const seletorValoresPessoais = [
  '#mMedia', '#mFixos', '.goal-value', '.goal-status',
  '.coins-value', '.cofrinho-num', '#financeIncome', '#financeExpenses',
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
    valoresOcultos = !valoresOcultos;
    try {
      localStorage.setItem('cofrinho_ocultar_valores', valoresOcultos ? '1' : '0');
    } catch (erro) {
      console.warn('Não foi possível salvar a preferência de privacidade.', erro);
    }
    aplicarPrivacidade();
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

  configurarEscolhaFotoPerfil();

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

  // A inicialização continua independente da tela de entrada. Sem splash,
  // uma sessão válida abre o app e uma sessão pendente permanece no login.
  splashIniciadoEm = Date.now();
  iniciarAplicacao();
});
