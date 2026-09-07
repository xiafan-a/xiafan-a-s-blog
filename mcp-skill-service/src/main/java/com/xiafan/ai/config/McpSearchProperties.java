package com.xiafan.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "app.mcp")
public class McpSearchProperties {

    private int timeoutSeconds = 120;
    private Map<String, ServerConfig> servers = new LinkedHashMap<>();

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public Map<String, ServerConfig> getServers() {
        return servers;
    }

    public void setServers(Map<String, ServerConfig> servers) {
        this.servers = servers;
    }

    public ServerConfig server(String name) {
        ServerConfig config = servers.get(name);
        if (config == null) {
            return new ServerConfig("npx", new ArrayList<>(List.of("-y", "bing-cn-mcp")));
        }
        return config;
    }

    public static class ServerConfig {
        private String command = "npx";
        private List<String> args = new ArrayList<>(List.of("-y", "bing-cn-mcp"));

        public ServerConfig() {
        }

        public ServerConfig(String command, List<String> args) {
            this.command = command;
            this.args = args;
        }

        public String getCommand() {
            return command;
        }

        public void setCommand(String command) {
            this.command = command;
        }

        public List<String> getArgs() {
            return args;
        }

        public void setArgs(List<String> args) {
            this.args = args;
        }
    }
}
