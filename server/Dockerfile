FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app
COPY .. /app
RUN ./gradlew :server:bootJar --no-daemon

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/server/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
