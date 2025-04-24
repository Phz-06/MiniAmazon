FROM gradle:8.4-jdk17
WORKDIR /app

COPY . .
EXPOSE 8080
CMD ["gradle", "bootRun"]