FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
COPY campus-common campus-common
COPY campus-user-feign campus-user-feign
COPY campus-trade-feign campus-trade-feign
COPY campus-forum-feign campus-forum-feign
COPY campus-ai-feign campus-ai-feign
COPY campus-user campus-user
COPY campus-trade campus-trade
COPY campus-forum campus-forum
COPY campus-ai campus-ai
COPY campus-admin campus-admin
COPY campus-gateway campus-gateway
RUN mvn clean package -DskipTests -pl campus-admin -am

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/campus-admin/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
