FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY docker-jars/booking-service.jar app.jar
EXPOSE 9094
ENTRYPOINT ["java", "-Xmx512m", "-jar", "app.jar"]
