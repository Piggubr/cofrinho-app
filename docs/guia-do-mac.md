# Guia do Mac: do zero até o Piggu rodando no iPhone

Para um Mac recém-tirado da caixa (Apple Silicon). Siga na ordem; cada passo termina com um
comando para conferir se deu certo. Tempo total: cerca de 1 hora, quase tudo esperando download.

O que cada parte serve:

| Parte | Para quê | Obrigatório? |
| --- | --- | --- |
| 1. Terminal | Rodar os comandos deste guia | Sim |
| 2. Xcode | App nativo (Swift) e app Capacitor no iPhone | Sim |
| 3. Homebrew | Instalar o resto com um comando cada | Sim |
| 4. Git e GitHub | Baixar e enviar código | Sim |
| 5. Node 24 e npm | Site (Angular) e app Capacitor | Sim, para o Capacitor e o site |
| 6. Java 21 e Maven | Backend rodando no Mac | Só se for mexer no backend |
| 7. Docker | Banco e serviços do backend no Mac | Só se for mexer no backend |
| 8. App nativo | Piggu em SwiftUI (`ios-nativo/`) | — |
| 9. App Capacitor | Piggu atual (`frontend/ios`) | — |

---

## 1. Terminal

1. Aperte **⌘ Espaço**, digite **Terminal** e Enter.
2. Para rodar um comando deste guia: copie o bloco inteiro, cole no Terminal (⌘V) e aperte Enter.
3. Quando pedir **Password**, é a senha de entrar no Mac. **Nada aparece enquanto você digita**
   (nem bolinhas): é normal. Digite e aperte Enter.
4. Dica: deixe o Terminal no Dock (botão direito no ícone → Opções → Manter no Dock).

Depois de instalar qualquer coisa nova, se um comando disser `command not found`, **feche o
Terminal (⌘Q) e abra de novo**: ele só lê as configurações novas ao abrir.

## 2. Xcode

O Xcode 27.1 já está instalado em `/Applications/Xcode.app`. Falta deixá-lo como o padrão e
terminar a primeira configuração.

1. Abra o **Xcode** uma vez pelo Launchpad. Se ele oferecer componentes, marque **iOS** e
   clique em **Download & Install** (são alguns GB; o simulador vem daí). Se não oferecer, vá em
   **Xcode → Settings → Components** e baixe a plataforma **iOS** mais recente.
2. No Terminal, aponte as ferramentas para o Xcode (hoje estão nas Command Line Tools, por isso
   o `xcodebuild` reclama), aceite a licença e rode a primeira configuração:

   ```bash
   sudo xcode-select -s /Applications/Xcode.app/Contents/Developer
   sudo xcodebuild -license accept
   xcodebuild -runFirstLaunch
   ```

3. Entre com a conta de desenvolvedor no Xcode: **Xcode → Settings → Accounts → +** →
   **Apple ID** → o mesmo Apple ID em que você assinou o Apple Developer Program. Na lista de
   *Teams* deve aparecer o seu nome com **(Team)** ou **Individual**, sem "Personal Team".
   *Personal Team* é a conta gratuita: o app vence em 7 dias e não sobe para a App Store.

**Conferir:**

```bash
xcode-select -p
```

Tem que mostrar `/Applications/Xcode.app/Contents/Developer`.

```bash
xcodebuild -version
```

Tem que mostrar `Xcode 27.1`.

## 3. Homebrew

O Homebrew é o "instalador" de programas de desenvolvedor no Mac.

