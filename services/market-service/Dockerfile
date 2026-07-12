FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
COPY target/market-service-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 9001
ENTRYPOINT ["java", "-jar", "app.jar"]
