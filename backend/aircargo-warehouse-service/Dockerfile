FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY docker-jars/warehouse-service.jar app.jar
EXPOSE 9096
ENTRYPOINT ["java", "-Xmx512m", "-jar", "app.jar"]
