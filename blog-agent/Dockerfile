FROM openjdk:17-jdk-slim

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        net-tools \
        curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
CMD ["java", "-jar", "/app/blog-agent.0.0.1.jar"]
