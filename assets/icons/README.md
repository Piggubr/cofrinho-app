# Cofrinho dos Fofos — Estrutura Refatorada

Este repositório contém a versão refatorada do monolito HTML/CSS/JS do **Cofrinho dos Fofos**, separando o código em arquivos organizados por responsabilidade para facilitar manutenção e evolução.

## Estrutura de pastas

```text
/
├── index.html
├── manifest.webmanifest
├── favicon.jpg
├── apple-touch-icon.png
│
├── css/
│   ├── variables.css
│   ├── base.css
│   ├── layout.css
│   ├── forms.css
│   ├── calendar.css
│   ├── shopping.css
│   ├── movies.css
│   ├── places.css
│   ├── prizes.css
│   ├── modals.css
│   └── responsive.css
│
├── js/
│   ├── utils.js
│   ├── state.js
│   ├── storage.js
│   ├── integrations.js
│   ├── modals.js
│   ├── navigation.js
│   ├── dashboard.js
│   ├── transactions.js
│   ├── calendar.js
│   ├── shopping.js
│   ├── photos.js
│   ├── movies.js
│   ├── places.js
│   ├── prizes.js
│   ├── fofocoins.js
│   └── app.js
│
└── assets/
    ├── icons/
    ├── illustrations/
    ├── logos/
    ├── movies/
    ├── places/
    └── photos/
```

## Como rodar localmente

### Opção 1: Abrir direto no navegador

1. Clone ou baixe este repositório.
2. Navegue até a pasta raiz.
3. Abra o arquivo `index.html` diretamente no seu navegador (duplo clique ou `Open With`).

O app deve carregar normalmente em modo local, usando `localStorage` para persistir os dados.

### Opção 2: Usar um servidor estático simples (recomendado)

Algumas funcionalidades (como certos tipos de requisição e PWA) podem se comportar melhor com um servidor HTTP local.

#### Com Python 3

Na raiz do projeto:

```bash
python -m http.server 8000
```

Depois acesse:

```text
http://localhost:8000
```

#### Com Node.js (http-server)

Se tiver Node instalado:

```bash
npx http-server -p 8000
```

E acesse:

```text
http://localhost:8000
```

## Imagens e assets

A estrutura espera que as imagens estejam organizadas assim:

```text
assets/
├── icons/
│   ├── olho.svg
│   ├── olho-fechado.svg
│   ├── menu.svg
│   ├── trofeu.svg
│   ├── assets/icons/coin.svg
│   ├── estrela.svg
│   ├── icone-scan.svg
│   └── icone-carrinho.svg
│
├── illustrations/
│   ├── piggu-abertura.webp
│   ├── porquinho-perfil.svg
│   ├── porquinho-gastos.svg
│   ├── trofeu-v2.svg
│   └── new-profile.svg
│
├── logos/
│   ├── logo-v2.svg
│   ├── cofrinho-logo.svg
│   ├── favicon.jpg
│   └── apple-touch-icon.png
│
├── movies/
│   └── posters/
│
├── places/
│   └── photos/
│
└── photos/
    └── monthly/
```

No `index.html` os caminhos estão relativos à raiz, por exemplo:

```html
<img src="assets/illustrations/piggu-abertura.webp" alt="...">
```

Se preferir manter as imagens na raiz como no original, basta ajustar os `src` no HTML.

## Integração com Google Sheets / Apps Script

O arquivo `js/integrations.js` contém as constantes:

```js
const APPSSCRIPTURL = 'https://script.google.com/.../exec';
const GOOGLECLIENTID = '....apps.googleusercontent.com';
```

Substitua pelos valores do seu projeto do Google Apps Script conforme o guia que você já tem. Enquanto essas URLs não forem configuradas, o app