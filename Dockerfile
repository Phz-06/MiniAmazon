FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app
# Dependencies first: this layer is reused until build.gradle changes.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 app
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
USER app
EXPOSE 8000
ENTRYPOINT ["java", "-jar", "app.jar"]
