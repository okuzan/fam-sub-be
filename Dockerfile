# ---- Builder Stage ----
FROM eclipse-temurin:21-jre-alpine AS builder
WORKDIR /app
COPY build/libs/*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted

# ---- Runtime Stage ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=builder /app/extracted/dependencies/ ./
COPY --from=builder /app/extracted/spring-boot-loader/ ./
COPY --from=builder /app/extracted/snapshot-dependencies/ ./
COPY --from=builder /app/extracted/application/ ./

ENTRYPOINT ["java", "-Djava.net.preferIPv4Stack=true", "-cp", ".", "org.springframework.boot.loader.launch.JarLauncher"]
