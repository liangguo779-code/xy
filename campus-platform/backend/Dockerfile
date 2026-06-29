FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
COPY campus-common campus-common
COPY campus-user campus-user
COPY campus-trade campus-trade
COPY campus-forum campus-forum
COPY campus-ai campus-ai
COPY campus-admin campus-admin
COPY campus-app campus-app
RUN mvn clean package -DskipTests -pl campus-app -am

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/campus-app/target/*.jar app.jar
EXPOSE 9000
ENTRYPOINT ["java", "-jar", "app.jar"]
