// CONTROLE FINANCEIRO DO CASAL — Google Apps Script
// Preencha somente SHEET_ID. As outras chaves ficam nas Propriedades do script.

const SHEET_ID = '1RXWT5TFArwWVaxBPV1LbHrETRDKut4Awl2XV8P-OcHo';
const SHEET_NAME = 'Gastos';
const METAS_SHEET_NAME = 'Metas';
const FOFOCOINS_SHEET_NAME = 'Fofocoins';
const PREMIOS_SHEET_NAME = 'Fofopremios';
const RESGATES_SHEET_NAME = 'Resgates';
const LUGARES_SHEET_NAME = 'Lugares';
const CONFIG_SHEET_NAME = 'Configuracoes';
const NOTAS_SHEET_NAME = 'Notas';
const COFRINHO_SHEET_NAME = 'Cofrinho';
const COMPRAS_SHEET_NAME = 'Compras';
const FILMES_SHEET_NAME = 'Filmes';
const PRODUTOS_SHEET_NAME = 'Produtos';
const GEMINI_MODEL = 'gemini-3.6-flash';

const EMAILS_AUTORIZADOS = [
  'ahcabral10@gmail.com',
  'ahcabraloffice@gmail.com',
  'beatrizvieirasouzadias@gmail.com',
  'cofrinhodosfofos@gmail.com',
  'eduardosouzapagel@gmail.com'
];

const EMAILS_ADMIN = [
  'cofrinhodosfofos@gmail.com',
  'eduardosouzapagel@gmail.com'
];

const CATEGORIAS = [
  'Aluguel', 'Alimentação', 'Transporte', 'Farmácia/Saúde',
  'Lazer', 'Assinaturas', 'Gastos Extras', 'Outros'
];

function doGet() {
  return respostaJson_({ success: true, mensagem: 'Motor do controle financeiro está funcionando.' });
}

function doPost(e) {
  try {
    if (!e || !e.postData || !e.postData.contents) {
      throw new Error('Pedido vazio.');
    }

    const body = JSON.parse(e.postData.contents);
    const usuario = validarUsuario_(body.id_token, body.session_token);
    let resultado;

    if (body.action === 'auth') {
      resultado = {
        success: true,
        usuario: usuario.email,
        perfil: usuario.perfil,
        session_token: criarSessao_(usuario)
      };
    } else if (body.action === 'getData') {
      resultado = carregarDados_(usuario.email, usuario.perfil);
    } else if (body.action === 'getExchangeRate') {
      resultado = carregarCotacaoReferencia_();
    } else if (body.action === 'searchProducts') {
      resultado = buscarProdutosMercado_(body.busca);
    } else if (body.action === 'getFeedPhoto') {
      resultado = carregarFotoFeed_(body.id);
    } else if (body.action === 'parse') {
      resultado = parseRecibo_(body.image_base64, body.mime_type);
    } else if (body.action === 'save') {
      resultado = salvarGastos_(body, usuario.email);
    } else if (body.action === 'updateExpense') {
      resultado = atualizarGasto_(body);
    } else if (body.action === 'deleteExpense') {
      resultado = excluirGasto_(body.id);
    } else if (body.action === 'saveFeed') {
      resultado = salvarFotoFeed_(body, usuario.email);
    } else if (body.action === 'updateFeed') {
      resultado = atualizarFotoFeed_(body);
    } else if (body.action === 'deleteFeed') {
      resultado = excluirFotoFeed_(body.id);
    } else if (body.action === 'setMonthlyGoal') {
      resultado = salvarMetaMensal_(body, usuario.email);
    } else if (body.action === 'adjustCoins') {
      exigirAdmin_(usuario);
      resultado = ajustarFofocoins_(body, usuario.email);
    } else if (body.action === 'savePrize') {
      exigirAdmin_(usuario);
      resultado = salvarPremio_(body, usuario.email);
    } else if (body.action === 'redeemPrize') {
      resultado = resgatarPremio_(body, usuario.email);
    } else if (body.action === 'savePlace') {
      resultado = salvarLugar_(body, usuario.email);
    } else if (body.action === 'deletePlace') {
      resultado = excluirLugar_(body.id, usuario);
    } else if (body.action === 'getPlacePhoto') {
      resultado = carregarFotoLugar_(body.id);
    } else if (body.action === 'addCategory') {
      resultado = adicionarConfiguracao_('CATEGORIA', body.nome, usuario.email);
    } else if (body.action === 'addPlaceTag') {
      resultado = adicionarConfiguracao_('MARCADOR_LUGAR', body.nome, usuario.email);
    } else if (body.action === 'saveNote') {
      resultado = salvarNota_(body, usuario.email);
    } else if (body.action === 'deleteNote') {
      resultado = excluirNota_(body.id);
    } else if (body.action === 'addDeposit') {
      resultado = adicionarDeposito_(body, usuario.email);
    } else if (body.action === 'deleteDeposit') {
      resultado = excluirDeposito_(body.id);
    } else if (body.action === 'saveShoppingItem') {
      resultado = salvarItemCompra_(body, usuario.email);
    } else if (body.action === 'toggleShoppingItem') {
      resultado = alternarItemCompra_(body.id, body.comprado);
    } else if (body.action === 'deleteShoppingItem') {
      resultado = excluirItemCompra_(body.id);
    } else if (body.action === 'randomMovie') {
      resultado = sortearFilme_(body.genero);
    } else if (body.action === 'searchMovies') {
      resultado = buscarFilmes_(body.busca);
    } else if (body.action === 'saveMovie') {
      resultado = salvarFilme_(body.filme, usuario.email);
    } else if (body.action === 'toggleMovieWatched') {
      resultado = alternarFilmeVisto_(body.id, body.assistido);
    } else if (body.action === 'rateMovie') {
      resultado = avaliarFilme_(body.id, body.nota, usuario.email);
    } else if (body.action === 'deleteMovie') {
      resultado = excluirFilme_(body.id);
    } else {
      throw new Error('Ação desconhecida.');
    }

    if (!['auth', 'getData', 'getExchangeRate', 'searchProducts', 'getFeedPhoto', 'getPlacePhoto', 'parse'].includes(body.action)) {
      limparCacheDados_();
    }

    return respostaJson_(resultado);
  } catch (erro) {
    console.error(erro);
    return respostaJson_({ success: false, erro: mensagemSegura_(erro) });
  }
}

function buscarProdutosMercado_(busca) {
  const termo = String(busca || '').trim().slice(0, 80);
  if (termo.length < 2) throw new Error('Digite pelo menos duas letras.');
  const cache = CacheService.getScriptCache();
  const chave = 'produto_busca_' + Utilities.base64EncodeWebSafe(
    Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, termo.toLowerCase())
  ).slice(0, 40);
  const salvo = cache.get(chave);
  if (salvo) return JSON.parse(salvo);

  const parametros = [
    'search_terms=' + encodeURIComponent(termo), 'search_simple=1', 'action=process', 'json=1',
    'page_size=8', 'sort_by=popularity',
    'fields=code,product_name,generic_name,brands,quantity,image_small_url,image_url'
  ].join('&');
  const resposta = UrlFetchApp.fetch('https://world.openfoodfacts.org/cgi/search.pl?' + parametros, {
    method:'get', muteHttpExceptions:true,
    headers:{ 'User-Agent':'CofrinhoDosFofos/1.0 (cofrinhodosfofos@gmail.com)', Accept:'application/json' }
  });
  if (resposta.getResponseCode() !== 200) throw new Error('A busca de produtos está indisponível agora.');
  const dados = JSON.parse(resposta.getContentText());
  const produtos = (dados.products || []).map(function(produto) {
    return {
      codigo:String(produto.code || ''),
      nome:String(produto.product_name || produto.generic_name || '').trim(),
      marca:String(produto.brands || '').split(',')[0].trim(),
      quantidade:String(produto.quantity || '').trim(),
      imagem:String(produto.image_small_url || produto.image_url || '')
    };
  }).filter(function(produto) { return produto.nome; }).slice(0, 8);
  const resultado = { success:true, produtos:produtos };
  cache.put(chave, JSON.stringify(resultado), 21600);
  return resultado;
}

