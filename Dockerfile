FROM eclipse-temurin:21-jre
LABEL authors="quentin-michon/gianni-bee"

WORKDIR /app

COPY target/dai-work-3-1.0-SNAPSHOT.jar /app/api.jar
COPY src/main/java/ch/heigvd/datas/ /app/src/main/java/ch/heigvd/datas

EXPOSE 8080

CMD ["sh", "-c", "java -jar /app/api.jar"]
