#!/bin/bash

if [ "$1" == "full" ]; then
    sudo docker-compose down --volumes --remove-orphans
    sudo docker-compose up -d --build --force-recreate
else
    sudo docker-compose restart web
fi
