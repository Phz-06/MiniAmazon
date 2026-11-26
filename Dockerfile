# One image definition for all three services:
#   docker build --build-arg SERVICE=order-service .
# The build stage compiles every module once and is shared by the three images.
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
COPY shared/build.gradle shared/
COPY order-service/build.gradle order-service/
COPY world-gateway/build.gradle world-gateway/
COPY carrier-service/build.gradle carrier-service/
# Dependencies first: these layers are reused until a build file changes.
RUN ./gradlew --no-daemon :order-service:dependencies :world-gateway:dependencies :carrier-service:dependencies > /dev/null
COPY shared/src shared/src
COPY order-service/src order-service/src
COPY world-gateway/src world-gateway/src
COPY carrier-service/src carrier-service/src
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre
ARG SERVICE
RUN useradd --system --uid 10001 app
WORKDIR /app
COPY --from=builder /app/${SERVICE}/build/libs/*.jar app.jar
USER app
ENTRYPOINT ["java", "-jar", "app.jar"]
