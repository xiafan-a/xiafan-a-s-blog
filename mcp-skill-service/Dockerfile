FROM node:20 AS node

FROM openjdk:17-jdk-slim
COPY --from=node /usr/local/bin/ /usr/local/bin/
COPY --from=node /usr/local/lib/node_modules/ /usr/local/lib/node_modules/
RUN npm config set registry https://registry.npmmirror.com \
    && npm install -g bing-cn-mcp \
    && rm -rf /root/.npm
WORKDIR /app
EXPOSE 8200
CMD ["java", "-jar", "/app/mcp-skill-service.jar"]
