# Multi-stage Docker build for MyBaaS
FROM gradle:8.5-jdk21 AS build

# Set working directory
WORKDIR /app

# Copy Gradle files
COPY build.gradle.kts settings.gradle.kts gradle.properties* ./
COPY gradle/ ./gradle/

# Copy source code
COPY src/ ./src/

# Build the project
RUN gradle clean build -x test

# Runtime stage
FROM eclipse-temurin:21-jdk

RUN curl -sL https://deb.nodesource.com/setup_24.x | bash -

# Install required packages
RUN apt-get update && apt-get install -y \
    nodejs \
    curl \
    net-tools \
    unzip \
    postgresql-client \
    && rm -rf /var/lib/apt/lists/*

# Install pnpm globally
RUN npm install -g pnpm

# Set working directory
WORKDIR /app

# Copy built JAR from build stage
COPY --from=build /app/build/libs/*.jar app.jar

# Copy OAuth2 mock server
COPY oauth2-mock-server/ ./oauth2-mock-server/
WORKDIR /app/oauth2-mock-server
RUN pnpm install

# Copy HTTP test files and configuration
WORKDIR /app
COPY api-tests/ ./api-tests/
COPY api-tests.http .
COPY app-test.properties app.properties

# Download and install IntelliJ HTTP client
RUN curl -f -L -o ijhttp.zip "https://jb.gg/ijhttp/latest" \
    && unzip ijhttp.zip -d /opt/ \
    && chmod +x /opt/ijhttp/ijhttp \
    && ln -s /opt/ijhttp/ijhttp /usr/local/bin/ijhttp \
    && rm ijhttp.zip

# Copy startup script
COPY startup.sh /app/startup.sh
RUN chmod +x /app/startup.sh

EXPOSE 7070 7070

CMD ["/app/startup.sh"]