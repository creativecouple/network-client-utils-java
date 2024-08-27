/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2024 CreativeCouple
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package net.creativecouple.utils.network.clients;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.Value;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;

/**
 * A Java implementation of the EventSource API, which enables clients to receive
 * server-sent events (SSE) from a specified URI. This class provides a way to
 * listen to events from an HTTP server that supports the text/event-stream protocol.
 * <p>
 * Server-sent events (SSE) allow servers to push updates to clients over a single,
 * long-lived HTTP connection. This class is inspired by the JavaScript EventSource API,
 * which is commonly used in web browsers.
 * <p>
 * For more information on EventSource and Server-Sent Events, please refer to the
 * following resources:
 * <ul>
 *   <li><a href="https://developer.mozilla.org/en-US/docs/Web/API/EventSource">Mozilla Developer Network: EventSource</a></li>
 *   <li><a href="https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events">Mozilla Developer Network: Server-Sent Events</a></li>
 *   <li><a href="https://html.spec.whatwg.org/multipage/server-sent-events.html#server-sent-events">HTML Standard: Server-Sent Events</a></li>
 * </ul>
 * <p>
 * Basic authentication is supported via the URI's user-info part.
 *
 * @author Peter Liske (CreativeCouple)
 */
public class EventSource implements AutoCloseable {

    /**
     * Creates a new EventSource instance with the specified URI.
     *
     * @param uri The URI to connect to for receiving server-sent events.
     */
    public EventSource(String uri) {
        this(URI.create(uri));
    }

    /**
     * Creates a new EventSource instance with the specified URI.
     *
     * @param uri The URI to connect to for receiving server-sent events.
     */
    public EventSource(URI uri) {
        this(ignore -> uri);
        this.uri = uri;
    }

