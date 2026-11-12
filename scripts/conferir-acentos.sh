#!/usr/bin/env bash
# Duas conferencias de texto que chega ao usuario:
#
# 1. Regressao (18/10/2026): duas mensagens chegaram como "SÃ³ quem lanÃ§ou" e "O Piggu Ã© para
#    quem", texto UTF-8 gravado de novo como se fosse Latin-1. Falha se esse padrao (Ã ou Â
#    seguido de um byte de continuacao) voltar ao codigo.
# 2. Auditoria (11/11/2026, BUG-009): 112 mensagens do backend sem acento ("nao", "ja", "O valor
#    e alto demais"), mostradas na tela como vieram. Falha se uma string Java do backend voltar
#    a ter uma palavra comum sem acento. Comentario, log e SQL ficam de fora; palavra que e dado
#    (MarcadoresLugar) tambem.
#
# Em Python, e nao grep -P: o grep do Mac nao tem -P e a conferencia passava sem olhar nada.
#
# Uso: bash scripts/conferir-acentos.sh   (da raiz do repositorio)
set -euo pipefail

cd "$(dirname "$0")/.."

python3 - <<'PY'
import glob, re, sys

falhas = 0

# 1. UTF-8 lido como Latin-1.
QUEBRADO = re.compile('[ÃÂ][\u0080-¿]')
pastas = ['backend/*/src', 'frontend/src', 'frontend/e2e', 'frontend/stack', 'docs', 'deploy']
fora = ('/node_modules/', '/target/', '/dist/')
arquivos = [f for p in pastas for f in glob.glob(p + '/**/*', recursive=True)] + ['README.md']
for nome in arquivos:
    if any(x in nome for x in fora):
        continue
    try:
        linhas = open(nome, encoding='utf-8').read().split('\n')
    except (IsADirectoryError, UnicodeDecodeError, FileNotFoundError):
        continue
    for n, linha in enumerate(linhas, 1):
        if QUEBRADO.search(linha):
            print(f'{nome}:{n}: {linha.strip()}')
            falhas += 1
if falhas:
    print('::error::Acento quebrado (UTF-8 lido como Latin-1) nas linhas acima. Reescreva o texto com o acento certo.')
    sys.exit(1)

# 2. Palavra comum sem acento numa string Java do backend.
SEM_ACENTO = re.compile(r'(?<![\w-])(' + '|'.join([
    'nao', 'ja', 'voce', 'voces', 'tambem', 'ate', 'sao', 'so', 'mes', 'tres', 'alem', 'apos', 'ninguem', 'alguem',
    'codigo', 'numeros?', 'familias?', 'possivel', 'disponivel', 'indisponivel', 'invalid[oa]s?', 'valid[oa]s?',
    'maxim[oa]', 'minim[oa]', 'opcao', 'opcoes', 'acao', 'sessao', 'conexao', 'descricao', 'cartao', 'cartoes',
    'orcamentos?', 'depositos?', 'titulo', 'premios?', 'precos?', 'servicos?', 'historico', 'usuarios?', 'endereco',
    'pagina', 'localizacao', 'comentario', 'informacao', 'configuracao', 'instalacao', 'confirmacao', 'exclusao',
    'permissao', 'propri[oa]', 'ultim[oa]', 'proxim[oa]', 'unic[oa]', 'periodo', 'faca', 'peca',
    r"e'", r'e (?:alt[oa]|long[oa]|grande|de \d|invalid|inválid)', r'esta (?:vazi[oa]|configurad[oa]|disponível|indisponível)',
    r'(?:não|nao|só|so) da (?:para|pra)',
]) + r')(?!\w)', re.IGNORECASE)
LITERAL = re.compile(r'"((?:[^"\\\n]|\\.)*)"')
PULAR_LINHA = re.compile(r'^\s*(//|\*|/\*)|log\.(info|warn|error|debug|trace)\(|@Operation|@Tag\(|@Schema|@Parameter')
SQL = re.compile(r'\b(SELECT|INSERT|UPDATE|DELETE|WHERE|FROM)\b|\{alias\}')
for nome in sorted(glob.glob('backend/*/src/main/java/**/*.java', recursive=True)):
    if nome.endswith('MarcadoresLugar.java'):
        continue
    for n, linha in enumerate(open(nome, encoding='utf-8'), 1):
        if PULAR_LINHA.search(linha) or '"""' in linha:
            continue
        for literal in LITERAL.findall(linha):
            # Texto de tela tem espaco; chave, rota e nome de coluna nao.
            if ' ' not in literal or SQL.search(literal) or '\\\\b' in literal:
                continue
            achou = SEM_ACENTO.search(literal)
            if achou:
                print(f'{nome}:{n}: "{achou.group(0)}" em "{literal}"')
                falhas += 1
if falhas:
    print('::error::Mensagem sem acento nas linhas acima (BUG-009). O texto vai para a tela como esta: acentue.')
    sys.exit(1)
print('Nenhum acento quebrado nem mensagem sem acento.')
PY
