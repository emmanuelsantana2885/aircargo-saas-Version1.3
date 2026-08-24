FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY docker-jars/notification-service.jar app.jar
EXPOSE 9100
ENTRYPOINT ["java", "-Xmx512m", "-jar", "app.jar"]
