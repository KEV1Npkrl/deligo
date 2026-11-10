FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B -ntp package -DskipTests
FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --uid 10001 deligo
COPY --from=build /app/target/deligo-0.1.0.jar app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
