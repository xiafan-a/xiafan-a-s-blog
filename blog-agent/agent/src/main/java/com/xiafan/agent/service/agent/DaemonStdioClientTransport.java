package com.xiafan.agent.service.agent;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.client.transport.ServerParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Stdio MCP transport with daemon reader/writer threads. The official SDK transport
 * works, but its non-daemon scheduler threads prevent a Spring Boot JVM from exiting.
 */
class DaemonStdioClientTransport implements McpClientTransport {

    private static final Logger log = LoggerFactory.getLogger(DaemonStdioClientTransport.class);

    private final ServerParameters params;
    private final McpJsonMapper jsonMapper;
    private final Sinks.Many<McpSchema.JSONRPCMessage> inbound =
            Sinks.many().unicast().onBackpressureBuffer();
    private final BlockingQueue<McpSchema.JSONRPCMessage> outbound = new LinkedBlockingQueue<>();

    private Process process;
    private volatile boolean closing;
    private Thread readerThread;
    private Thread writerThread;
    private Thread errorThread;

    DaemonStdioClientTransport(ServerParameters params, McpJsonMapper jsonMapper) {
        this.params = params;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public Mono<Void> connect(Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>> handler) {
        return Mono.<Void>fromRunnable(() -> {
            List<String> command = new ArrayList<>();
            command.add(params.getCommand());
            command.addAll(params.getArgs());

            ProcessBuilder builder = new ProcessBuilder(command);
            builder.environment().putAll(params.getEnv());
            try {
                process = builder.start();
            } catch (IOException e) {
                throw new RuntimeException("Failed to start MCP server: " + command, e);
            }

            inbound.asFlux()
                    .flatMap(message -> Mono.just(message).transform(handler))
                    .subscribe();
            readerThread = daemonThread(this::readLoop, "mcp-stdio-reader");
            writerThread = daemonThread(this::writeLoop, "mcp-stdio-writer");
            errorThread = daemonThread(this::errorLoop, "mcp-stdio-error");
            readerThread.start();
            writerThread.start();
            errorThread.start();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
        outbound.add(message);
        return Mono.empty();
    }

    @Override
    public <T> T unmarshalFrom(Object data, TypeRef<T> typeRef) {
        return jsonMapper.convertValue(data, typeRef);
    }

    @Override
    public Mono<Void> closeGracefully() {
        return Mono.<Void>fromRunnable(() -> {
            closing = true;
            if (process != null) {
                process.destroy();
            }
            interrupt(readerThread);
            interrupt(writerThread);
            interrupt(errorThread);
            if (process != null) {
                try {
                    process.waitFor(3, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                closeSilently(process.getInputStream());
                closeSilently(process.getOutputStream());
                closeSilently(process.getErrorStream());
            }
            inbound.tryEmitComplete();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private void readLoop() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while (!closing && (line = reader.readLine()) != null) {
                try {
                    McpSchema.JSONRPCMessage message = McpSchema.deserializeJsonRpcMessage(jsonMapper, line);
                    if (!inbound.tryEmitNext(message).isSuccess()) {
                        break;
                    }
                } catch (Exception e) {
                    if (!closing) {
                        log.warn("Failed to read MCP message: {}", e.getMessage());
                    }
                    break;
                }
            }
        } catch (IOException e) {
            if (!closing) {
                log.warn("MCP stdout closed: {}", e.getMessage());
            }
        } finally {
            inbound.tryEmitComplete();
        }
    }

    private void writeLoop() {
        while (!closing) {
            McpSchema.JSONRPCMessage message;
            try {
                message = outbound.take();
            } catch (InterruptedException e) {
                break;
            }
            if (closing) {
                break;
            }
            try {
                writeMessage(message);
            } catch (Exception e) {
                log.warn("Failed to write MCP message: {}", e.getMessage());
                break;
            }
        }
    }

    private void errorLoop() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while (!closing && (line = reader.readLine()) != null) {
                log.info("MCP stderr: {}", line);
            }
        } catch (IOException ignored) {
            // process is shutting down
        }
    }

    private void writeMessage(McpSchema.JSONRPCMessage message) throws Exception {
        String json = jsonMapper.writeValueAsString(message);
        json = json.replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n");
        OutputStream output = process.getOutputStream();
        synchronized (output) {
            output.write(json.getBytes(StandardCharsets.UTF_8));
            output.write('\n');
            output.flush();
        }
    }

    private static Thread daemonThread(Runnable task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        return thread;
    }

    private static void interrupt(Thread thread) {
        if (thread != null) {
            thread.interrupt();
        }
    }

    private static void closeSilently(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
            // already closed or shutting down
        }
    }
}