function carregarCotacaoReferencia_() {
  const cache = CacheService.getScriptCache();
  const chaveCache = 'cotacao_referencia_eur_brl_v2';
  const emCache = cache.get(chaveCache);
  if (emCache) return JSON.parse(emCache);

  const propriedades = PropertiesService.getScriptProperties();
  try {
    const resposta = UrlFetchApp.fetch('https://api.frankfurter.dev/v2/rate/EUR/BRL', {
      method: 'get',
      muteHttpExceptions: true,
      headers: { Accept: 'application/json' }
    });
    if (resposta.getResponseCode() !== 200) throw new Error('Serviço de câmbio indisponível.');
    const dados = JSON.parse(resposta.getContentText());
    const taxa = Number(dados.rate);
    if (!Number.isFinite(taxa) || taxa <= 0) throw new Error('Cotação inválida.');
    const resultado = {
      success: true,
      taxa: taxa,
      data: String(dados.date || ''),
      fonte: 'referência de bancos centrais',
      estimativa: true
    };
    const serializado = JSON.stringify(resultado);
    cache.put(chaveCache, serializado, 3600);
    propriedades.setProperty('ULTIMA_COTACAO_EUR_BRL', serializado);
    return resultado;
  } catch (erro) {
    const anterior = propriedades.getProperty('ULTIMA_COTACAO_EUR_BRL');
    if (anterior) {
      const resultado = JSON.parse(anterior);
      resultado.desatualizada = true;
      return resultado;
    }
    return { success:true, taxa:6.15, fonte:'valor temporário', estimativa:true, desatualizada:true };
  }
}

function validarUsuario_(idToken, sessionToken) {
  if (sessionToken) {
    const usuarioDaSessao = validarSessao_(sessionToken);
    if (usuarioDaSessao) return usuarioDaSessao;
    throw new Error('Sua sessão venceu. Entre novamente com Google.');
  }
  if (!idToken) throw new Error('Faça login com Google para continuar.');

  const chaveToken = 'login_' + Utilities.base64EncodeWebSafe(
    Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, idToken)
  ).slice(0, 40);
  const cache = CacheService.getScriptCache();
  const usuarioEmCache = cache.get(chaveToken);
  if (usuarioEmCache) {
    const usuario = JSON.parse(usuarioEmCache);
    if (EMAILS_AUTORIZADOS.includes(usuario.email)) return usuario;
  }

  const clientId = propriedadeObrigatoria_('GOOGLE_CLIENT_ID');
  const resposta = UrlFetchApp.fetch(
    'https://oauth2.googleapis.com/tokeninfo?id_token=' + encodeURIComponent(idToken),
    { muteHttpExceptions: true }
  );

  if (resposta.getResponseCode() !== 200) {
    throw new Error('Seu login venceu ou é inválido. Entre novamente.');
  }

  const dados = JSON.parse(resposta.getContentText());
  const agora = Math.floor(Date.now() / 1000);
  const emissorValido = dados.iss === 'accounts.google.com' || dados.iss === 'https://accounts.google.com';
  const email = String(dados.email || '').trim().toLowerCase();

  if (dados.aud !== clientId || !emissorValido || Number(dados.exp) <= agora) {
    throw new Error('Login Google inválido para este site.');
  }
  if (String(dados.email_verified) !== 'true') {
    throw new Error('O e-mail Google não está verificado.');
  }
  if (!EMAILS_AUTORIZADOS.includes(email)) {
    throw new Error('Este e-mail não está autorizado.');
  }

  const usuario = {
    email: email,
    sub: dados.sub,
    perfil: EMAILS_ADMIN.includes(email) ? 'ADMIN' : 'USUARIA'
  };
  const segundosRestantes = Math.max(1, Number(dados.exp) - agora);
  cache.put(chaveToken, JSON.stringify(usuario), Math.min(300, segundosRestantes));
  return usuario;
}

function criarSessao_(usuario) {
  limparSessoesExpiradas_();
  const token = [Utilities.getUuid(), Utilities.getUuid(), Utilities.getUuid()].join('.');
  const chave = chaveSessao_(token);
  const expiraEm = Date.now() + (30 * 24 * 60 * 60 * 1000);
  PropertiesService.getScriptProperties().setProperty(chave, JSON.stringify({
    email: usuario.email,
    exp: expiraEm
  }));
  return token;
}

function validarSessao_(token) {
  const propriedades = PropertiesService.getScriptProperties();
  const chave = chaveSessao_(token);
  const valor = propriedades.getProperty(chave);
  if (!valor) return null;

  try {
    const sessao = JSON.parse(valor);
    const email = String(sessao.email || '').trim().toLowerCase();
    if (Number(sessao.exp) <= Date.now() || !EMAILS_AUTORIZADOS.includes(email)) {
      propriedades.deleteProperty(chave);
      return null;
    }
    return {
      email: email,
      perfil: EMAILS_ADMIN.includes(email) ? 'ADMIN' : 'USUARIA'
    };
  } catch (erro) {
    propriedades.deleteProperty(chave);
    return null;
  }
}

function chaveSessao_(token) {
  const hash = Utilities.computeDigest(
    Utilities.DigestAlgorithm.SHA_256,
    String(token || '')
  );
  return 'sessao_' + Utilities.base64EncodeWebSafe(hash).replace(/=+$/, '');
}

function limparSessoesExpiradas_() {
  const propriedades = PropertiesService.getScriptProperties();
  const todas = propriedades.getProperties();
  const agora = Date.now();
  Object.keys(todas).forEach(function(chave) {
    if (chave.indexOf('sessao_') !== 0) return;
    try {
      if (Number(JSON.parse(todas[chave]).exp) <= agora) propriedades.deleteProperty(chave);
    } catch (erro) {
      propriedades.deleteProperty(chave);
    }
  });
}

function exigirAdmin_(usuario) {
  if (!usuario || usuario.perfil !== 'ADMIN') {
    throw new Error('Esta ação é exclusiva do administrador.');
  }
}

function parseRecibo_(imageBase64, mimeType) {
  if (!imageBase64) throw new Error('A foto do recibo não chegou.');
  const tiposPermitidos = ['image/jpeg', 'image/png', 'image/webp', 'image/heic', 'image/heif'];
  const tipoImagem = tiposPermitidos.includes(String(mimeType || '').toLowerCase())
    ? String(mimeType).toLowerCase()
    : 'image/jpeg';
  const apiKey = propriedadeObrigatoria_('GEMINI_API_KEY');
  const hoje = Utilities.formatDate(new Date(), Session.getScriptTimeZone(), 'yyyy-MM-dd');
  const memoriaProdutos = carregarProdutos_().slice(0, 80).map(function(p) {
    return p.nome + ' = ' + p.categoria;
  });
  const prompt = [
    'Leia esta foto de um recibo ou nota fiscal.',
    'Extraia cada item e classifique-o em: ' + listarCategorias_().join(', ') + '.',
    'Valores devem ser números em euro, sem símbolo.',
    'Data no formato AAAA-MM-DD. Se não conseguir ler, use ' + hoje + '.',
    'Se não der para separar os itens, devolva um item com o total e categoria Outros.',
    'Nunca invente itens ou valores que não estejam visíveis.',
    memoriaProdutos.length ? 'Use esta memória para padronizar nomes e categorias quando houver correspondência: ' + memoriaProdutos.join('; ') : ''
  ].join('\n');
  const esquema = {
        type:'object',
        properties:{
          estabelecimento:{ type:'string' }, data:{ type:'string' },
          itens:{ type:'array', items:{
            type:'object',
            properties:{ item:{type:'string'}, categoria:{type:'string'}, valor:{type:'number'} },
            required:['item','categoria','valor']
          } }
        }, required:['estabelecimento','data','itens']
  };
  const payload = {
    contents:[{ parts:[
      { text:prompt },
      { inline_data:{ mime_type:tipoImagem, data:imageBase64 } }
    ] }],
    generationConfig:{ responseMimeType:'application/json', responseSchema:esquema }
  };
  const url = 'https://generativelanguage.googleapis.com/v1beta/models/' + GEMINI_MODEL + ':generateContent?key=' + encodeURIComponent(apiKey);
  const resposta = UrlFetchApp.fetch(url, {
    method:'post', contentType:'application/json', payload:JSON.stringify(payload), muteHttpExceptions:true
  });
  const codigoResposta = resposta.getResponseCode();
  const corpoResposta = resposta.getContentText();
  if (codigoResposta < 200 || codigoResposta >= 300) {
    console.error('Falha Gemini (' + codigoResposta + '): ' + corpoResposta.slice(0, 1500));
    if (codigoResposta === 400) throw new Error('O Gemini recusou o formato da imagem ou do pedido.');
    if (codigoResposta === 403) throw new Error('A chave do Gemini foi recusada. Confira GEMINI_API_KEY.');
    if (codigoResposta === 404) throw new Error('O modelo do Gemini não está disponível para esta chave.');
    if (codigoResposta === 429) throw new Error('A cota do Gemini acabou por enquanto. Tente novamente mais tarde.');
    throw new Error('O Gemini não conseguiu ler o recibo agora (erro ' + codigoResposta + ').');
  }
  const dados = JSON.parse(corpoResposta);
  const partes = dados.candidates && dados.candidates[0] && dados.candidates[0].content && dados.candidates[0].content.parts;
  const texto = Array.isArray(partes)
    ? partes.filter(function(parte) { return parte.text && !parte.thought; }).map(function(parte) { return parte.text; }).join('')
    : '';
  if (!texto) throw new Error('A IA não encontrou itens no recibo.');
  const lido = JSON.parse(texto);
  const itens = validarItens_(lido.itens);

  return {
    success: true,
    recibo_id: Utilities.getUuid(),
    estabelecimento: String(lido.estabelecimento || '').slice(0, 200),
    data: dataValida_(lido.data) ? lido.data : hoje,
    itens: itens
  };
}

