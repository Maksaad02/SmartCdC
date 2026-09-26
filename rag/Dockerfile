# ---- Build ----
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/

# Tests executes par la CI (./mvnw verify), pas dans le build de l'image.
RUN ./mvnw -B -q clean package -DskipTests

# ---- Runtime ----
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S app && adduser -S -G app -u 10001 app

WORKDIR /app
COPY --from=build --chown=app:app /app/target/*.jar app.jar

USER app

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError" \
    SPRING_PROFILES_ACTIVE=prod

# Le port suit $PORT (plateformes type Render) et vaut 8082 par defaut.
EXPOSE 8082

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://127.0.0.1:9090/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
