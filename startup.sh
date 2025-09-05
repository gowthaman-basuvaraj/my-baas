#!/bin/bash
set -e

echo "Starting OAuth2 Mock Server..."
cd /app/oauth2-mock-server
node server.js &
OAUTH_PID=$!

echo "Waiting for OAuth2 server to be ready..."
sleep 5

echo "Starting MyBaaS Application..."
cd /app
java -jar app.jar &
APP_PID=$!

echo "Waiting for application to be ready..."
sleep 10

echo "Running HTTP tests..."
ijhttp api-tests.http --env-file http-client.private.env.json --env-name local

# Keep the container running
echo "All services started successfully!"
wait $OAUTH_PID $APP_PID