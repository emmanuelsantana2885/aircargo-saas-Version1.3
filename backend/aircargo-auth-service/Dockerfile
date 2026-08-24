FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY docker-jars/auth-service.jar app.jar
EXPOSE 9092
ENTRYPOINT ["java", "-Xmx512m", "-jar", "app.jar"]
