FROM maven:3-eclipse-temurin-17 AS build  
COPY src /usr/src/app/src  
COPY pom.xml /usr/src/app
WORKDIR /usr/src/app
RUN mvn package

FROM eclipse-temurin:17

COPY --from=build /usr/src/app/target/otel-log-1.0-SNAPSHOT-jar-with-dependencies.jar /otel-log.jar

ENTRYPOINT ["java", "-jar", "/otel-log.jar"]
