# syntax=docker/dockerfile:1.7
# Imagem de produção do fin-mec: um único jar servindo API (/api/**) e o SPA React.
# Três estágios para a imagem final levar só a JRE e o jar (sem Node, Maven nem código-fonte).

# 1) Frontend — o vite escreve em ../src/main/resources/static (ver web/vite.config.ts).
FROM node:22-alpine AS web
WORKDIR /build/web
COPY web/package.json web/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY web/ ./
RUN npm run build

# 2) Backend — dependências numa camada própria (cache entre builds que só mudam código).
FROM maven:3.9-eclipse-temurin-21 AS app
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
COPY --from=web /build/src/main/resources/static ./src/main/resources/static
RUN mvn -B -q -DskipTests package \
    && cp "$(find target -maxdepth 1 -name 'mec-fin-*.jar' ! -name '*.original' | head -1)" /app.jar

# 3) Runtime — usuário sem privilégio, memória proporcional ao limite do container.
FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app --home-dir /app app
WORKDIR /app
COPY --from=app /app.jar /app/app.jar
USER app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError" \
    SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