```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

Ele pede a senha e aperta **Enter** para confirmar; demora uns minutos. No fim ele mostra
"Next steps". Rode estes dois comandos para o Terminal achar o `brew` sempre:

```bash
echo 'eval "$(/opt/homebrew/bin/brew shellenv)"' >> ~/.zprofile
eval "$(/opt/homebrew/bin/brew shellenv)"
```

**Conferir:**

```bash
brew --version
```

Tem que mostrar `Homebrew 4.x` ou mais novo.

## 4. Git e GitHub

O Git já veio com as ferramentas do Xcode. Configure seu nome e e-mail (aparecem nos commits):

```bash
git config --global user.name "Seu Nome"
git config --global user.email "seu-email@exemplo.com"
git config --global init.defaultBranch main
```

Para falar com o GitHub sem digitar senha, instale o `gh` e faça login (ele abre o navegador):

```bash
brew install gh
gh auth login
```

Responda: **GitHub.com** → **HTTPS** → **Yes** (autenticar o Git) → **Login with a web browser**.
Copie o código que aparecer, cole na página que abrir e autorize.

O repositório já está neste Mac em `~/Documents/Piggu/cofrinho-app`. Num Mac sem ele:

```bash
mkdir -p ~/Documents/Piggu && cd ~/Documents/Piggu
gh repo clone Piggubr/cofrinho-app
```

**Conferir:**

```bash
gh auth status
```

Tem que mostrar `Logged in to github.com`.

## 5. Node 24 e npm

O projeto usa **Node 24** (o mesmo do CI). O npm vem junto com o Node.

```bash
brew install node@24
echo 'export PATH="/opt/homebrew/opt/node@24/bin:$PATH"' >> ~/.zprofile
export PATH="/opt/homebrew/opt/node@24/bin:$PATH"
```

O `node@24` não entra no PATH sozinho; é por isso que tem a linha do `export`.

Instale as dependências do site/app (a primeira vez demora alguns minutos):

```bash
cd ~/Documents/Piggu/cofrinho-app/frontend
npm ci
```

**Conferir:**

```bash
node -v
```

Tem que mostrar `v24.` alguma coisa.

```bash
npm -v
```

Tem que mostrar `10.` ou mais.

Para ver o site no navegador do Mac, com dados de exemplo e sem backend:

```bash
cd ~/Documents/Piggu/cofrinho-app/frontend
npm run start:demo
```

Abra http://localhost:4200. Para parar: **Control C** no Terminal.

## 6. Java 21 e Maven (só para o backend)

```bash
brew install openjdk@21 maven
sudo ln -sfn /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-21.jdk
echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 21)' >> ~/.zprofile
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

O `ln` registra o Java no macOS. Sem ele, o `java` responde "Unable to locate a Java Runtime".

**Conferir:**

```bash
java -version
```

Tem que mostrar `openjdk version "21...`.

```bash
mvn -v
```

Tem que mostrar `Java version: 21...`.

## 7. Docker (só para o backend)

```bash
brew install --cask docker
```

Abra o **Docker** pelo Launchpad uma vez, aceite os termos e espere a baleia na barra de menus
ficar parada. Depois, para subir o backend inteiro no Mac:

```bash
cd ~/Documents/Piggu/cofrinho-app/backend
cp .env.example .env
docker compose up --build
```

A API fica em `http://localhost:8080`. Os detalhes estão em `backend/README.md`.

**Conferir:**

```bash
docker --version
```

## 8. App nativo (Swift) no simulador e no iPhone

Está na branch `swift/inicio` (só local por enquanto).

```bash
cd ~/Documents/Piggu/cofrinho-app
git switch swift/inicio
open ios-nativo/Piggu.xcodeproj
```

### No simulador

1. No topo do Xcode, ao lado do nome **Piggu**, escolha **iPhone 17** (ou outro).
2. Aperte **⌘R**. O simulador abre com o login.
3. Para ver as telas sem conta: **Product → Scheme → Edit Scheme… → Run → Arguments** e marque
   `-PigguDemo YES`. Qualquer e-mail e senha entram, com dados de exemplo.

### Assinatura (uma vez)

1. No Xcode, clique no projeto **Piggu** (ícone azul no topo da lista à esquerda) → alvo
   **Piggu** → aba **Signing & Capabilities**.
2. **Automatically manage signing** ligado; em **Team**, escolha a sua equipe paga (não a
   *Personal Team*). O Xcode cria os certificados e registra o Bundle ID sozinho.
