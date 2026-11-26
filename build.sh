#!/bin/bash
# Rebuild images and start from an EMPTY database (the data volume is deleted).
docker compose down --volumes --remove-orphans
docker compose up -d --build --force-recreate
