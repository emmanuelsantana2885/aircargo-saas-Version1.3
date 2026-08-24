FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY docker-jars/flight-service.jar app.jar
EXPOSE 9093
ENTRYPOINT ["java", "-Xmx512m", "-jar", "app.jar"]
