# ---- Builder Stage ----
FROM gradle:8.5.0-jdk21 AS builder
WORKDIR /builder

# Copy gradle files first for better caching
COPY build.gradle.kts settings.gradle.kts gradlew gradlew.bat ./
COPY gradle ./gradle
RUN ./gradlew --version --no-daemon --max-workers=1

# Copy source code
COPY src ./src

# Build with optimizations
RUN ./gradlew bootJar -x test --no-daemon --max-workers=1 --parallel

# ---- Layer Extraction Stage ----
FROM eclipse-temurin:21-jdk-alpine AS layertools-extractor
WORKDIR /app
COPY --from=builder /builder/build/libs/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# ---- Runtime Stage ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=layertools-extractor /app/dependencies/ ./
COPY --from=layertools-extractor /app/spring-boot-loader/ ./
COPY --from=layertools-extractor /app/snapshot-dependencies/ ./
COPY --from=layertools-extractor /app/application/ ./

ENTRYPOINT ["java", "-Djava.net.preferIPv4Stack=true", "org.springframework.boot.loader.launch.JarLauncher"]
