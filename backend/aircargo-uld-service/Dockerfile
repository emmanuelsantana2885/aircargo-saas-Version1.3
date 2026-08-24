FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY docker-jars/uld-service.jar app.jar
EXPOSE 9097
ENTRYPOINT ["java", "-Xmx512m", "-jar", "app.jar"]