function salvarGastos_(body, emailUsuario) {
  if (!dataValida_(body.data)) throw new Error('A data do gasto é inválida.');
  const itens = validarItens_(body.itens);
  const planilha = SpreadsheetApp.openById(SHEET_ID);
  const aba = planilha.getSheetByName(SHEET_NAME);
  if (!aba) throw new Error('A aba Gastos não existe. Rode configurarPlanilha uma vez.');

  const reciboId = String(body.recibo_id || Utilities.getUuid());
  const estabelecimento = String(body.estabelecimento || '').slice(0, 200);
  const origem = String(body.origem || 'Manual').slice(0, 30);
  const linhas = itens.map(function(item) {
    return [
      body.data, reciboId, estabelecimento, item.item, item.categoria,
      item.valor, String(item.tipo || 'Variável').slice(0, 30), origem,
      emailUsuario, new Date(), Utilities.getUuid()
    ];
  });

  const lock = LockService.getScriptLock();
  lock.waitLock(10000);
  try {
    aba.getRange(aba.getLastRow() + 1, 1, linhas.length, linhas[0].length).setValues(linhas);
  } finally {
    lock.releaseLock();
  }

  atualizarMemoriaProdutos_(itens, body.data, emailUsuario);

  return { success: true, itens_salvos: linhas.length, registro_ids: linhas.map(function(linha) { return String(linha[10]); }), usuario: emailUsuario };
}

function validarItens_(itens) {
  if (!Array.isArray(itens) || itens.length < 1 || itens.length > 100) {
    throw new Error('A lista de gastos está vazia ou é grande demais.');
  }
  const categoriasPermitidas = listarCategorias_();
  return itens.map(function(item) {
    const nome = String(item.item || '').trim().slice(0, 200);
    const categoria = categoriasPermitidas.includes(item.categoria) ? item.categoria : 'Outros';
    const valor = Number(item.valor);
    if (!nome || !Number.isFinite(valor) || valor < 0 || valor > 1000000) {
      throw new Error('Existe um item ou valor inválido.');
    }
    return { item: nome, categoria: categoria, valor: valor, tipo: item.tipo };
  });
}

function configurarPlanilha() {
  const planilha = SpreadsheetApp.openById(SHEET_ID);
  const aba = planilha.getSheetByName(SHEET_NAME) || planilha.insertSheet(SHEET_NAME);
  const cabecalhos = [
    'Data', 'Recibo_ID', 'Estabelecimento', 'Item', 'Categoria',
    'Valor (€)', 'Tipo', 'Origem', 'Usuário', 'Registrado_em', 'Registro_ID'
  ];
  aba.getRange(1, 1, 1, cabecalhos.length).setValues([cabecalhos]);
  aba.setFrozenRows(1);
  aba.getRange('A:A').setNumberFormat('yyyy-mm-dd');
  aba.getRange('F:F').setNumberFormat('€0.00');
  garantirIdsGastos_(aba);
  obterAbaFeed_();
  obterAbaMetas_();
  obterAbaFofocoins_();
  obterAbaPremios_();
  obterAbaResgates_();
  obterAbaLugares_();
  obterAbaConfiguracoes_();
  obterAbaNotas_();
  obterAbaCofrinho_();
  obterAbaCompras_();
  obterAbaFilmes_();
  obterAbaProdutos_();
  obterPastaFeedRaiz_();
}

function carregarDados_(emailUsuario, perfilUsuario) {
  const chaveCache = 'dados_' + Utilities.base64EncodeWebSafe(emailUsuario).slice(0, 80);
  const cache = CacheService.getScriptCache();
  try {
    const salvo = cache.get(chaveCache);
    if (salvo) return JSON.parse(salvo);
  } catch (erro) { console.warn(erro); }

  const aba = SpreadsheetApp.openById(SHEET_ID).getSheetByName(SHEET_NAME);
  const gastos = [];
  if (aba && aba.getLastRow() > 1) {
    garantirIdsGastos_(aba);
    const valores = aba.getRange(2, 1, aba.getLastRow() - 1, 11).getValues();
    valores.forEach(function(linha) {
      if (!linha[0] || !linha[3]) return;
      const data = linha[0] instanceof Date
        ? Utilities.formatDate(linha[0], Session.getScriptTimeZone(), 'yyyy-MM-dd')
        : String(linha[0]).slice(0, 10);
      gastos.push({
        id: String(linha[10]), data: data, estabelecimento: String(linha[2] || ''),
        item: String(linha[3]), categoria: String(linha[4] || 'Outros'),
        valor: Number(linha[5]) || 0, tipo: String(linha[6] || 'Variável'),
        origem: String(linha[7] || ''), usuario: String(linha[8] || ''),
        registrado_em: linha[9] instanceof Date ? linha[9].toISOString() : String(linha[9] || '')
      });
    });
  }

  const fotos = [];
  const feed = obterAbaFeed_();
  if (feed.getLastRow() > 1) {
    feed.getRange(2, 1, feed.getLastRow() - 1, 6).getValues().forEach(function(linha) {
      const mesKey = linha[1] instanceof Date
        ? Utilities.formatDate(linha[1], Session.getScriptTimeZone(), 'yyyy-MM')
        : String(linha[1] || '').trim().slice(0, 7);
      fotos.push({
        id: String(linha[0]), mesKey: mesKey, legenda: String(linha[3] || ''),
        usuario: String(linha[4] || '')
      });
    });
  }
  const resultado = {
    success: true,
    gastos: gastos,
    fotos: fotos,
    metas: carregarMetas_(),
    fofocoins: carregarFofocoins_(),
    premios: carregarPremios_(),
    lugares: carregarLugares_(),
    notas: carregarNotas_(),
    cofrinho: carregarCofrinho_(gastos),
    compras: carregarCompras_(),
    filmes: carregarFilmes_(),
    produtos: carregarProdutos_(),
    configuracoes: carregarConfiguracoes_(),
    usuario: emailUsuario,
    perfil: perfilUsuario || 'USUARIA'
  };
  try { cache.put(chaveCache, JSON.stringify(resultado), 300); } catch (erro) { console.warn(erro); }
  return resultado;
}

function obterAbaCofrinho_() {
  const planilha = SpreadsheetApp.openById(SHEET_ID);
  let aba = planilha.getSheetByName(COFRINHO_SHEET_NAME);
  if (!aba) {
    aba = planilha.insertSheet(COFRINHO_SHEET_NAME);
    aba.appendRow(['ID', 'Data', 'Valor', 'Usuario', 'Registrado_em']);
    aba.setFrozenRows(1);
    aba.getRange('B:B').setNumberFormat('yyyy-mm-dd');
    aba.getRange('C:C').setNumberFormat('€0.00');
  }
  return aba;
}

