FROM eclipse-temurin:21-jre

WORKDIR /app

COPY build/libs/app.jar app.jar

EXPOSE 7777

CMD ["java", "-jar", "app.jar"]