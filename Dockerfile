# Stage 1: Build the JAR file using Maven
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Run the lightweight JRE runtime image
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 3636

# JVM memory tuning flags (-Xmx360m) to fit safely within Render's 512MB free RAM limit
ENTRYPOINT ["java", "-Xmx360m", "-Xms256m", "-XX:+UseSerialGC", "-jar", "app.jar"]