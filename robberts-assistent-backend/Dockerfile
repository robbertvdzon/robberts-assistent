# syntax=docker/dockerfile:1.7

FROM maven:3.9.10-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src src
RUN mvn -q -DskipTests package

# glibc-base (NIET alpine/musl): firebase-admin/Firestore gebruikt gRPC's netty-tcnative
# (BoringSSL), die voor glibc gebouwd is en op musl crasht met SIGSEGV bij het laden van de
# SSLContext. Op Alpine crashte de backend daardoor in een CrashLoopBackOff zodra Firestore
# initialiseerde. Debian-based JRE lost dat op.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/robberts-assistent-backend-0.0.1-SNAPSHOT.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