function carregarCofrinho_(gastos) {
  const aba = obterAbaCofrinho_();
  const depositos = [];
  if (aba.getLastRow() > 1) {
    aba.getRange(2, 1, aba.getLastRow() - 1, 5).getValues().forEach(function(linha) {
      if (!linha[0]) return;
      const data = linha[1] instanceof Date
        ? Utilities.formatDate(linha[1], Session.getScriptTimeZone(), 'yyyy-MM-dd')
        : String(linha[1] || '').slice(0, 10);
      depositos.push({
        id: String(linha[0]), data: data, valor: Number(linha[2]) || 0,
        registrado_em: linha[4] instanceof Date ? linha[4].toISOString() : String(linha[4] || '')
      });
    });
  }
  const totalDepositado = depositos.reduce(function(total, item) { return total + item.valor; }, 0);
  const inicio = depositos.length
    ? depositos.map(function(item) { return item.registrado_em; }).filter(Boolean).sort()[0]
    : '';
  const totalGasto = inicio ? (gastos || []).filter(function(item) {
    return item.registrado_em && item.registrado_em >= inicio;
  }).reduce(function(total, item) { return total + (Number(item.valor) || 0); }, 0) : 0;
  return { depositos: depositos, total_depositos: totalDepositado, total_gastos: totalGasto, saldo: totalDepositado - totalGasto };
}

function adicionarDeposito_(body, emailUsuario) {
  const valor = Number(body.valor);
  if (!Number.isFinite(valor) || valor <= 0 || valor > 10000000) throw new Error('Digite um valor de depósito válido.');
  const data = dataValida_(body.data) ? body.data : Utilities.formatDate(new Date(), Session.getScriptTimeZone(), 'yyyy-MM-dd');
  const id = Utilities.getUuid();
  const registradoEm = new Date();
  obterAbaCofrinho_().appendRow([id, data, valor, emailUsuario, registradoEm]);
  return { success: true, deposito: { id: id, data: data, valor: valor, registrado_em: registradoEm.toISOString() } };
}

function excluirDeposito_(id) {
  const aba = obterAbaCofrinho_();
  const linha = localizarLinhaPorId_(aba, id, 1);
  if (!linha) throw new Error('Depósito não encontrado.');
  aba.deleteRow(linha);
  return { success: true };
}

function obterAbaCompras_() {
  const planilha = SpreadsheetApp.openById(SHEET_ID);
  let aba = planilha.getSheetByName(COMPRAS_SHEET_NAME);
  if (!aba) {
    aba = planilha.insertSheet(COMPRAS_SHEET_NAME);
    aba.appendRow(['ID', 'Item', 'Quantidade', 'Lista', 'Comprado', 'Usuario', 'Criado_em']);
    aba.setFrozenRows(1);
  }
  if (aba.getLastColumn() < 10) {
    aba.getRange(1, 8, 1, 3).setValues([['Marca', 'Imagem', 'Codigo']]);
  }
  return aba;
}

function carregarCompras_() {
  const aba = obterAbaCompras_();
  if (aba.getLastRow() <= 1) return [];
  return aba.getRange(2, 1, aba.getLastRow() - 1, 10).getValues().filter(function(linha) {
    return linha[0] && linha[1];
  }).map(function(linha) {
    return {
      id: String(linha[0]), item: String(linha[1]), quantidade: String(linha[2] || ''),
      lista: String(linha[3] || 'Compras'), comprado: linha[4] === true,
      usuario: String(linha[5] || ''), marca:String(linha[7] || ''),
      imagem:String(linha[8] || ''), codigo:String(linha[9] || '')
    };
  });
}

function salvarItemCompra_(body, emailUsuario) {
  const item = String(body.item || '').trim().slice(0, 150);
  const quantidade = String(body.quantidade || '').trim().slice(0, 50);
  const lista = body.lista === 'Desejos' ? 'Desejos' : 'Compras';
  const marca = String(body.marca || '').trim().slice(0, 100);
  const imagem = /^https:\/\//.test(String(body.imagem || '')) ? String(body.imagem).slice(0, 1000) : '';
  const codigo = String(body.codigo || '').replace(/\D/g, '').slice(0, 20);
  if (!item) throw new Error('Digite o que deseja adicionar.');
  const id = Utilities.getUuid();
  obterAbaCompras_().appendRow([id, item, quantidade, lista, false, emailUsuario, new Date(), marca, imagem, codigo]);
  return { success: true, item: { id:id, item:item, quantidade:quantidade, lista:lista, comprado:false, usuario:emailUsuario, marca:marca, imagem:imagem, codigo:codigo } };
}

function alternarItemCompra_(id, comprado) {
  const aba = obterAbaCompras_();
  const linha = localizarLinhaPorId_(aba, id, 1);
  if (!linha) throw new Error('Item não encontrado.');
  aba.getRange(linha, 5).setValue(comprado === true);
  return { success: true };
}

function excluirItemCompra_(id) {
  const aba = obterAbaCompras_();
  const linha = localizarLinhaPorId_(aba, id, 1);
  if (!linha) throw new Error('Item não encontrado.');
  aba.deleteRow(linha);
  return { success: true };
}

function obterAbaFilmes_() {
  const planilha = SpreadsheetApp.openById(SHEET_ID);
  let aba = planilha.getSheetByName(FILMES_SHEET_NAME);
  if (!aba) {
    aba = planilha.insertSheet(FILMES_SHEET_NAME);
    aba.appendRow(['ID', 'TMDB_ID', 'Titulo', 'Ano', 'Poster', 'Nota_TMDB', 'Sinopse', 'Assistido', 'Usuario', 'Criado_em', 'Avaliacoes_JSON']);
    aba.setFrozenRows(1);
  }
  return aba;
}

function carregarFilmes_() {
  const aba = obterAbaFilmes_();
  if (aba.getLastRow() <= 1) return [];
  if (!aba.getRange(1, 11).getValue()) aba.getRange(1, 11).setValue('Avaliacoes_JSON');
  return aba.getRange(2, 1, aba.getLastRow() - 1, 11).getValues().filter(function(linha) {
    return linha[0] && linha[2];
  }).map(function(linha) {
    return {
      id:String(linha[0]), tmdb_id:String(linha[1]), titulo:String(linha[2]), ano:String(linha[3] || ''),
      poster:String(linha[4] || ''), nota:Number(linha[5]) || 0, sinopse:String(linha[6] || ''),
      assistido:linha[7] === true, usuario:String(linha[8] || ''), avaliacoes:parseJsonSeguro_(linha[10], {})
    };
  });
}

