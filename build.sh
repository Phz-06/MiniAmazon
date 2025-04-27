#!/bin/bash

sudo docker-compose down --volumes --remove-orphans
sudo docker-compose up -d --build --force-recreate
