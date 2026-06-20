FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8081 20880
ENTRYPOINT ["java", "-jar", "app.jar"]
