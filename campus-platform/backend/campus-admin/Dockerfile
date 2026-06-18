# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY campus-common campus-common
COPY campus-user-feign campus-user-feign
COPY campus-trade-feign campus-trade-feign
COPY campus-forum-feign campus-forum-feign
COPY campus-ai-feign campus-ai-feign
COPY campus-admin campus-admin
RUN mvn clean package -pl campus-admin -am -DskipTests

# Run stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/campus-admin/target/*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]
