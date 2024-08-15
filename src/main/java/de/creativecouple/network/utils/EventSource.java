package de.creativecouple.network.utils;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.Value;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static de.creativecouple.network.utils.EventSource.Status.CLOSED;
import static de.creativecouple.network.utils.EventSource.Status.CONNECTED;
import static de.creativecouple.network.utils.EventSource.Status.CONNECTING;
import static de.creativecouple.network.utils.EventSource.Status.DISCONNECTED;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;


public class EventSource implements AutoCloseable, Closeable {

    private static final Pattern linePattern = Pattern.compile("\uFEFF?([^:]+)?(?:: ?)?(.*)");

    private static final Map<String, String> defaultRequestHeaders = new HashMap<>();

    static {
        defaultRequestHeaders.put("Accept", "text/event-stream, text/plain;q=0.9, text/*;q=0.5");
        defaultRequestHeaders.put("Accept-Encoding", "identity");
        defaultRequestHeaders.put("Cache-Control", "no-store");
    }

    public EventSource(String uri) {
        this(URI.create(uri));
    }

    public EventSource(URI uri) {
        this(ignore -> uri);
        this.uri = uri;
    }

    private final Object internalLock = new Object();

    public EventSource(Function<String, URI> uriFactory) {
        thread = new Thread(() -> {
            try {
                loop:
                while (!Thread.interrupted()) {
                    status = DISCONNECTED;
                    while (!wantsToConnect) {
                        synchronized (internalLock) {
                            if (!wantsToConnect) {
                                internalLock.wait();
                            }
                        }
                    }
                    status = CONNECTING;
                    int timeout = readTimeout;
                    try {
                        uri = uriFactory.apply(lastEventID);
                        final Map<String, String> requestHeaders = new HashMap<>(defaultRequestHeaders);
                        ofNullable(lastEventID).ifPresent(id -> requestHeaders.put("Last-Event-ID", id));
                        ofNullable(onBeforeOpen).ifPresent(l -> l.accept(requestHeaders));
                        final URLConnection connection = uri.toURL().openConnection();
                        requestHeaders.forEach(connection::setRequestProperty);
                        connection.setConnectTimeout(timeout);
                        connection.setReadTimeout(timeout);
                        try (InputStream inputStream = connection.getInputStream()) {
                            status = CONNECTED;
                            ofNullable(onOpen).ifPresent(l -> l.accept(uri));
                            actualRetryMillis = connection.getHeaderFieldInt("Retry-After", actualRetryMillis);
                            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, UTF_8));
                            for (String line; (line = reader.readLine()) != null; ) {
                                processTextEventStreamLine(line);
                                if (!wantsToConnect) {
                                    continue loop;
                                }
                            }
                        }
                    } catch (IOException e) {
                        ofNullable(onError).ifPresent(l -> l.accept(e));
                    }
                    Thread.sleep(retryMillis());
                }
            } catch (InterruptedException ignored) {
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    @Getter
    @Setter
    private Consumer<Exception> onError;

    @Getter
    @Setter
    private Consumer<URI> onOpen;

    @Getter
    @Setter
    private Consumer<Map<String, String>> onBeforeOpen;

    private Consumer<Message> onMessage;

    @Getter
    private URI uri;

    @Getter
    private Status status = DISCONNECTED;

    private final Map<String, List<Consumer<Message>>> listeners = new ConcurrentHashMap<>();

    private boolean wantsToConnect = false;
    private final Thread thread;

    @Getter
    private String lastEventID = null;

    @Setter
    private int defaultRetryMillis = 30_000;

    private int actualRetryMillis = -1;

    @Setter
    private int readTimeout = 60_000;

    private String nextEventType = "";
    private final StringBuilder nextEventData = new StringBuilder();

    @Override
    public void close() {
        synchronized (internalLock) {
            status = CLOSED;
            wantsToConnect = false;
            thread.interrupt();
        }
    }

    public int retryMillis() {
        return actualRetryMillis < 0 ? defaultRetryMillis : actualRetryMillis;
    }

    public EventSource onMessage(Consumer<Message> listener) {
        this.onMessage = listener;
        checkWantsToConnect();
        return this;
    }

    public void addEventListener(String type, @NonNull Consumer<Message> listener) {
        listeners.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(listener);
        checkWantsToConnect();
    }

    public void removeEventListener(String type, @NonNull Consumer<Message> listener) {
        listeners.computeIfPresent(type, (k, l) -> {
            l.remove(listener);
            return l.isEmpty() ? null : l;
        });
        checkWantsToConnect();
    }

    private void checkWantsToConnect() {
        synchronized (internalLock) {
            wantsToConnect = status != CLOSED && (onMessage != null || !listeners.isEmpty());
            internalLock.notify();
        }
    }

    private void processTextEventStreamLine(String line) {
        if (line.isEmpty()) {
            String type = nextEventType.isEmpty() ? "message" : nextEventType;
            Message event = new Message(lastEventID, type, nextEventData.toString());
            ofNullable(onMessage).ifPresent(l -> l.accept(event));
            listeners.getOrDefault(type, emptyList()).forEach(l -> l.accept(event));
            nextEventType = "";
            nextEventData.setLength(0);
            return;
        }
        Matcher matcher = linePattern.matcher(line);
        if (!matcher.matches()) {
            return;
        }
        String key = matcher.group(1);
        String value = matcher.group(2);
        if (key == null) {
            ofNullable(onMessage).ifPresent(l -> l.accept(new Message(null, null, value)));
            return;
        }
        switch (key) {
            case "event":
                nextEventType = value;
                break;
            case "data":
                if (nextEventData.length() > 0) {
                    nextEventData.append('\n');
                }
                nextEventData.append(value);
                break;
            case "id":
                if (value.indexOf('\u0000') < 0) {
                    lastEventID = value;
                }
                break;
            case "retry":
                try {
                    defaultRetryMillis = Integer.parseInt(value);
                } catch (NumberFormatException ignored) {
                }
                break;
        }
    }

    @Value
    public static class Message {
        String lastEventId;
        String type;
        String data;
    }

    public enum Status {DISCONNECTED, CONNECTING, CONNECTED, CLOSED}
}