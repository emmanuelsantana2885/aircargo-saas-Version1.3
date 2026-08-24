FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY docker-jars/load-planning-service.jar app.jar
EXPOSE 9098
ENTRYPOINT ["java", "-Xmx512m", "-jar", "app.jar"]
