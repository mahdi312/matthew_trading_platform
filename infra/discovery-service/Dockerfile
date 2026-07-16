FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /app
RUN apk add --no-cache maven
COPY pom.xml .
COPY shared ./shared
COPY infra ./infra
COPY services ./services
RUN mvn -pl infra/discovery-service -am package -DskipTests -B

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
RUN apk add --no-cache curl
COPY --from=build /app/infra/discovery-service/target/discovery-service.jar app.jar
EXPOSE 8761
HEALTHCHECK --interval=15s --timeout=10s --retries=5 \
  CMD curl -f http://localhost:8761/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
