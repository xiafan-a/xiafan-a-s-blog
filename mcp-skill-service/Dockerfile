FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package

FROM node:20-bookworm-slim AS node

FROM openjdk:17-jdk-slim
COPY --from=node /usr/local/bin/ /usr/local/bin/
COPY --from=node /usr/local/lib/node_modules/ /usr/local/lib/node_modules/
RUN npm install -g bing-cn-mcp \
    && rm -rf /root/.npm
WORKDIR /app
COPY --from=build /build/target/mcp-skill-service-0.0.1-SNAPSHOT.jar /app/app.jar
EXPOSE 8200
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