3. Atenção: o app nativo usa o mesmo Bundle ID do app Capacitor (`br.com.piggu.app`). No
   iPhone, **um substitui o outro**. Se quiser os dois juntos para comparar, troque, só no seu
   Mac, o Bundle Identifier do nativo para algo como `br.com.piggu.app.nativo`.

### No iPhone

1. Ligue o iPhone no Mac pelo cabo e desbloqueie. No iPhone, toque em **Confiar** neste
   computador e digite o código do iPhone.
2. Ligue o **Modo de Desenvolvedor**: Ajustes → Privacidade e Segurança → Modo de
   Desenvolvedor → ligar. O iPhone reinicia e pergunta de novo; confirme. Se a opção não
   aparecer, rode o app uma vez pelo Xcode (passo 3) e ela aparece.
3. No topo do Xcode, troque o simulador pelo seu **iPhone** e aperte **⌘R**.
4. Na primeira vez o Xcode pode levar alguns minutos "Preparing iPhone".
5. Depois da primeira vez, dá para rodar sem cabo: Xcode → **Window → Devices and Simulators**
   → seu iPhone → **Connect via network**.

## 9. App Capacitor (o Piggu atual) no iPhone

Precisa do passo 5 (Node e `npm ci`).

**Demo** (dados de exemplo, sem conta):

```bash
cd ~/Documents/Piggu/cofrinho-app/frontend
npm run build:demo
npx cap sync ios
npx cap open ios
```

**Produção** (abre o piggu.com.br dentro do app, com a sua conta):

```bash
cd ~/Documents/Piggu/cofrinho-app/frontend
PIGGU_SITE=https://piggu.com.br npm run build
PIGGU_SITE=https://piggu.com.br npx cap sync ios
npx cap open ios
```

No Xcode que abrir, faça como no passo 8: alvo **App** → **Signing & Capabilities** → **Team**
→ escolha o iPhone → **⌘R**.

O Xcode grava o Team no `frontend/ios/App/App.xcodeproj/project.pbxproj`. Isso aparece no
`git status` como mudança. Só faça commit disso se for intencional.

## Problemas comuns

| Mensagem | O que fazer |
| --- | --- |
| `command not found: brew` / `npm` / `node` | Feche e abra o Terminal. Se continuar, refaça as linhas `echo ... >> ~/.zprofile` do passo. |
| `xcode-select: error: tool 'xcodebuild' requires Xcode` | Passo 2, comando `sudo xcode-select -s ...`. |
| `Unable to locate a Java Runtime` | Passo 6, comando `sudo ln -sfn ...`. |
| Xcode: "Signing for "Piggu" requires a development team" | Passo 8, Assinatura: escolha o Team. |
| Xcode: "Failed to register bundle identifier" | O Bundle ID já está em outra conta. Use a mesma conta do app Capacitor, ou troque o Bundle ID só no seu Mac. |
| Xcode: "Developer Mode disabled" | Passo 8, No iPhone, item 2. |
| iPhone não aparece no Xcode | Troque o cabo/porta, desbloqueie o iPhone, toque em Confiar. Veja em Window → Devices and Simulators. |
| iPhone: "Desenvolvedor não confiável" | Ajustes → Geral → VPN e Gerenciamento de Dispositivo → toque na sua conta → Confiar. |
| `npm ci` falha com erro de versão | Confira `node -v` (tem que ser 24) e rode de novo. |
| `EACCES` / permissão negada no npm | Não use `sudo npm`. Feche e abra o Terminal e confira se o `node` é o do Homebrew: `which node` deve mostrar `/opt/homebrew/...`. |

## Conferência final

Cole tudo de uma vez; cada linha tem que mostrar uma versão, sem erro:

```bash
xcode-select -p; xcodebuild -version | head -1; brew --version | head -1; git --version; gh --version | head -1; node -v; npm -v; java -version 2>&1 | head -1; docker --version
```
