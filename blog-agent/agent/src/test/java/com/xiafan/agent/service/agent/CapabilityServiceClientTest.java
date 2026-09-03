package com.xiafan.agent.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.xiafan.agent.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapabilityServiceClientTest {

    @Test
    void listsAndExecutesToolsOverHttp() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/tools", exchange -> {
            byte[] body = """
                    {"tools":[{"name":"get_Date","display_name":"Get current date",
                    "description":"Get the current date","category":"date","parameters":[],
                    "enabled":true,"requires_auth":false,"timeout":60,"built_in":true}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/api/v1/tools/get_Date/execute", exchange -> {
            byte[] body = """
                    {"call_id":"test-call","tool_name":"get_Date","success":true,
                    "result":{"date":"2026-09-03"},"error":null,"execution_time":0.001}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            AppProperties props = new AppProperties();
            props.getCapabilityService().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            CapabilityServiceClient client = new CapabilityServiceClient(props, new ObjectMapper());

            List<Map<String, Object>> tools = client.listTools();
            assertEquals(1, tools.size());
            assertEquals("get_Date", tools.get(0).get("name"));

            Map<String, Object> result = client.executeTool("get_Date", Map.of());
            assertEquals(true, result.get("success"));
            assertTrue(result.containsKey("result"));
        } finally {
            server.stop(0);
        }
    }
}
