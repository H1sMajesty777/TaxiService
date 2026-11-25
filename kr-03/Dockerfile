FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY kr-01/pom.xml kr-01/pom.xml
COPY kr-02/pom.xml kr-02/pom.xml
COPY kr-03/pom.xml kr-03/pom.xml
COPY kr-01/src kr-01/src
COPY kr-03/src kr-03/src
RUN mvn -B -ntp -pl kr-03 -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S taxi && adduser -S taxi -G taxi
COPY --from=build /workspace/kr-03/target/taxi-backend-1.0.0.jar /app/taxi.jar
USER taxi
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/taxi.jar"]
