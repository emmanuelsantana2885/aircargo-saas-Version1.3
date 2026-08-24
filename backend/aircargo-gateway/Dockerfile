FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY docker-jars/gateway.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Xmx512m", "-jar", "app.jar"]
