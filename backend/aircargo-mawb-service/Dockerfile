FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY docker-jars/mawb-service.jar app.jar
EXPOSE 9095
ENTRYPOINT ["java", "-Xmx512m", "-jar", "app.jar"]