    /**
     * Creates a new EventSource instance with a factory method for generating URIs.
     * This allows dynamic generation of URIs based on the last event ID.
     *
     * @param uriFactory A function that generates a URI based on the last event ID.
     */
    public EventSource(Function<String, URI> uriFactory) {
        thread = new Thread(() -> {
            try {
                while (!Thread.interrupted()) {
                    uri = uriFactory.apply(lastEventID);
                    waitAndStreamEvents();
                }
            } catch (InterruptedException ignored) {
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    private final Thread thread;
    private final Object internalLock = new Object();
    private boolean wantsToConnect = false;
    private final Map<String, List<Listener<Message>>> listeners = new ConcurrentHashMap<>();

    /**
     * -- GETTER --
     * Returns the current status of the EventSource connection.
     *
     * @return The current EventSource connection status.
     */
    @Getter
    private Status status = Status.DISCONNECTED;

    /**
     * -- GETTER --
     * Returns the current URI to which the EventSource is connected.
     * This is the final URI after potential redirects by the server.
     *
     * @return The current URI to which the EventSource is connected.
     */
    @Getter
    private URI uri;

    /**
     * -- GETTER --
     * Returns the ID of the last event received.
     * This is used to reconnect to the server and
     * continue receiving events from where it left off.
     *
     * @return The ID of the last event received.
     */
    @Getter
    private String lastEventID = null;

    /**
     * -- SETTER --
     * Sets a listener that is invoked when an error occurs while processing events.
     *
     * @param onError A {@link Listener} that is invoked when an error occurs while processing events.
     * @return The current EventSource instance.
     */
    @Setter
    private Listener<Exception> onError;

    /**
     * -- SETTER --
     * Sets a listener that is invoked when the EventSource successfully opens a connection.
     *
     * @param onOpen A {@link Listener} that is invoked when the EventSource successfully opens a connection.
     * @return The current EventSource instance.
     */
    @Setter
    private Listener<URI> onOpen;

    /**
     * -- SETTER --
     * Sets a callback that is invoked before the EventSource opens a connection, allowing
     * modification of request headers.
     *
     * @param onBeforeOpen A {@link Listener} that is invoked before the EventSource opens a connection.
     * @return The current EventSource instance.
     */
    @Setter
    private Listener<Map<String, String>> onBeforeOpen;

    private Listener<Message> onMessage;

    /**
     * Sets a listener for any message events.
     * This listener is invoked for each message received from the server.
     *
     * @param listener A {@link Consumer} that processes incoming messages.
     * @return The current EventSource instance.
     */
    public EventSource onMessage(Listener<Message> listener) {
        this.onMessage = listener;
        updateWantsToConnect();
        return this;
    }

    /**
     * -- SETTER --
     * Sets the default retry time in milliseconds to wait before reconnecting after a connection
     * is lost. Any retry time definition from the server can override this value.
     *
     * @param defaultRetryMillis The default retry time in milliseconds to wait before reconnecting after a connection is lost.
     * @return The current EventSource instance.
     */
    @Setter
    private int defaultRetryMillis = 30_000;

    private int actualRetryMillis = -1;

    /**
     * -- SETTER --
     * Sets the read timeout in milliseconds for the connection.
     *
     * @param readTimeout The read timeout in milliseconds for the connection.
     * @return The current EventSource instance.
     */
    @Setter
    private int readTimeout = 60_000;

    private String nextEventType = "";
    private final StringBuilder nextEventData = new StringBuilder();

    /**
     * Closes the EventSource connection and stops listening for events.
     */
    @Override
    public void close() {
        synchronized (internalLock) {
            status = Status.CLOSED;
            wantsToConnect = false;
            thread.interrupt();
        }
    }

    /**
     * Returns the current retry time in milliseconds. This value is either the default retry time
     * or a value provided by the server.
     *
     * @return The retry time in milliseconds.
     */
    public int retryMillis() {
        return actualRetryMillis < 0 ? defaultRetryMillis : actualRetryMillis;
    }

    /**
     * Adds an event listener for a specific event type.
     *
     * @param type     The event type to listen for.
     * @param listener A {@link Consumer} that processes incoming events of the specified event type.
     */
    public void addEventListener(String type, @NonNull Listener<Message> listener) {
        listeners.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(listener);
        updateWantsToConnect();
    }

    /**
     * Removes an event listener for a specific event type.
     *
     * @param type     The event type.
     * @param listener The listener to remove.
     */
    public void removeEventListener(String type, @NonNull Listener<?> listener) {
        listeners.computeIfPresent(type, (k, l) -> {
            l.remove(listener);
            return l.isEmpty() ? null : l;
        });
        updateWantsToConnect();
    }

    /**
     * Updates the connection status based on the presence of listeners.
     */
    private void updateWantsToConnect() {
        synchronized (internalLock) {
            wantsToConnect = status != Status.CLOSED && (onMessage != null || !listeners.isEmpty());
            internalLock.notify();
        }
    }

    /**
     * Waits until the EventSource is ready to connect, based on whether there are
     * listeners attached.
     *
     * @throws InterruptedException If the thread is interrupted while waiting.
     */
    private void waitToConnect() throws InterruptedException {
        while (!wantsToConnect) {
            synchronized (internalLock) {
                if (!wantsToConnect) {
                    internalLock.wait();
                }
            }
        }
    }

    /**
     * Opens a connection to the server and starts streaming events. If an error occurs,
     * it will wait for the retry interval before attempting to reconnect.
     *
     * @throws InterruptedException If the thread is interrupted while waiting.
     */
    private void waitAndStreamEvents() throws InterruptedException {
        status = Status.DISCONNECTED;
        waitToConnect();
        status = Status.CONNECTING;
        try {
            final URLConnection connection = openUrlConnection();
            try (InputStream inputStream = connection.getInputStream()) {
                status = Status.CONNECTED;
                uri = URI.create(connection.getURL().toString());
                callListener(onOpen, uri);
                actualRetryMillis = connection.getHeaderFieldInt("Retry-After", actualRetryMillis);
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, UTF_8));
                for (String line; (line = reader.readLine()) != null; ) {
                    processTextEventStreamLine(line);
                    if (!wantsToConnect) {
                        return;
                    }
                }
            }
            if (connection instanceof HttpURLConnection &&
                ((HttpURLConnection) connection).getResponseCode() == HttpURLConnection.HTTP_NO_CONTENT) {
                close();
            }
        } catch (Exception e) {
            callListener(onError, e);
        }
        Thread.sleep(retryMillis());
    }

    /**
     * Opens a URL connection to the server with the necessary headers for receiving
     * server-sent events.
     *
     * @return The open {@link URLConnection}.
     * @throws IOException If an I/O error occurs while opening the connection.
     */
    private URLConnection openUrlConnection() throws IOException {
        final URLConnection connection = uri.toURL().openConnection();
        Authentication.addUserInfoHeader(connection);
        getRequestHeaders().forEach(connection::setRequestProperty);
        int timeout = readTimeout;
        connection.setConnectTimeout(timeout);
        connection.setReadTimeout(timeout);
        return connection;
    }

    /**
     * Prepares the request headers for the connection, including necessary headers
     * for receiving server-sent events.
     *
     * @return A map of request headers.
     */
    private Map<String, String> getRequestHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "text/event-stream, text/plain;q=0.9, text/*;q=0.5");
        headers.put("Accept-Encoding", "identity");
        headers.put("Cache-Control", "no-store");
        ofNullable(lastEventID).ifPresent(id -> headers.put("Last-Event-ID", id));
        callListener(onBeforeOpen, headers);
        return headers;
    }

