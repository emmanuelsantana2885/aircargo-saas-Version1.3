# Multi-stage build for aircargo-flight-service
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build
COPY backend/ .
RUN mvn dependency:go-offline -B -q -pl aircargo-flight-service -am
RUN mvn clean package -DskipTests -q -pl aircargo-flight-service -am

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -g 1000 -S appgroup && adduser -u 1000 -S appuser -G appgroup
WORKDIR /app
COPY --from=builder /build/aircargo-flight-service/target/aircargo-flight-service-*.jar app.jar
RUN chown -R appuser:appgroup /app
USER appuser
EXPOSE 9093
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dserver.port=${PORT:-9093} -jar app.jar"]
