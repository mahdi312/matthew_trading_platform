FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /app
RUN apk add --no-cache maven
COPY pom.xml .
COPY shared ./shared
COPY infra ./infra
COPY services ./services
RUN mvn -pl infra/gateway-service -am package -DskipTests -B

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
RUN apk add --no-cache curl
COPY --from=build /app/infra/gateway-service/target/gateway-service.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
