# Runtime-only image: Gradle never runs inside Docker.
# Build the jar first (CI or locally):  ./gradlew bootJar -x test
# then:                                 docker build -t jade-ai-bot .
#
# The boot jar is split into Spring Boot layers so the ~310 MB dependency layer
# stays cached across deploys and only the small application layer is rebuilt.

FROM eclipse-temurin:17-jre-jammy AS layers
WORKDIR /layers
COPY build/libs/JadeAiBot.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Install dependencies for Playwright (Chromium needs glibc, so stay on Debian/Ubuntu, not Alpine)
RUN apt-get update && apt-get install -y \
    curl \
    wget \
    ca-certificates \
    fonts-liberation \
    libasound2 \
    libatk-bridge2.0-0 \
    libatk1.0-0 \
    libcups2 \
    libdbus-1-3 \
    libgbm1 \
    libgtk-3-0 \
    libnspr4 \
    libnss3 \
    libxcomposite1 \
    libxdamage1 \
    libxrandr2 \
    xdg-utils \
    && rm -rf /var/lib/apt/lists/*

# No need to install Node.js or npm - Playwright Java bundles everything it needs
# Playwright Java will automatically download and manage browsers at runtime

# Least-frequently-changing layers first
COPY --from=layers /layers/dependencies/ ./
COPY --from=layers /layers/spring-boot-loader/ ./
COPY --from=layers /layers/snapshot-dependencies/ ./
COPY --from=layers /layers/application/ ./

# Create a non-root user for security
#RUN groupadd -g 1001 appgroup && \
#    useradd -u 1001 -g appgroup -M appuser && \
#    chown -R appuser:appgroup /app
#USER appuser

# Expose the port
EXPOSE 8080

# Set default environment variables
ENV JAVA_OPTS="-Xmx512m -Xms256m"

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run the application. `exec` makes java PID 1 so `docker stop` triggers a graceful
# shutdown instead of waiting 10s for SIGKILL.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
