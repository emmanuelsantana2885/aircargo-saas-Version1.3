FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build
COPY . .
RUN mvn package -B -pl aircargo-flight-service -am -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /build/aircargo-flight-service/target/*.jar app.jar
EXPOSE 9093
ENTRYPOINT ["java", "-jar", "app.jar"]
