FROM node:20-bookworm-slim AS node

FROM openjdk:17-jdk-slim
COPY --from=node /usr/local/bin/ /usr/local/bin/
COPY --from=node /usr/local/lib/node_modules/ /usr/local/lib/node_modules/
RUN npm install -g bing-cn-mcp \
    && rm -rf /root/.npm
WORKDIR /app
EXPOSE 8200
CMD ["java", "-jar", "/app/mcp-skill-service.0.0.1.jar"]
