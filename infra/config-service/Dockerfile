FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /app
RUN apk add --no-cache maven
COPY pom.xml .
COPY shared ./shared
COPY infra ./infra
COPY services ./services
RUN mvn -pl infra/config-service -am package -DskipTests -B

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
RUN apk add --no-cache curl
COPY --from=build /app/infra/config-service/target/config-service.jar app.jar
COPY infra/config-repo /config-repo
EXPOSE 8888
HEALTHCHECK --interval=15s --timeout=10s --retries=5 \
  CMD curl -f http://localhost:8888/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.cloud.config.server.native.search-locations=file:/config-repo"]
