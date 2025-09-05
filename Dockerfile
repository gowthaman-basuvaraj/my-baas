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
FROM openjdk:21-jdk-slim

# Install required packages
RUN apt-get update && apt-get install -y \
    nodejs \
    npm \
    curl \
    unzip \
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
COPY app.properties .

# Download and install IntelliJ HTTP client
RUN curl -fsSL https://jb.gg/ijhttp/latest -o ijhttp.zip \
    && unzip ijhttp.zip -d /opt/ \
    && chmod +x /opt/ijhttp \
    && ln -s /opt/ijhttp /usr/local/bin/ijhttp \
    && rm ijhttp.zip

# Copy startup script
COPY startup.sh /app/startup.sh
RUN chmod +x /app/startup.sh

EXPOSE 7070 7070

CMD ["/app/startup.sh"]