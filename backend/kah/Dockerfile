FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

ENV TZ=Asia/Shanghai

COPY target/kah-0.0.1-SNAPSHOT.jar /app/app.jar

RUN mkdir -p /app/storage

EXPOSE 8080

ENTRYPOINT ["java","-Xms256m","-Xmx768m","-jar","/app/app.jar"]
