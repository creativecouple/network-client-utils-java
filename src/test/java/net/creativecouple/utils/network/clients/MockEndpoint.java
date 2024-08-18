package net.creativecouple.utils.network.clients;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import static java.nio.charset.StandardCharsets.UTF_8;

public class MockEndpoint implements Closeable, AutoCloseable {

    private static final HttpServer server;

    static {
        try {
            server = HttpServer.create(new InetSocketAddress(0), 0);
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private final String path = "/" + UUID.randomUUID() + "/";

    public final URI uri = URI.create("http://localhost:" + server.getAddress().getPort() + path);

    private final List<HttpExchange> exchanges = new CopyOnWriteArrayList<>();
    public final List<HttpExchange> requests = Collections.unmodifiableList(exchanges);

    private final StringBuilder buffer = new StringBuilder();
    public final Map<String, String> headers = new HashMap<>();
    private int failStatus = 0;
    private String failMessage = null;

    public MockEndpoint() {
        server.createContext(path, this::handleHttpRequest);
        headers.put("Content-Type", "text/event-stream");
    }

    private void handleHttpRequest(HttpExchange exchange) throws IOException {
        exchanges.add(exchange);
        headers.forEach(exchange.getResponseHeaders()::add);
        if (failStatus > 0) {
            byte[] content = failMessage.getBytes(UTF_8);
            exchange.sendResponseHeaders(failStatus, content.length);
            OutputStream body = exchange.getResponseBody();
            body.write(content);
            body.close();
            return;
        }
        exchange.sendResponseHeaders(200, 0);
        try (OutputStream output = exchange.getResponseBody()) {
            while (!Thread.interrupted()) {
                synchronized (buffer) {
                    String content = buffer.toString();
                    output.write(content.getBytes(UTF_8));
                    output.flush();
                    buffer.setLength(0);
                    buffer.wait();
                }
            }
        } catch (InterruptedException ignored) {
        }
    }

    @Override
    public void close() {
        server.removeContext(path);
    }

    public void println(String line) {
        synchronized (buffer) {
            buffer.append(line).append("\n");
            buffer.notifyAll();
        }
    }

    public void fail(int statusCode, String message) {
        failStatus = statusCode;
        failMessage = message;
    }
}
