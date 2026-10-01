# Build unico para todos os servicos: o modulo desejado entra por ARG.
# Um Dockerfile so evita varios arquivos quase identicos saindo de sincronia.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# As dependencias mudam bem menos que o codigo: copiar so os poms primeiro
# faz o cache do Docker sobreviver a maioria das alteracoes.
COPY pom.xml .
COPY piggu-common/pom.xml piggu-common/
COPY piggu-gateway/pom.xml piggu-gateway/
COPY piggu-identity/pom.xml piggu-identity/
COPY piggu-finance/pom.xml piggu-finance/
COPY piggu-rewards/pom.xml piggu-rewards/
COPY piggu-lifestyle/pom.xml piggu-lifestyle/
COPY piggu-media/pom.xml piggu-media/
COPY piggu-banking/pom.xml piggu-banking/
RUN mvn -B -q dependency:go-offline -DskipTests || true

COPY . .
ARG MODULO
RUN mvn -B -q -pl ${MODULO} -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
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
