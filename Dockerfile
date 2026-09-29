FROM eclipse-temurin:25.0.4_7-jdk-noble AS jdk-build

WORKDIR /app

COPY gradle ./gradle
COPY gradlew ./gradlew
RUN chmod +x gradlew

COPY build.gradle ./build.gradle
COPY settings.gradle ./settings.gradle
RUN ./gradlew dependencies --no-daemon

COPY src ./src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:25.0.4_7-jre-noble
LABEL authors="limecoding"

WORKDIR /app

COPY --from=jdk-build /app/build/libs/llmRouter-0.0.1.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]