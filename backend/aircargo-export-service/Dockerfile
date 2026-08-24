FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/aircargo-export-service-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java -Xms256m -Xmx512m -Dserver.port=$PORT -jar app.jar"]