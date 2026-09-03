FROM node:20-bookworm-slim

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        openjdk-17-jre-headless \
        net-tools \
        curl \
    && rm -rf /var/lib/apt/lists/*

RUN npm install -g bing-cn-mcp

WORKDIR /app
CMD ["java", "-jar", "/app/blog-agent.0.0.1.jar"]
