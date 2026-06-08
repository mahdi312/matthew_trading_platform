# Build stage
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN apk add --no-cache maven && mvn -q -DskipTests package

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/TradingPlatformApp-*.jar app.jar
ENV SPRING_PROFILES_ACTIVE=docker
ENV JAVA_OPTS="--add-opens javafx.graphics/com.sun.javafx.application=ALL-UNNAMED --add-opens javafx.base/com.sun.javafx.runtime=ALL-UNNAMED -Xmx1024m"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
