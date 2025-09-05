#!/bin/bash
set -e

echo "list of env..."
env

echo "Starting OAuth2 Mock Server..."
cd /app/oauth2-mock-server
node -v
node server.js &
OAUTH_PID=$!

echo "Waiting for OAuth2 server to be ready..."
sleep 5

echo "running services"
netstat -tunlp

echo "DropDB on Start? ${DROP_DB_ON_START}"
if [ "$DROP_DB_ON_START" = "yes" ];then
  dropdb -h postgres -U postgres "${DATABASE_NAME}"
  createdb -h postgres -U postgres "${DATABASE_NAME}"
fi

echo "Starting MyBaaS Application..."
cd /app
java -jar app.jar &
APP_PID=$!

echo "Waiting for application to be ready..."
sleep 10

echo "Running HTTP tests..."
ijhttp api-tests.http --env-file /app/oauth2-mock-server/http-client.private.env.json --env dev

# Keep the container running
echo "All services started successfully!"
wait $OAUTH_PID $APP_PID