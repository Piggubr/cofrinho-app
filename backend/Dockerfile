# Build unico para todos os servicos: o modulo desejado entra por ARG.
# Um Dockerfile so evita varios arquivos quase identicos saindo de sincronia.
# O build roda na maquina que compila (BUILDPLATFORM): o .jar e o mesmo em qualquer
# processador. So a imagem final e da plataforma do servidor (ARM na Oracle), entao o CI
# monta a imagem ARM sem emular o Maven, que levaria muito mais tempo.
FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# As dependencias mudam bem menos que o codigo: copiar so os poms primeiro
# faz o cache do Docker sobreviver a maioria das alteracoes. Todo modulo do pom raiz
# precisa estar aqui: faltando um, o go-offline falha e cada imagem baixa tudo de novo.
COPY pom.xml .
COPY piggu-common/pom.xml piggu-common/
COPY piggu-testing/pom.xml piggu-testing/
COPY piggu-gateway/pom.xml piggu-gateway/
COPY piggu-identity/pom.xml piggu-identity/
COPY piggu-finance/pom.xml piggu-finance/
COPY piggu-rewards/pom.xml piggu-rewards/
COPY piggu-lifestyle/pom.xml piggu-lifestyle/
COPY piggu-media/pom.xml piggu-media/
COPY piggu-banking/pom.xml piggu-banking/
COPY piggu-cobertura/pom.xml piggu-cobertura/
RUN mvn -B -q dependency:go-offline -DskipTests

COPY . .
ARG MODULO
RUN mvn -B -q -pl ${MODULO} -am package -DskipTests -Dcheckstyle.skip

FROM eclipse-temurin:24-jre-alpine
# Liga a imagem publicada no GitHub (ghcr.io) ao repositorio.
LABEL org.opencontainers.image.source=https://github.com/Piggubr/cofrinho-app
WORKDIR /app
ARG MODULO
COPY --from=build /app/${MODULO}/target/*.jar app.jar

# OCR dos recibos: so o finance precisa do Tesseract (portugues e ingles).
RUN if [ "$MODULO" = "piggu-finance" ]; then \
        apk add --no-cache tesseract-ocr tesseract-ocr-data-por tesseract-ocr-data-eng; \
    fi

# Usuario sem privilegios: um processo comprometido nao vira root no container.
RUN addgroup -S piggu && adduser -S piggu -G piggu
USER piggu

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