function consultarTmdb_(caminho, parametros) {
  let credencial = propriedadeObrigatoria_('TMDB_READ_TOKEN').trim().replace(/^Bearer\s+/i, '').replace(/^['"]|['"]$/g, '');
  const params = Object.assign({}, parametros || {});
  const pareceTokenLongo = credencial.indexOf('.') !== -1 || credencial.indexOf('eyJ') === 0;
  if (!pareceTokenLongo) params.api_key = credencial;
  const query = Object.keys(params).map(function(chave) {
    return encodeURIComponent(chave) + '=' + encodeURIComponent(params[chave]);
  }).join('&');
  const opcoes = { muteHttpExceptions:true, headers:{ accept:'application/json' } };
  if (pareceTokenLongo) opcoes.headers.Authorization = 'Bearer ' + credencial;
  const resposta = UrlFetchApp.fetch('https://api.themoviedb.org/3/' + caminho + (query ? '?' + query : ''), {
    headers:opcoes.headers,
    muteHttpExceptions:opcoes.muteHttpExceptions
  });
  if (resposta.getResponseCode() === 401) throw new Error('A credencial do TMDB foi recusada. Confira a propriedade TMDB_READ_TOKEN.');
  if (resposta.getResponseCode() !== 200) throw new Error('O TMDB não respondeu corretamente (erro ' + resposta.getResponseCode() + ').');
  return JSON.parse(resposta.getContentText());
}

function normalizarFilmeTmdb_(filme) {
  return {
    tmdb_id:String(filme.id), titulo:String(filme.title || ''),
    ano:String(filme.release_date || '').slice(0, 4),
    poster:filme.poster_path ? 'https://image.tmdb.org/t/p/w500' + filme.poster_path : '',
    nota:Math.round((Number(filme.vote_average) || 0) * 10) / 10,
    sinopse:String(filme.overview || '').slice(0, 1000)
  };
}

function sortearFilme_(genero) {
  const parametros = {
    language:'pt-BR', region:'PT', include_adult:'false', include_video:'false',
    sort_by:'popularity.desc', 'vote_count.gte':100, page:Math.floor(Math.random() * 20) + 1
  };
  if (/^\d+$/.test(String(genero || ''))) parametros.with_genres = genero;
  const dados = consultarTmdb_('discover/movie', parametros);
  const resultados = (dados.results || []).filter(function(item) { return item.title; });
  if (!resultados.length) throw new Error('Nenhum filme encontrado com esse filtro.');
  return { success:true, filme:normalizarFilmeTmdb_(resultados[Math.floor(Math.random() * resultados.length)]) };
}

function buscarFilmes_(busca) {
  const termo = String(busca || '').trim().slice(0, 100);
  if (termo.length < 2) throw new Error('Digite pelo menos duas letras.');
  const dados = consultarTmdb_('search/movie', { query:termo, language:'pt-BR', region:'PT', include_adult:'false', page:1 });
  return { success:true, filmes:(dados.results || []).slice(0, 8).map(normalizarFilmeTmdb_) };
}

function salvarFilme_(filme, emailUsuario) {
  filme = filme || {};
  const titulo = String(filme.titulo || '').trim().slice(0, 200);
  if (!titulo) throw new Error('O filme não chegou corretamente.');
  const aba = obterAbaFilmes_();
  const tmdbId = String(filme.tmdb_id || '');
  if (tmdbId && carregarFilmes_().some(function(item) { return item.tmdb_id === tmdbId; })) throw new Error('Esse filme já está na lista.');
  const id = Utilities.getUuid();
  aba.appendRow([id, tmdbId, titulo, String(filme.ano || ''), String(filme.poster || ''), Number(filme.nota) || 0, String(filme.sinopse || '').slice(0, 1000), false, emailUsuario, new Date(), '{}']);
  return { success:true, filme:{ id:id, tmdb_id:tmdbId, titulo:titulo, ano:String(filme.ano || ''), poster:String(filme.poster || ''), nota:Number(filme.nota) || 0, sinopse:String(filme.sinopse || ''), assistido:false, usuario:emailUsuario, avaliacoes:{} } };
}

function alternarFilmeVisto_(id, assistido) {
  const aba = obterAbaFilmes_();
  const linha = localizarLinhaPorId_(aba, id, 1);
  if (!linha) throw new Error('Filme não encontrado.');
  aba.getRange(linha, 8).setValue(assistido === true);
  return { success:true };
}

function avaliarFilme_(id, nota, emailUsuario) {
  nota = Number(nota);
  if (!Number.isInteger(nota) || nota < 1 || nota > 5) throw new Error('Escolha uma nota de 1 a 5.');
  const aba = obterAbaFilmes_();
  const linha = localizarLinhaPorId_(aba, id, 1);
  if (!linha) throw new Error('Filme não encontrado.');
  const avaliacoes = parseJsonSeguro_(aba.getRange(linha, 11).getValue(), {});
  avaliacoes[emailUsuario] = nota;
  aba.getRange(linha, 11).setValue(JSON.stringify(avaliacoes));
  return { success:true, avaliacoes:avaliacoes };
}

function parseJsonSeguro_(valor, padrao) {
  try { return valor ? JSON.parse(String(valor)) : padrao; } catch (erro) { return padrao; }
}

function excluirFilme_(id) {
  const aba = obterAbaFilmes_();
  const linha = localizarLinhaPorId_(aba, id, 1);
  if (!linha) throw new Error('Filme não encontrado.');
  aba.deleteRow(linha);
  return { success:true };
}

function limparCacheDados_() {
  const cache = CacheService.getScriptCache();
  EMAILS_AUTORIZADOS.forEach(function(email) {
    const chave = 'dados_' + Utilities.base64EncodeWebSafe(email).slice(0, 80);
    cache.remove(chave);
  });
}

function obterAbaProdutos_() {
  const planilha = SpreadsheetApp.openById(SHEET_ID);
  let aba = planilha.getSheetByName(PRODUTOS_SHEET_NAME);
  if (!aba) {
    aba = planilha.insertSheet(PRODUTOS_SHEET_NAME);
    aba.appendRow(['Chave', 'Nome_padrao', 'Categoria', 'Ultimo_preco', 'Menor_preco', 'Maior_preco', 'Preco_medio', 'Compras', 'Ultima_compra', 'Usuario', 'Ultima_variacao']);
    aba.setFrozenRows(1);
    aba.getRange('D:G').setNumberFormat('€0.00');
    aba.getRange('K:K').setNumberFormat('€0.00');
  }
  return aba;
}

function chaveProduto_(nome) {
  return String(nome || '').toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '')
    .replace(/\([^)]*\)/g, ' ').replace(/\b\d+[.,]?\d*\s*(kg|g|mg|l|ml|cl|un|uni|unidades?)\b/g, ' ')
    .replace(/\b(de|da|do|das|dos|um|uma)\b/g, ' ').replace(/[^a-z0-9]+/g, ' ').trim();
}

function carregarProdutos_() {
  const aba = obterAbaProdutos_();
  if (aba.getLastRow() <= 1) return [];
  return aba.getRange(2, 1, aba.getLastRow() - 1, 11).getValues().filter(function(linha) {
    return linha[0] && linha[1];
  }).map(function(linha) {
    const data = linha[8] instanceof Date
      ? Utilities.formatDate(linha[8], Session.getScriptTimeZone(), 'yyyy-MM-dd')
      : String(linha[8] || '').slice(0, 10);
    return {
      chave:String(linha[0]), nome:String(linha[1]), categoria:String(linha[2] || 'Outros'),
      ultimo:Number(linha[3]) || 0, menor:Number(linha[4]) || 0, maior:Number(linha[5]) || 0,
      media:Number(linha[6]) || 0, compras:Number(linha[7]) || 0,
      data:data, variacao:Number(linha[10]) || 0
    };
  }).sort(function(a,b) { return b.compras - a.compras; });
}

function atualizarMemoriaProdutos_(itens, data, emailUsuario) {
  const aba = obterAbaProdutos_();
  const lock = LockService.getScriptLock();
  lock.waitLock(10000);
  try {
    const linhas = aba.getLastRow() > 1 ? aba.getRange(2, 1, aba.getLastRow() - 1, 11).getValues() : [];
    const indice = {};
    linhas.forEach(function(linha, i) { indice[String(linha[0])] = i; });
    itens.forEach(function(item) {
      const chave = chaveProduto_(item.item);
      if (!chave) return;
      const valor = Number(item.valor) || 0;
      if (Object.prototype.hasOwnProperty.call(indice, chave)) {
        const i = indice[chave];
        const linha = linhas[i];
        const anterior = Number(linha[3]) || valor;
        const compras = (Number(linha[7]) || 0) + 1;
        const media = (((Number(linha[6]) || anterior) * (compras - 1)) + valor) / compras;
        linhas[i] = [chave, String(item.item), String(item.categoria), valor,
          Math.min(Number(linha[4]) || valor, valor), Math.max(Number(linha[5]) || valor, valor),
          media, compras, data, emailUsuario, valor - anterior];
      } else {
        indice[chave] = linhas.length;
        linhas.push([chave, String(item.item), String(item.categoria), valor, valor, valor, valor, 1, data, emailUsuario, 0]);
      }
    });
    if (linhas.length) aba.getRange(2, 1, linhas.length, 11).setValues(linhas);
  } finally { lock.releaseLock(); }
}

function obterAbaMetas_() {
  const planilha = SpreadsheetApp.openById(SHEET_ID);
  let aba = planilha.getSheetByName(METAS_SHEET_NAME);
  if (!aba) {
    aba = planilha.insertSheet(METAS_SHEET_NAME);
    aba.appendRow(['Mes', 'Limite', 'Usuario', 'Atualizado_em']);
    aba.setFrozenRows(1);
    aba.getRange('A:A').setNumberFormat('@');
    aba.getRange('B:B').setNumberFormat('€0.00');
  }
  return aba;
}

function carregarMetas_() {
  const aba = obterAbaMetas_();
  const metas = {};
  if (aba.getLastRow() <= 1) return metas;
  aba.getRange(2, 1, aba.getLastRow() - 1, 2).getValues().forEach(function(linha) {
    const mes = linha[0] instanceof Date
      ? Utilities.formatDate(linha[0], Session.getScriptTimeZone(), 'yyyy-MM')
      : String(linha[0] || '').trim().slice(0, 7);
    const limite = Number(linha[1]);
    if (/^\d{4}-\d{2}$/.test(mes) && Number.isFinite(limite) && limite > 0) metas[mes] = limite;
  });
  return metas;
}

