FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
COPY target/trading-service-1.0.0-SNAPSHOT.jar app.jar
EXPOSE 9002
ENTRYPOINT ["java", "-jar", "app.jar"]
