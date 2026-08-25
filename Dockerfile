# syntax=docker/dockerfile:1

# ---- Build stage ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Cache Gradle wrapper/dependency downloads separately from source changes
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --version --no-daemon

COPY src ./src
RUN ./gradlew build -x test --no-daemon \
    && find build/libs -maxdepth 1 -name "*.jar" ! -name "*-plain.jar" -exec cp {} app.jar \;

# ---- Run stage ----
FROM eclipse-temurin:21-jre AS run
WORKDIR /app

RUN addgroup --system spring && adduser --system --ingroup spring spring
USER spring:spring

COPY --from=build --chown=spring:spring /app/app.jar app.jar

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
