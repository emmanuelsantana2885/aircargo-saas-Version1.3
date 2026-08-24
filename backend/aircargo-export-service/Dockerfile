FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY docker-jars/export-service.jar app.jar
EXPOSE 9099
ENTRYPOINT ["java", "-Xmx512m", "-jar", "app.jar"]
