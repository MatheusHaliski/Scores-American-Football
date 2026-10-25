# syntax=docker/dockerfile:1
# API do Scores: American Football. Bytecode Java 21 (maven.compiler.release) rodando numa JVM LTS 25 (D-5).
# O CI roda os testes; a imagem só empacota (deploy só depois do CI verde).

FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -DskipTests -pl scores-af-bootstrap -am package \
 && cp scores-af-bootstrap/target/scores-af-bootstrap-*.jar /src/app.jar

FROM eclipse-temurin:25-jre
RUN groupadd --system --gid 10001 scores && useradd --system --uid 10001 --gid scores --no-create-home scores
WORKDIR /app
COPY --from=build --chown=scores:scores /src/app.jar /app/app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
