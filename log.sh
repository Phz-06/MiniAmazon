#!/bin/bash

CONTAINER=$1

if [ -z "$CONTAINER" ]; then
  echo "Usage: $0 <container-name-or-id>"
  exit 1
fi

# Get full container ID
ID=$(sudo docker inspect --format="{{.Id}}" $CONTAINER 2>/dev/null)

if [ -z "$ID" ]; then
  echo "Error: container $CONTAINER not found."
  exit 1
fi

# Clear the logs
echo "Clearing logs for container $CONTAINER..."
sudo truncate -s 0 /var/lib/docker/containers/$ID/*-json.log

# Attach to fresh logs
echo "Showing fresh logs for $CONTAINER..."
sudo docker logs -f $CONTAINER