function salvarMetaMensal_(body, emailUsuario) {
  const mes = String(body.mes || '').trim();
  const limite = Number(body.limite);
  if (!/^\d{4}-\d{2}$/.test(mes)) throw new Error('Mês inválido.');
  if (!Number.isFinite(limite) || limite <= 0 || limite > 1000000) {
    throw new Error('Digite um valor válido para a meta.');
  }

  const bloqueio = LockService.getScriptLock();
  bloqueio.waitLock(10000);
  try {
    const aba = obterAbaMetas_();
    let linhaEncontrada = 0;
    if (aba.getLastRow() > 1) {
      const meses = aba.getRange(2, 1, aba.getLastRow() - 1, 1).getDisplayValues();
      for (let i = 0; i < meses.length; i++) {
        if (String(meses[i][0]).trim().slice(0, 7) === mes) { linhaEncontrada = i + 2; break; }
      }
    }
    const valores = [mes, limite, emailUsuario, new Date()];
    if (linhaEncontrada) aba.getRange(linhaEncontrada, 1, 1, 4).setValues([valores]);
    else aba.appendRow(valores);
    return { success: true, mes: mes, limite: limite };
  } finally {
    bloqueio.releaseLock();
  }
}

function obterAbaFofocoins_() {
  const planilha = SpreadsheetApp.openById(SHEET_ID);
  let aba = planilha.getSheetByName(FOFOCOINS_SHEET_NAME);
  if (!aba) {
    aba = planilha.insertSheet(FOFOCOINS_SHEET_NAME);
    aba.appendRow(['ID', 'Data', 'Valor', 'Motivo', 'Usuario', 'Responsavel']);
    aba.setFrozenRows(1);
    aba.getRange('B:B').setNumberFormat('yyyy-mm-dd hh:mm');
    aba.getRange('C:C').setNumberFormat('0');
  }
  return aba;
}

function carregarFofocoins_() {
  const aba = obterAbaFofocoins_();
  const historico = [];
  let saldo = 0;
  if (aba.getLastRow() > 1) {
    aba.getRange(2, 1, aba.getLastRow() - 1, 6).getValues().forEach(function(linha) {
      const valor = Number(linha[2]) || 0;
      saldo += valor;
      historico.push({
        id: String(linha[0]),
        data: linha[1] instanceof Date
          ? Utilities.formatDate(linha[1], Session.getScriptTimeZone(), 'yyyy-MM-dd HH:mm')
          : String(linha[1] || ''),
        valor: valor,
        motivo: String(linha[3] || ''),
        usuario: String(linha[4] || '')
      });
    });
  }
  historico.reverse();
  return { saldo: saldo, historico: historico.slice(0, 100) };
}

function ajustarFofocoins_(body, emailResponsavel) {
  const valor = Math.trunc(Number(body.valor));
  const motivo = String(body.motivo || '').trim().slice(0, 200);
  if (!Number.isFinite(valor) || valor === 0 || Math.abs(valor) > 1000000) {
    throw new Error('Digite uma quantidade válida de Fofocoins.');
  }
  if (!motivo) throw new Error('Explique o motivo do ajuste.');

  const bloqueio = LockService.getScriptLock();
  bloqueio.waitLock(10000);
  try {
    const atual = carregarFofocoins_().saldo;
    if (atual + valor < 0) throw new Error('O saldo não pode ficar negativo.');
    const id = Utilities.getUuid();
    obterAbaFofocoins_().appendRow([id, new Date(), valor, motivo, 'USUARIA', emailResponsavel]);
    return { success: true, saldo: atual + valor };
  } finally {
    bloqueio.releaseLock();
  }
}

function obterAbaPremios_() {
  const planilha = SpreadsheetApp.openById(SHEET_ID);
  let aba = planilha.getSheetByName(PREMIOS_SHEET_NAME);
  if (!aba) {
    aba = planilha.insertSheet(PREMIOS_SHEET_NAME);
    aba.appendRow(['ID', 'Nome', 'Descricao', 'Preco', 'Ativo', 'Atualizado_por', 'Atualizado_em']);
    aba.setFrozenRows(1);
    aba.getRange('D:D').setNumberFormat('0');
  }
  return aba;
}

function obterAbaResgates_() {
  const planilha = SpreadsheetApp.openById(SHEET_ID);
  let aba = planilha.getSheetByName(RESGATES_SHEET_NAME);
  if (!aba) {
    aba = planilha.insertSheet(RESGATES_SHEET_NAME);
    aba.appendRow(['ID', 'Data', 'Premio_ID', 'Premio', 'Preco', 'Usuario', 'Status']);
    aba.setFrozenRows(1);
    aba.getRange('B:B').setNumberFormat('yyyy-mm-dd hh:mm');
  }
  return aba;
}

function carregarPremios_() {
  const aba = obterAbaPremios_();
  const premios = [];
  if (aba.getLastRow() <= 1) return premios;
  aba.getRange(2, 1, aba.getLastRow() - 1, 7).getValues().forEach(function(linha) {
    if (!linha[0] || !linha[1]) return;
    premios.push({
      id: String(linha[0]), nome: String(linha[1]), descricao: String(linha[2] || ''),
      preco: Math.trunc(Number(linha[3]) || 0), ativo: linha[4] === true || String(linha[4]).toLowerCase() === 'true'
    });
  });
  return premios;
}

function salvarPremio_(body, emailResponsavel) {
  const nome = String(body.nome || '').trim().slice(0, 100);
  const descricao = String(body.descricao || '').trim().slice(0, 300);
  const preco = Math.trunc(Number(body.preco));
  const ativo = body.ativo !== false;
  if (!nome) throw new Error('Digite o nome do prêmio.');
  if (!Number.isFinite(preco) || preco <= 0 || preco > 10000000) throw new Error('Digite um preço válido.');
  const aba = obterAbaPremios_();
  const id = String(body.id || Utilities.getUuid());
  const linha = body.id ? localizarLinhaPorId_(aba, id, 1) : 0;
  const valores = [id, nome, descricao, preco, ativo, emailResponsavel, new Date()];
  if (linha) aba.getRange(linha, 1, 1, 7).setValues([valores]);
  else aba.appendRow(valores);
  return { success: true, premio: { id: id, nome: nome, descricao: descricao, preco: preco, ativo: ativo } };
}

function resgatarPremio_(body, emailUsuario) {
  const bloqueio = LockService.getScriptLock();
  bloqueio.waitLock(10000);
  try {
    const abaPremios = obterAbaPremios_();
    const linha = localizarLinhaPorId_(abaPremios, body.id, 1);
    if (!linha) throw new Error('Prêmio não encontrado.');
    const premio = abaPremios.getRange(linha, 1, 1, 5).getValues()[0];
    const ativo = premio[4] === true || String(premio[4]).toLowerCase() === 'true';
    const preco = Math.trunc(Number(premio[3]) || 0);
    if (!ativo) throw new Error('Este prêmio não está disponível.');
    const saldo = carregarFofocoins_().saldo;
    if (saldo < preco) throw new Error('Você ainda não possui Fofocoins suficientes.');
    const idResgate = Utilities.getUuid();
    obterAbaFofocoins_().appendRow([Utilities.getUuid(), new Date(), -preco, 'Resgate: ' + String(premio[1]), emailUsuario, 'SISTEMA']);
    obterAbaResgates_().appendRow([idResgate, new Date(), String(premio[0]), String(premio[1]), preco, emailUsuario, 'Resgatado']);
    return { success: true, saldo: saldo - preco, resgate: idResgate, premio: String(premio[1]) };
  } finally {
    bloqueio.releaseLock();
  }
}

function obterAbaConfiguracoes_() {
  const planilha = SpreadsheetApp.openById(SHEET_ID);
  let aba = planilha.getSheetByName(CONFIG_SHEET_NAME);
  if (!aba) {
    aba = planilha.insertSheet(CONFIG_SHEET_NAME);
    aba.appendRow(['Tipo', 'Nome', 'Criado_por', 'Criado_em']);
    aba.setFrozenRows(1);
  }
  return aba;
}

function carregarConfiguracoes_() {
  const resultado = { categorias: [], marcadoresLugar: [] };
  const aba = obterAbaConfiguracoes_();
  if (aba.getLastRow() <= 1) return resultado;
  aba.getRange(2, 1, aba.getLastRow() - 1, 2).getDisplayValues().forEach(function(linha) {
    if (linha[0] === 'CATEGORIA') resultado.categorias.push(linha[1]);
    if (linha[0] === 'MARCADOR_LUGAR') resultado.marcadoresLugar.push(linha[1]);
  });
  return resultado;
}

function listarCategorias_() {
  return CATEGORIAS.concat(carregarConfiguracoes_().categorias).filter(function(valor, indice, lista) {
    return lista.indexOf(valor) === indice;
  });
}

