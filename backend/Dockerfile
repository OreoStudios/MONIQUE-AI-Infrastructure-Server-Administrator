FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 monique
WORKDIR /app
COPY --from=build /build/target/monique-backend-*.jar app.jar
USER monique
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
