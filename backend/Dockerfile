# ---- Build ----
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# POM d'abord : les dependances sont mises en cache tant que pom.xml ne change pas.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src

# Les tests sont executes par la CI (mvn verify) et bloquent la fusion : le build
# de l'image ne les rejoue pas.
RUN mvn -B -q clean package -DskipTests

# ---- Runtime ----
FROM eclipse-temurin:21-jre-alpine

# Utilisateur sans privileges : un jar compromis ne doit pas etre root dans le conteneur.
RUN addgroup -S app && adduser -S -G app -u 10001 app

WORKDIR /app
COPY --from=builder --chown=app:app /app/target/*.jar app.jar

USER app

# Le JVM dimensionne son tas sur la limite memoire du conteneur (75 %) et
# s'arrete franchement en cas de manque de memoire, pour que Docker le relance.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError" \
    SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://127.0.0.1:9090/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