function adicionarConfiguracao_(tipo, nomeBruto, emailUsuario) {
  const nome = String(nomeBruto || '').trim().replace(/\s+/g, ' ').slice(0, 50);
  if (!nome || nome.length < 2) throw new Error('Digite um nome válido.');
  const config = carregarConfiguracoes_();
  const existentes = tipo === 'CATEGORIA' ? listarCategorias_() : ['Favorito', 'Melhor custo-beneficio', 'Voltaria', 'Nao voltaria'].concat(config.marcadoresLugar);
  if (existentes.some(function(item) { return item.toLowerCase() === nome.toLowerCase(); })) throw new Error('Essa opção já existe.');
  obterAbaConfiguracoes_().appendRow([tipo, nome, emailUsuario, new Date()]);
  return { success:true, nome:nome };
}

function obterAbaLugares_() {
  const planilha = SpreadsheetApp.openById(SHEET_ID);
  let aba = planilha.getSheetByName(LUGARES_SHEET_NAME);
  if (!aba) {
    aba = planilha.insertSheet(LUGARES_SHEET_NAME);
    aba.appendRow(['ID', 'Nome', 'Categoria', 'Localizacao', 'Nota', 'Comentario', 'Data', 'Marcacoes', 'Arquivo_ID', 'Usuario', 'Registrado_em', 'Valor']);
    aba.setFrozenRows(1);
    aba.getRange('G:G').setNumberFormat('yyyy-mm-dd');
  }
  if (aba.getLastColumn() < 12) aba.getRange(1, 12).setValue('Valor');
  return aba;
}

function carregarLugares_() {
  const aba = obterAbaLugares_();
  const lugares = [];
  if (aba.getLastRow() <= 1) return lugares;
  aba.getRange(2, 1, aba.getLastRow() - 1, 12).getValues().forEach(function(linha) {
    if (!linha[0] || !linha[1]) return;
    const data = linha[6] instanceof Date
      ? Utilities.formatDate(linha[6], Session.getScriptTimeZone(), 'yyyy-MM-dd')
      : String(linha[6] || '').slice(0, 10);
    lugares.push({ id:String(linha[0]), nome:String(linha[1]), categoria:String(linha[2] || 'Outros'), localizacao:String(linha[3] || ''), nota:Number(linha[4]) || 0, comentario:String(linha[5] || ''), data:data, marcacoes:String(linha[7] || '').split('|').filter(Boolean), temFoto:Boolean(linha[8]), usuario:String(linha[9] || ''), valor:Number(linha[11]) || 0 });
  });
  lugares.sort(function(a,b) { return b.data.localeCompare(a.data); });
  return lugares;
}

function salvarLugar_(body, emailUsuario) {
  const nome = String(body.nome || '').trim().slice(0, 120);
  const categoria = String(body.categoria || 'Outros').trim().slice(0, 50);
  const localizacao = String(body.localizacao || '').trim().slice(0, 200);
  const comentario = String(body.comentario || '').trim().slice(0, 500);
  const nota = Number(body.nota);
  const valor = Number(body.valor) || 0;
  const data = String(body.data || '').trim();
  const permitidas = ['Favorito', 'Melhor custo-beneficio', 'Voltaria', 'Nao voltaria'].concat(carregarConfiguracoes_().marcadoresLugar);
  const marcacoes = Array.isArray(body.marcacoes) ? body.marcacoes.filter(function(m) { return permitidas.includes(m); }) : [];
  if (!nome) throw new Error('Digite o nome do lugar.');
  if (!/^\d{4}-\d{2}-\d{2}$/.test(data)) throw new Error('Escolha a data da visita.');
  if (!Number.isFinite(nota) || nota < 1 || nota > 5) throw new Error('A nota deve ser de 1 a 5.');
  if (!Number.isFinite(valor) || valor < 0) throw new Error('Digite um valor válido.');
  const idExistente = String(body.id || '').trim();
  const aba = obterAbaLugares_();
  const linhaExistente = idExistente ? localizarLinhaPorId_(aba, idExistente, 1) : 0;
  if (idExistente && !linhaExistente) throw new Error('Lugar não encontrado para edição.');
  if (linhaExistente) {
    const dono = String(aba.getRange(linhaExistente, 10).getValue()).toLowerCase();
    if (dono && dono !== String(emailUsuario).toLowerCase()) throw new Error('Você não pode editar este lugar.');
  }
  const id = idExistente || Utilities.getUuid();
  let arquivoId = linhaExistente ? String(aba.getRange(linhaExistente, 9).getValue()) : '';
  if (body.image_base64) {
    const bytes = Utilities.base64Decode(body.image_base64);
    if (bytes.length > 5 * 1024 * 1024) throw new Error('A foto é grande demais.');
    const mime = /^image\/(jpeg|png|webp)$/.test(body.mime_type) ? body.mime_type : 'image/jpeg';
    const novoArquivoId = obterPastaLugares_().createFile(Utilities.newBlob(bytes, mime, id + '.jpg')).getId();
    if (arquivoId) { try { DriveApp.getFileById(arquivoId).setTrashed(true); } catch (erro) { console.warn(erro); } }
    arquivoId = novoArquivoId;
  }
  const valores = [id, nome, categoria, localizacao, nota, comentario, data, marcacoes.join('|'), arquivoId, emailUsuario, new Date(), valor];
  if (linhaExistente) aba.getRange(linhaExistente, 1, 1, valores.length).setValues([valores]);
  else aba.appendRow(valores);
  return { success:true, id:id };
}

function excluirLugar_(id, usuario) {
  const aba = obterAbaLugares_();
  const linha = localizarLinhaPorId_(aba, id, 1);
  if (!linha) throw new Error('Lugar não encontrado.');
  const arquivoId = String(aba.getRange(linha, 9).getValue());
  const dono = String(aba.getRange(linha, 10).getValue()).toLowerCase();
  if (usuario.perfil !== 'ADMIN' && dono !== usuario.email) throw new Error('Você não pode excluir este lugar.');
  if (arquivoId) { try { DriveApp.getFileById(arquivoId).setTrashed(true); } catch (erro) { console.warn(erro); } }
  aba.deleteRow(linha);
  return { success:true };
}

function obterPastaLugares_() {
  const props = PropertiesService.getScriptProperties();
  const idSalvo = props.getProperty('LUGARES_FOLDER_ID');
  if (idSalvo) { try { return DriveApp.getFolderById(idSalvo); } catch (erro) { console.warn(erro); } }
  const pasta = DriveApp.createFolder('Cofrinho dos Fofos - Lugares');
  props.setProperty('LUGARES_FOLDER_ID', pasta.getId());
  return pasta;
}

function carregarFotoLugar_(id) {
  const aba = obterAbaLugares_();
  const linha = localizarLinhaPorId_(aba, id, 1);
  if (!linha) throw new Error('Lugar não encontrado.');
  const arquivoId = String(aba.getRange(linha, 9).getValue());
  if (!arquivoId) return { success:true, id:String(id), url:'' };
  const blob = DriveApp.getFileById(arquivoId).getBlob();
  return { success:true, id:String(id), url:'data:' + blob.getContentType() + ';base64,' + Utilities.base64Encode(blob.getBytes()) };
}

function carregarFotoFeed_(id) {
  const aba = obterAbaFeed_();
  const linha = localizarLinhaPorId_(aba, id, 1);
  if (!linha) throw new Error('Foto não encontrada.');
  const arquivoId = String(aba.getRange(linha, 3).getValue());
  const blob = DriveApp.getFileById(arquivoId).getBlob();
  return {
    success: true,
    id: String(id),
    url: 'data:' + blob.getContentType() + ';base64,' + Utilities.base64Encode(blob.getBytes())
  };
}

function atualizarGasto_(body) {
  const aba = SpreadsheetApp.openById(SHEET_ID).getSheetByName(SHEET_NAME);
  const linha = localizarLinhaPorId_(aba, body.id, 11);
  if (!linha) throw new Error('Gasto não encontrado.');
  const item = validarItens_([{ item: body.item, categoria: body.categoria, valor: body.valor }])[0];
  aba.getRange(linha, 4, 1, 3).setValues([[item.item, item.categoria, item.valor]]);
  return { success: true };
}

function excluirGasto_(id) {
  const aba = SpreadsheetApp.openById(SHEET_ID).getSheetByName(SHEET_NAME);
  const linha = localizarLinhaPorId_(aba, id, 11);
  if (!linha) throw new Error('Gasto não encontrado.');
  aba.deleteRow(linha);
  return { success: true };
}