    /**
     * The pattern to match event stream text lines.
     */
    private static final Pattern linePattern = Pattern.compile("\uFEFF?([^:]+)?(?:: ?)?(.*)");

    /**
     * Processes a single line of the event stream, updating the state of the event being
     * received.
     *
     * @param line The line of text from the event stream.
     */
    private void processTextEventStreamLine(String line) {
        if (line.isEmpty()) {
            dispatchEventAndReset();
            return;
        }
        Matcher matcher = linePattern.matcher(line);
        if (matcher.matches()) {
            String key = matcher.group(1);
            String value = matcher.group(2);
            if (key == null) {
                dispatchComment(value);
            } else {
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
        }
    }

    /**
     * Dispatches a comment (a line that begins with a colon) to
     * a potentially registered global message listener.
     *
     * @param value The comment text.
     */
    private void dispatchComment(String value) {
        callListener(onMessage, new Message(null, null, value));
    }

    /**
     * Dispatches the current event to any matching listeners
     * and resets the state to prepare for the next event.
     */
    private void dispatchEventAndReset() {
        String type = nextEventType.isEmpty() ? "message" : nextEventType;
        Message message = new Message(lastEventID, type, nextEventData.toString());
        callListener(onMessage, message);
        listeners.getOrDefault(type, emptyList()).forEach(l -> callListener(l, message));
        nextEventType = "";
        nextEventData.setLength(0);
    }

    private <T> void callListener(Listener<T> listener, T data) {
        ofNullable(listener).ifPresent(l -> l.accept(new EventContext(this, l), data));
    }

    /**
     * Represents a message received from the server via the EventSource.
     */
    @Value
    @RequiredArgsConstructor(access = AccessLevel.PACKAGE)
    public static class Message {
        String lastEventId;
        String type;
        String data;
    }

    /**
     * Represents then context in which an event message is handled.
     */
    @Value
    @RequiredArgsConstructor(access = AccessLevel.PACKAGE)
    public static class EventContext {
        @EqualsAndHashCode.Exclude
        transient EventSource source;
        @EqualsAndHashCode.Exclude
        Listener<?> target;
    }

    /**
     * Functional listener interface for EventSource listening methods.
     *
     * @param <T> the data to listen for
     * @see EventSource#onError(Listener)
     * @see EventSource#onOpen(Listener)
     * @see EventSource#onBeforeOpen(Listener)
     * @see EventSource#onMessage(Listener)
     * @see EventSource#addEventListener(String, Listener)
     */
    @FunctionalInterface
    public interface Listener<T> extends BiConsumer<EventContext, T> {
    }

    /**
     * Represents the possible states of the EventSource connection.
     */
    public enum Status {
        /**
         * The initial state and whenever connection is lost to the remote URI.
         */
        DISCONNECTED,

        /**
         * The state when a connection is about to be established.
         * This event source client will connect/disconnect automatically
         * when there are/aren't listeners registered.
         */
        CONNECTING,

        /**
         * The state when a connection is established.
         * The URI that the event source is connected to can be obtained via {@link EventSource#uri()}.
         */
        CONNECTED,

        /**
         * The final state when the connection has been closed via {@link EventSource#close()}.
         */
        CLOSED
    }
}