FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM quay.io/keycloak/keycloak:26.6.1
COPY --from=builder /app/target/keycloak-2fa-messaging-authenticator-*.jar /opt/keycloak/providers/
RUN /opt/keycloak/bin/kc.sh build
EXPOSE 8080
