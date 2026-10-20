# Observabilidade: métricas, logs, erros, alertas e painel analítico

Tudo roda no próprio servidor, sem serviço de fora e sem porta pública nova. O passo a
passo para ligar e acompanhar no dia a dia está no guia da pasta do projeto
(`guias/monitoramento-e-painel.md`); aqui fica como as peças se encaixam.

```
 serviços Spring ──/actuator/prometheus──► Prometheus ──┐
 node-exporter (CPU, memória, disco) ─────► (1 ano)     │
                                                        ├──► Grafana  (127.0.0.1:3000,
 stdout dos containers ─► arquivo do Docker ─► Alloy ─► Loki (30 dias) │    só por túnel SSH)
                                                        │
 Postgres, schema "analitico" (só contagens) ───────────┘
```

| Peça | Imagem | O que faz | Memória |
|---|---|---|---|
| `prometheus` | `prom/prometheus:v3.13.4` | Lê as métricas a cada 30 s e guarda 1 ano (teto de 2 GB) | 384 MB |
| `loki` | `grafana/loki:3.7.8` | Guarda os logs por 30 dias e apaga o resto sozinho | 384 MB |
| `alloy` | `grafana/alloy:v1.20.1` | Lê os arquivos de log do Docker e manda para o Loki | 192 MB |
| `node-exporter` | `prom/node-exporter:v1.12.1` | CPU, memória, disco e rede da máquina | 64 MB |
| `grafana` | `grafana/grafana-oss:13.0.2` | Painéis e alertas por e-mail | 384 MB |

Grafana, Loki e Alloy são programas em Go e levam um `GOMEMLIMIT` um pouco abaixo do teto
(`PIGGU_GOMEMLIMIT_<PEÇA>`): sem ele, abrir vários painéis de uma vez passava do limite e o
kernel matava o Grafana. Subiu um `PIGGU_MEMORIA_*`? Suba o `GOMEMLIMIT` junto.

Ficam no perfil `observabilidade` do `backend/docker-compose.yml` e as configurações em
`backend/observabilidade/`. Com `PIGGU_OBSERVABILIDADE=true` no `.env`, o
`deploy/atualizar.sh` sobe o perfil junto com o app e o reinicia quando algo em
`backend/observabilidade/` muda. Um problema no monitoramento não reprova o deploy.

## Métricas e performance

Cada serviço publica `/actuator/prometheus` (Micrometer). O endereço fica aberto sem token
porque só a rede interna do Compose chega nele: o Caddy repassa apenas `/api` e o gateway
não roteia `/actuator`. As métricas trazem rota (o padrão, como `/api/expenses/{id}`),
status e tempos; nunca e-mail, id de pessoa ou valor.

- **Tempo de resposta:** `http.server.requests` em cada serviço e
  `spring.cloud.gateway.requests` no gateway, com faixas fixas (50 ms a 5 s) para o p95.
- **JVM e banco:** heap, threads, GC e conexões do Hikari, de graça pelo Spring Boot.
- **Falhas inesperadas:** `piggu_falhas_inesperadas_total{excecao}`, somado pelo
  `ApiExceptionHandler` a cada 500.
- **Erros no app:** `piggu_erros_do_app_total`, somado a cada erro de JavaScript recebido.
- **Pessoas ativas:** `piggu_uso_pessoas_ativas{janela="dia|semana|mes"}`, lido do
  `last_login_at` no identity (`MetricasDeUso`) no máximo uma vez por minuto. O banco só
  guarda o último acesso; a história fica no Prometheus a partir do dia em que ele liga.

## Logs

Os serviços escrevem JSON no padrão ECS (`LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs` no
Compose; `PIGGU_LOG_FORMATO=` vazio volta ao texto). Cada linha já trazia `requestId` e
`userId` (o UUID, nunca o e-mail) e agora sai com `log.level`, `log.logger` e, nos erros,
`error.type` e `error.stack_trace` em campos próprios.

