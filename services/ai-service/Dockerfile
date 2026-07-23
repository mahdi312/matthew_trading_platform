FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /app
RUN apk add --no-cache maven
COPY pom.xml .
COPY shared ./shared
COPY infra ./infra
COPY services ./services
RUN mvn -pl services/ai-service -am package -DskipTests -B

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=build /app/services/ai-service/target/ai-service.jar app.jar
EXPOSE 8089
ENTRYPOINT ["java", "-jar", "app.jar"]
