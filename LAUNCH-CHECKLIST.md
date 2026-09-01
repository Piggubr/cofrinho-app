# Piggu — checklist de publicação

## Já preparado no código

- Planilha: `1Hk1JYEbZzlsZErV7CErt-0Op-rlhLPG2UNSKKK-ORA0`
- Conta administradora: `pigguadm@gmail.com`
- Backend: `Code.gs`
- A função `configurarPlanilha()` cria todas as abas, cabeçalhos e pastas necessárias.

## 1. Criar o Apps Script

1. Entre em `https://script.google.com/home` usando `pigguadm@gmail.com`.
2. Crie um projeto e dê o nome `Piggu Backend`.
3. Substitua o conteúdo de `Code.gs` pelo arquivo `Code.gs` deste projeto.
4. Salve.
5. No seletor de funções, execute `configurarPlanilha` uma vez e aprove as permissões.

## 2. Propriedades do script

Em **Configurações do projeto → Propriedades do script**, crie:

- `GOOGLE_CLIENT_ID`: ID OAuth criado para o site.
- `GEMINI_API_KEY`: opcional; habilita leitura de recibos por foto.
- `TMDB_READ_TOKEN`: opcional; habilita a busca de filmes.

Não coloque esses valores no GitHub. O ID OAuth também será configurado no frontend; os outros dois permanecem somente no Apps Script.

## 3. Publicar o backend

1. Clique em **Implantar → Nova implantação**.
2. Tipo: **Aplicativo da Web**.
3. Executar como: **Eu**.
4. Acesso: escolha a opção compatível com os usuários do Piggu.
5. Implante e copie a URL final terminada em `/exec`.

## 4. OAuth

1. No Google Cloud, crie um cliente OAuth do tipo **Aplicativo da Web**.
2. Cadastre o domínio oficial em **Origens JavaScript autorizadas**.
3. Durante os testes, adicione também a URL de preview usada no Netlify.
4. Copie o ID do cliente, sem compartilhar nenhum segredo.

## 5. Conectar e publicar o site

1. Trocar `APPSSCRIPTURL` e `GOOGLECLIENTID` em `js/integrations.js`.
2. Testar login, leitura e gravação.
3. Enviar a versão aprovada para a branch de produção do GitHub.
4. Confirmar o deploy no Netlify e testar o domínio oficial.

## Testes mínimos antes de divulgar

- Login e saída da conta.
- Adicionar entrada e gasto manual.
- Editar Economia do mês.
- Criar, avançar, concluir e apagar uma meta.
- Criar e finalizar uma lista de compras.
- Criar e apagar lugar e nota.
- Atualizar a página e confirmar que os dados continuam salvos.
- Testar em iPhone e em uma janela anônima.
