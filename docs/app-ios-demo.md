# Piggu demo no iPhone

A demo é o app inteiro com **dados de exemplo** (Família Demo: Ana, titular Premium, e
Beto, membro), sem backend e sem login. Dá para lançar, editar, pagar e resgatar: tudo
fica na memória do app e volta ao início quando ele é fechado. Conectar banco (Pluggy),
pagar assinatura (Stripe) e apagar conta respondem "indisponível na demonstração".

## No navegador do PC ou do iPhone (mais rápido)

```bash
cd frontend
npm run start:demo        # ng serve --configuration demo --host 0.0.0.0, porta 4200
```

- PC: http://localhost:4200
- iPhone na mesma rede Wi-Fi: `http://<IP do PC>:4200` no Safari (o IP aparece na linha
  "Network" do comando). Se não abrir, libere a porta no Firewall do Windows.

## Como app (IPA)

1. O workflow **iOS demo (IPA sem assinatura)** (`.github/workflows/ios-demo.yml`) roda em
   macOS no GitHub a cada push numa branch `demo/**` e publica o artefato
   `Piggu-demo-ipa` (um zip com o `Piggu-demo.ipa`).
2. No Windows, instale o **Sideloadly** (sideloadly.io) e o iTunes/Apple Devices.
3. Ligue o iPhone no cabo, abra o Sideloadly, arraste o `Piggu-demo.ipa`, informe o seu
   Apple ID e clique em Start. O Sideloadly assina o app com o seu Apple ID.
4. No iPhone: Ajustes → Geral → VPN e Gerenciamento de Dispositivo → confie no seu Apple ID.
   No iOS 16 ou mais novo, ligue também Ajustes → Privacidade e Segurança → Modo de
   Desenvolvedor.

Com Apple ID gratuito, o app vale por **7 dias** (depois é só instalar de novo) e cabem
até 3 apps assim no aparelho.

## Como funciona

- `ng build --configuration demo` troca `src/app/demo/modo-demo.ts` por
  `modo-demo.demo.ts` (fileReplacements): um interceptor responde todas as rotas `/api`
  com os dados de `dados-da-demo.ts`. O build normal não leva nada disso; o CI confere.
- Capacitor 8 (`capacitor.config.ts`, pasta `ios/`, Swift Package Manager, sem CocoaPods)
  empacota `dist/demo/browser`. Ícone e splash saem de `frontend/resources/`.