O Alloy lê `/var/lib/docker/containers/*/*-json.log` **somente leitura**, em vez do socket
do Docker (que daria controle total da máquina ao container). O nome do serviço vem do
próprio arquivo: o `x-logs` do Compose pede ao Docker que grave `com.docker.compose.service`
em cada linha. Linha sem esse rótulo (outros containers, o próprio monitoramento) é
descartada. Antes de gravar, o Alloy troca e-mail, CPF e token que tenham escapado por
`[email]`, `[cpf]` e `[token]`. Rótulos no Loki: só `servico` e `nivel`.

A rotação local do Docker (5 arquivos de 10 MB por serviço) continua: é a cópia que fica
se o Loki estiver fora.

## Erros (error tracking)

- **Backend:** toda linha `ERROR` vai para o painel "Erros e logs", agrupada pela classe
  da exceção, com a pilha e o `requestId` para achar a chamada inteira em todos os serviços.
- **App:** o `ErrosDoApp` (front, `core/erros`) substitui o `ErrorHandler` do Angular e
  manda os erros de JavaScript para `POST /api/app/erros` (identity, aberto, sem token). Vai
  só mensagem, pilha e tela (ids viram `:id`), limpos de e-mail, CPF e telefone no aparelho
  e de novo no servidor. No máximo 5 por aba, sem repetir, e o limite por IP das rotas
  abertas vale. Erro de chamada à API fica de fora (o backend já registra) e a demo não
  manda nada.

## Rate limit

| Camada | Onde | Limite |
|---|---|---|
| Por IP, a API inteira | `LimitePorIpGlobalFilter` no gateway | 600 por minuto (`PIGGU_LIMITE_GATEWAY`) |
| Por IP, rotas abertas | `LimiteDeRequisicoes` nos serviços | login 20, demais 120 por minuto |
| Por conta | `LimiteDeRequisicoes` nos serviços | escrita 120, envio 15, busca cara 60 por minuto |

O teto do gateway é largo de propósito: no celular muita gente sai pelo mesmo IP (CGNAT)
e uma tela faz várias chamadas. Ele corta enxurrada e varredura, e a recusa (429, com
`Retry-After`) sai no mesmo formato de erro dos serviços.

## Alertas

Provisionados em `backend/observabilidade/grafana/provisionamento/alertas/alertas.yml` e
enviados por e-mail para `PIGGU_ALERTAS_EMAIL` pelo SMTP da Resend (mesma
`PIGGU_EMAIL_API_KEY` dos avisos do app). Um e-mail por tipo, repetido a cada 4 horas
enquanto continuar.

| Alerta | Quando |
|---|---|
| Serviço fora do ar | um serviço principal não responde há 2 minutos |
| Erros no log | qualquer linha `ERROR` nos últimos 10 minutos |
| App lento | p95 do gateway acima de 2 s por 10 minutos |
| Disco quase cheio | mais de 85% |
| Memória no limite | mais de 90% por 10 minutos |
| Muitos erros no app | mais de 20 erros de JavaScript em 30 minutos |
| Muitas recusas pelo limite | mais de 100 respostas 429 em 10 minutos |

## Painel analítico

O painel "Uso do app" lê o Postgres com o usuário `piggu_analitico`, criado pelo
`db/init/01-criar-bancos.sh`. Ele só entra no `piggu_identity` e no `piggu_finance`, e lá
só lê o schema `analitico`: views que devolvem contagens (migrations V32 do identity e V18
do financeiro). Nenhuma view traz e-mail, nome, documento, valor em dinheiro, descrição ou
id de pessoa ou família; o `MigrationsTest` e o `MetricasDeUsoTest` quebram se uma coluna
assim aparecer. O usuário é somente leitura e cada consulta tem prazo de 15 s.

View nova: crie no schema `${esquema_analitico}` (o placeholder do Flyway que vale
`analitico`; os testes que migram um schema à parte usam outro) numa migration do serviço; o `ALTER DEFAULT
PRIVILEGES` do script já a deixa legível pelo painel.

## Mudar um painel

Os painéis vêm dos JSON em `backend/observabilidade/grafana/paineis/` e não aceitam edição
pela tela (o arquivo sempre vence). Para mudar: edite no Grafana, *Export > Export as JSON*,
troque o arquivo no repositório e mande num PR. O Grafana relê em até um minuto.