function salvarFotoFeed_(body, emailUsuario) {
  if (!/^\d{4}-\d{2}$/.test(String(body.mesKey || ''))) throw new Error('Mês inválido.');
  if (!body.image_base64) throw new Error('A foto não chegou.');
  const mime = /^image\/(jpeg|png|webp)$/.test(body.mime_type) ? body.mime_type : 'image/jpeg';
  const id = Utilities.getUuid();
  const pastaDoMes = obterPastaFeedMes_(body.mesKey);
  const bytes = Utilities.base64Decode(body.image_base64);
  if (bytes.length > 5 * 1024 * 1024) throw new Error('A foto é grande demais.');
  const arquivo = pastaDoMes.createFile(Utilities.newBlob(bytes, mime, id + '.jpg'));
  obterAbaFeed_().getRange('B:B').setNumberFormat('@');
  obterAbaFeed_().appendRow([id, body.mesKey, arquivo.getId(), '', emailUsuario, new Date()]);
  return {
    success: true,
    id: id,
    mesKey: String(body.mesKey),
    url: 'data:' + mime + ';base64,' + body.image_base64
  };
}

function atualizarFotoFeed_(body) {
  const aba = obterAbaFeed_();
  const linha = localizarLinhaPorId_(aba, body.id, 1);
  if (!linha) throw new Error('Foto não encontrada.');
  aba.getRange(linha, 4).setValue(String(body.legenda || '').slice(0, 300));
  return { success: true };
}

function excluirFotoFeed_(id) {
  const aba = obterAbaFeed_();
  const linha = localizarLinhaPorId_(aba, id, 1);
  if (!linha) throw new Error('Foto não encontrada.');
  const arquivoId = String(aba.getRange(linha, 3).getValue());
  try { DriveApp.getFileById(arquivoId).setTrashed(true); } catch (erro) { console.warn(erro); }
  aba.deleteRow(linha);
  return { success: true };
}

function obterAbaNotas_() {
  const planilha = SpreadsheetApp.openById(SHEET_ID);
  let aba = planilha.getSheetByName(NOTAS_SHEET_NAME);
  if (!aba) {
    aba = planilha.insertSheet(NOTAS_SHEET_NAME);
    aba.appendRow(['ID', 'Titulo', 'Texto', 'Data', 'Valor', 'Categoria', 'Gasto_ID', 'Usuario', 'Criado_em']);
    aba.setFrozenRows(1);
    aba.getRange('D:D').setNumberFormat('yyyy-mm-dd');
    aba.getRange('E:E').setNumberFormat('€0.00');
  }
  return aba;
}

function carregarNotas_() {
  const aba = obterAbaNotas_();
  const notas = [];
  if (aba.getLastRow() <= 1) return notas;
  aba.getRange(2, 1, aba.getLastRow() - 1, 9).getValues().forEach(function(linha) {
    if (!linha[0] || !linha[1]) return;
    const data = linha[3] instanceof Date
      ? Utilities.formatDate(linha[3], Session.getScriptTimeZone(), 'yyyy-MM-dd')
      : String(linha[3] || '').slice(0, 10);
    const criado = linha[8] instanceof Date
      ? Utilities.formatDate(linha[8], Session.getScriptTimeZone(), "yyyy-MM-dd'T'HH:mm:ss")
      : String(linha[8] || '');
    notas.push({
      id:String(linha[0]), titulo:String(linha[1]), texto:String(linha[2] || ''),
      data:data, valor:Number(linha[4]) || 0, categoria:String(linha[5] || ''),
      gastoId:String(linha[6] || ''), usuario:String(linha[7] || ''), criado:criado
    });
  });
  return notas;
}

function salvarNota_(body, emailUsuario) {
  const titulo = String(body.titulo || '').trim().slice(0, 150);
  const texto = String(body.texto || '').trim().slice(0, 1500);
  const data = String(body.data || '').trim();
  const valor = Number(body.valor) || 0;
  const categoria = listarCategorias_().includes(body.categoria) ? body.categoria : 'Outros';
  if (!titulo) throw new Error('Digite o título da nota.');
  if (data && !dataValida_(data)) throw new Error('A data do lembrete é inválida.');
  if (!Number.isFinite(valor) || valor < 0 || valor > 1000000) throw new Error('O valor do evento é inválido.');
  if (valor > 0 && !data) throw new Error('Escolha a data do evento pago.');

  let gastoId = '';
  if (valor > 0) {
    const gasto = salvarGastos_({
      data:data, estabelecimento:'', origem:'Nota',
      itens:[{ item:titulo, categoria:categoria, valor:valor, tipo:'Variável' }]
    }, emailUsuario);
    gastoId = gasto.registro_ids[0];
  }

  const id = Utilities.getUuid();
  obterAbaNotas_().appendRow([id, titulo, texto, data || '', valor, valor > 0 ? categoria : '', gastoId, emailUsuario, new Date()]);
  return { success:true, id:id, gastoId:gastoId };
}

function excluirNota_(id) {
  const aba = obterAbaNotas_();
  const linha = localizarLinhaPorId_(aba, id, 1);
  if (!linha) throw new Error('Nota não encontrada.');
  const gastoId = String(aba.getRange(linha, 7).getValue() || '');
  if (gastoId) {
    try { excluirGasto_(gastoId); } catch (erro) { console.warn(erro); }
  }
  aba.deleteRow(linha);
  return { success:true };
}

function obterAbaFeed_() {
  const planilha = SpreadsheetApp.openById(SHEET_ID);
  let aba = planilha.getSheetByName('Feed');
  if (!aba) {
    aba = planilha.insertSheet('Feed');
    aba.appendRow(['ID', 'Mes', 'Arquivo_ID', 'Legenda', 'Usuário', 'Registrado_em']);
    aba.setFrozenRows(1);
  }
  aba.getRange('B:B').setNumberFormat('@');
  return aba;
}

function obterPastaFeedRaiz_() {
  const props = PropertiesService.getScriptProperties();
  const idSalvo = props.getProperty('FEED_FOLDER_ID');
  if (idSalvo) {
    try { return DriveApp.getFolderById(idSalvo); } catch (erro) { console.warn(erro); }
  }
  const pastaRaiz = DriveApp.createFolder('Cofrinho dos Fofos - Feed');
  props.setProperty('FEED_FOLDER_ID', pastaRaiz.getId());
  return pastaRaiz;
}

function obterPastaFeedMes_(mesKey) {
  const nomes = ['Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho',
    'Julho', 'Agosto', 'Setembro', 'Outubro', 'Novembro', 'Dezembro'];
  const partes = String(mesKey).split('-');
  const nome = mesKey + ' - ' + nomes[Number(partes[1]) - 1];
  const raiz = obterPastaFeedRaiz_();
  const existentes = raiz.getFoldersByName(nome);
  return existentes.hasNext() ? existentes.next() : raiz.createFolder(nome);
}

function garantirIdsGastos_(aba) {
  if (aba.getLastRow() <= 1) return;
  const intervalo = aba.getRange(2, 11, aba.getLastRow() - 1, 1);
  const ids = intervalo.getValues();
  let mudou = false;
  ids.forEach(function(linha) {
    if (!linha[0]) { linha[0] = Utilities.getUuid(); mudou = true; }
  });
  if (mudou) intervalo.setValues(ids);
}

function localizarLinhaPorId_(aba, id, coluna) {
  if (!aba || aba.getLastRow() <= 1) return 0;
  const ids = aba.getRange(2, coluna, aba.getLastRow() - 1, 1).getDisplayValues();
  for (let i = 0; i < ids.length; i++) if (ids[i][0] === String(id)) return i + 2;
  return 0;
}

function propriedadeObrigatoria_(nome) {
  const valor = PropertiesService.getScriptProperties().getProperty(nome);
  if (!valor) throw new Error('Falta configurar ' + nome + ' nas Propriedades do script.');
  return valor.trim();
}

function dataValida_(valor) {
  return /^\d{4}-\d{2}-\d{2}$/.test(String(valor || ''));
}

function respostaJson_(objeto) {
  return ContentService.createTextOutput(JSON.stringify(objeto))
    .setMimeType(ContentService.MimeType.JSON);
}

function mensagemSegura_(erro) {
  return erro && erro.message ? erro.message : 'Ocorreu um erro. Tente novamente.';
}
