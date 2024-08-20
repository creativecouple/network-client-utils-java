package net.creativecouple.utils.network.clients;

import com.sun.net.httpserver.HttpExchange;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.nio.file.Files.createTempFile;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static net.creativecouple.utils.network.clients.EventSource.Status.CLOSED;
import static net.creativecouple.utils.network.clients.EventSource.Status.CONNECTED;
import static net.creativecouple.utils.network.clients.EventSource.Status.CONNECTING;
import static net.creativecouple.utils.network.clients.EventSource.Status.DISCONNECTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "DataFlowIssue"})
class EventSourceTest {

    @SneakyThrows
    private static void pause() {
        Thread.sleep(150);
    }

    @Test
    void instantiateEventSource() {
        URI uri = URI.create("https://example.com/events");
        try (EventSource eventSource = new EventSource(uri)) {
            assertThat(eventSource).isNotNull();
            assertThat(eventSource.uri()).isSameAs(uri);
            assertThat(eventSource.status()).isEqualTo(DISCONNECTED);
        }
    }

    @Test
    void instantiateEventSourceWithString() {
        try (EventSource eventSource = new EventSource("https://example.com/events")) {
            assertThat(eventSource).isNotNull();
            assertThat(eventSource.uri()).isEqualTo(URI.create("https://example.com/events"));
            assertThat(eventSource.status()).isEqualTo(DISCONNECTED);
        }
    }

    @Test
    void closeEventSource() {
        try (EventSource eventSource = new EventSource("https://example.com/events")) {
            eventSource.close();
            pause();
            assertThat(eventSource.status()).isEqualTo(CLOSED);
        }
    }

    @Test
    void addErrorListener() {
        Consumer<Exception> errorListener = mock(Consumer.class);
        try (EventSource eventSource = new EventSource("https://example.com/events")
                .onError(errorListener)
        ) {
            assertThat(eventSource).isNotNull();
            pause();
        }
        verifyNoInteractions(errorListener);
    }

    @Test
    void callErrorListenerWrongUrl() {
        Consumer<Exception> errorListener = mock(Consumer.class);
        try (EventSource eventSource = new EventSource("protocol-does-not-exist://example.com/events")
                .onError(errorListener)
                .onMessage(mock(Consumer.class))
        ) {
            assertThat(eventSource).isNotNull();
            pause();
        }
        verify(errorListener).accept(any(MalformedURLException.class));
    }

    @Test
    void callErrorListenerNotFound() {
        Consumer<Exception> errorListener = mock(Consumer.class);
        try (EventSource eventSource = new EventSource("file:///file/does/not/exist")
                .onError(errorListener)
                .onMessage(mock(Consumer.class))
        ) {
            assertThat(eventSource).isNotNull();
            pause();
        }
        verify(errorListener).accept(any(FileNotFoundException.class));
    }

    @Test
    void removeErrorListener() {
        Consumer<Exception> errorListener = mock(Consumer.class);
        try (EventSource eventSource = new EventSource("protocol-does-not-exist://example.com/events")
                .onError(errorListener)
                .onError(null)
                .onMessage(mock(Consumer.class))
        ) {
            assertThat(eventSource).isNotNull();
            pause();
        }
        verifyNoInteractions(errorListener);
    }

    @Test
    void addOpenListener() throws Exception {
        Consumer<URI> openListener = mock(Consumer.class);
        Consumer<Exception> errorListener = mock(Consumer.class);
        URI uri = createTempFile("events", ".txt").toUri();
        try (EventSource eventSource = new EventSource(uri)
                .onError(errorListener)
                .onOpen(openListener)
        ) {
            assertThat(eventSource).isNotNull();
            pause();
        }
        verifyNoInteractions(openListener);
        verifyNoInteractions(errorListener);
    }

    @Test
    void callOpenListener() throws Exception {
        Consumer<URI> openListener = mock(Consumer.class);
        Consumer<Exception> errorListener = mock(Consumer.class);
        URI uri = createTempFile("events", ".txt").toUri();
        try (EventSource eventSource = new EventSource(uri)
                .onOpen(openListener)
                .onError(errorListener)
                .onMessage(mock(Consumer.class))
        ) {
            assertThat(eventSource).isNotNull();
            pause();
        }
        verify(openListener).accept(uri);
        verifyNoInteractions(errorListener);
    }

    @Test
    void removeOpenListener() throws Exception {
        Consumer<URI> openListener = mock(Consumer.class);
        Consumer<Exception> errorListener = mock(Consumer.class);
        URI uri = createTempFile("events", ".txt").toUri();
        try (EventSource eventSource = new EventSource(uri)
                .onOpen(openListener)
                .onError(errorListener)
                .onOpen(null)
                .onMessage(mock(Consumer.class))
        ) {
            assertThat(eventSource).isNotNull();
            pause();
        }
        verifyNoInteractions(openListener);
        verifyNoInteractions(errorListener);
    }

    @Test
    void addMessageListener() throws Exception {
        Consumer<EventSource.Message> listener = mock(Consumer.class);
        Consumer<Exception> errorListener = mock(Consumer.class);
        URI uri = createTempFile("events", ".txt").toUri();
        try (EventSource eventSource = new EventSource(uri).onError(errorListener).onMessage(listener)) {
            assertThat(eventSource).isNotNull();
            pause();
        }
        verifyNoInteractions(listener);
        verifyNoInteractions(errorListener);
    }

    @Test
    void callMessageListener() {
        Consumer<EventSource.Message> listener = mock(Consumer.class);
        Consumer<Exception> errorListener = mock(Consumer.class);
        try (MockEndpoint endpoint = new MockEndpoint();
             EventSource eventSource = new EventSource(endpoint.uri)
                     .onError(errorListener)
                     .onMessage(listener)
        ) {
            assertThat(eventSource).isNotNull();
            endpoint.println(":ping");
            pause();
        }
        verifyNoInteractions(errorListener);
        verify(listener).accept(new EventSource.Message(null, null, "ping"));
    }

    @Test
    void removeMessageListener() {
        Consumer<EventSource.Message> listener = mock(Consumer.class);
        Consumer<Exception> errorListener = mock(Consumer.class);
        try (MockEndpoint endpoint = new MockEndpoint();
             EventSource eventSource = new EventSource(endpoint.uri)
                     .onError(errorListener)
                     .onMessage(listener)
        ) {
            assertThat(eventSource).isNotNull();
            pause();
            eventSource.onMessage(null);
            endpoint.println(":ping");
            pause();
        }
        verifyNoInteractions(listener);
        verifyNoInteractions(errorListener);
    }

    @Test
    void removeMessageListenerWithoutEventListeners() {
        Consumer<EventSource.Message> listener = mock(Consumer.class);
        Consumer<Exception> errorListener = mock(Consumer.class);
        try (MockEndpoint endpoint = new MockEndpoint();
             EventSource eventSource = new EventSource(endpoint.uri)
                     .onError(errorListener)
        ) {
            assertThat(eventSource).isNotNull();
            assertThat(eventSource.status()).isEqualTo(DISCONNECTED);
            eventSource.onMessage(listener);
            endpoint.println("retry:0");
            pause();
            assertThat(eventSource.status()).isIn(CONNECTING, CONNECTED);
            pause();
            assertThat(eventSource.status()).isEqualTo(CONNECTED);
            eventSource.onMessage(null);
            endpoint.println(":ping");
            pause();
            assertThat(eventSource.status()).isEqualTo(DISCONNECTED);
        }
        verifyNoInteractions(listener);
        verifyNoInteractions(errorListener);
    }

    @Test
    void callEventSourceWithFactory() throws Exception {
        Path fileA = createTempFile("events", ".txt");
        Files.write(fileA, asList("retry:0", "id:foobar", "event:A", "data: Affe", "data", "data: Ananas", ""));

        Path fileB = createTempFile("events", ".txt");
        Files.write(fileB, asList("id: 42", "event:B", "data: Bär", "data: Barbara", ""));

        Path fileC = createTempFile("events", ".txt");
        Files.write(fileC, asList("retry:10000", "event:C", "data: Chamäleon", "data: ", ""));

        Function<String, URI> uriFactory = mock(Function.class);
        when(uriFactory.apply(any())).thenReturn(fileA.toUri(), fileB.toUri(), fileC.toUri());

        Consumer<URI> openListener = mock(Consumer.class);
        Consumer<Exception> errorListener = mock(Consumer.class);
        Consumer<EventSource.Message> listener = mock(Consumer.class);

        try (EventSource eventSource = new EventSource(uriFactory)
                .onOpen(openListener)
                .onError(errorListener)
                .onMessage(listener)
        ) {
            assertThat(eventSource).isNotNull();
            pause();

            verify(uriFactory).apply(null);
            verify(openListener).accept(fileA.toUri());
            verify(listener).accept(new EventSource.Message("foobar", "A", "Affe\n\nAnanas"));
            pause();

            verify(uriFactory).apply("foobar");
            verify(openListener).accept(fileB.toUri());
            verify(listener).accept(new EventSource.Message("42", "B", "Bär\nBarbara"));
            pause();

            verify(uriFactory).apply("42");
            verify(openListener).accept(fileC.toUri());
            verify(listener).accept(new EventSource.Message("42", "C", "Chamäleon\n"));
            pause();
        }
        verifyNoMoreInteractions(uriFactory, listener, openListener, errorListener);
    }

    @Test
    void addNamedMessageListener() {
        Consumer<EventSource.Message> listener = mock(Consumer.class);
        Consumer<Exception> errorListener = mock(Consumer.class);
        try (EventSource eventSource = new EventSource("https://example.com/events")
                .onError(errorListener)
        ) {
            assertThat(eventSource).isNotNull();
            eventSource.addEventListener("foobar", listener);
            pause();
        }
        verifyNoInteractions(listener);
        verifyNoInteractions(errorListener);
    }

    @Test
    void addNamedMessageListenerNull() {
        try (EventSource eventSource = new EventSource("https://example.com/events")) {
            assertThatThrownBy(() -> eventSource.addEventListener("foobar", null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Test
    void callNamedMessageListener() {
        Consumer<EventSource.Message> listener = mock(Consumer.class);
        Consumer<Exception> errorListener = mock(Consumer.class);
        try (MockEndpoint endpoint = new MockEndpoint();
             EventSource eventSource = new EventSource(endpoint.uri)
                     .onError(errorListener)
        ) {
            assertThat(eventSource).isNotNull();
            eventSource.addEventListener("foobar", listener);
            endpoint.println("event:foobar");
            endpoint.println("");
            pause();
        }
        verify(listener).accept(new EventSource.Message(null, "foobar", ""));
        verifyNoInteractions(errorListener);
    }

    @Test
    void ignoreUTF8_BOM() {
        Consumer<EventSource.Message> listener = mock(Consumer.class);
        Consumer<Exception> errorListener = mock(Consumer.class);
        try (MockEndpoint endpoint = new MockEndpoint();
             EventSource eventSource = new EventSource(endpoint.uri)
                     .onError(errorListener)
        ) {
            assertThat(eventSource).isNotNull();
            eventSource.addEventListener("foobar", listener);
            endpoint.println("\uFEFFevent:foobar");
            endpoint.println("");
            pause();
        }
        verify(listener).accept(new EventSource.Message(null, "foobar", ""));
        verifyNoInteractions(errorListener);
    }

    @Test
    void emptyEventName() {
        Consumer<EventSource.Message> listener = mock(Consumer.class);
        Consumer<Exception> errorListener = mock(Consumer.class);
        try (MockEndpoint endpoint = new MockEndpoint();
             EventSource eventSource = new EventSource(endpoint.uri)
                     .onError(errorListener)
        ) {
            assertThat(eventSource).isNotNull();
            eventSource.addEventListener("message", listener);
            endpoint.println("");
            endpoint.println("event:");
            endpoint.println("");
            pause();
        }
        verify(listener, times(2)).accept(new EventSource.Message(null, "message", ""));
        verifyNoInteractions(errorListener);
    }

    @Test
    void removeNamedMessageListener() {
        Consumer<EventSource.Message> listener = mock(Consumer.class);
        Consumer<Exception> errorListener = mock(Consumer.class);
        try (MockEndpoint endpoint = new MockEndpoint();
             EventSource eventSource = new EventSource(endpoint.uri)
                     .onError(errorListener)
        ) {
            assertThat(eventSource).isNotNull();
            eventSource.addEventListener("foobar", listener);
            pause();
            eventSource.removeEventListener("foobar", listener);
            endpoint.println("event:foobar");
            endpoint.println("");
            pause();
        }
        verifyNoInteractions(listener);
        verifyNoInteractions(errorListener);
    }

    @Test
    void removeDifferentlyNamedMessageListener() {
        Consumer<EventSource.Message> listener = mock(Consumer.class);
        Consumer<Exception> errorListener = mock(Consumer.class);
        try (MockEndpoint endpoint = new MockEndpoint();
             EventSource eventSource = new EventSource(endpoint.uri)
                     .onError(errorListener)
        ) {
            assertThat(eventSource).isNotNull();
            eventSource.addEventListener("foobar", listener);
            pause();
            eventSource.removeEventListener("42", listener);
            endpoint.println("event:foobar");
            endpoint.println("");
            pause();
        }
        verify(listener).accept(new EventSource.Message(null, "foobar", ""));
        verifyNoInteractions(errorListener);
    }

    @Test
    void addNullNameMessageListener() {
        try (EventSource eventSource = new EventSource("https://example.com/events")) {
            assertThatThrownBy(() -> eventSource.addEventListener(null, mock(Consumer.class)))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Test
    void callBeforeOpenListener() throws Exception {
        Consumer<Map<String, String>> beforeOpenListener = mock(Consumer.class);
        Consumer<Exception> errorListener = mock(Consumer.class);
        URI uri = createTempFile("events", ".txt").toUri();
        try (EventSource eventSource = new EventSource(uri)
                .onBeforeOpen(beforeOpenListener)
                .onError(errorListener)
                .onMessage(mock(Consumer.class))
        ) {
            assertThat(eventSource).isNotNull();
            pause();
        }
        verify(beforeOpenListener).accept(argThat(map -> {
            assertThat(map).hasSizeGreaterThanOrEqualTo(3)
                    .containsEntry("Accept", "text/event-stream, text/plain;q=0.9, text/*;q=0.5")
                    .containsEntry("Accept-Encoding", "identity").containsEntry("Cache-Control", "no-store");
            return true;
        }));
        verifyNoInteractions(errorListener);
    }

    @Test
    void addRequestHeader() {
        Consumer<Exception> errorListener = mock(Consumer.class);
        try (MockEndpoint endpoint = new MockEndpoint();
             EventSource eventSource = new EventSource(endpoint.uri)
                     .onBeforeOpen(headers -> headers.put("Authorization", "Bearer s3cr3t"))
                     .onError(errorListener)
                     .onMessage(mock(Consumer.class))
        ) {
            assertThat(eventSource).isNotNull();
            pause();
            assertThat(endpoint.requests).hasSize(1);
            HttpExchange exchange = endpoint.requests.get(0);
            assertThat(exchange.getRequestHeaders()).hasSizeGreaterThanOrEqualTo(4)
                    .containsEntry("Authorization", singletonList("Bearer s3cr3t"))
                    .containsEntry("Accept", singletonList("text/event-stream, text/plain;q=0.9, text/*;q=0.5"))
                    .containsEntry("Accept-Encoding", singletonList("identity"))
                    .containsEntry("Cache-Control", singletonList("no-store"));
        }
        verifyNoInteractions(errorListener);
    }

    @Test
    void checkLastEventIdHeader() throws Exception {
        Consumer<Exception> errorListener = mock(Consumer.class);
        try (MockEndpoint endpoint = new MockEndpoint(); EventSource eventSource = new EventSource(endpoint.uri)
                .readTimeout(500)
                .onError(errorListener)
                .onMessage(mock(Consumer.class))
        ) {
            assertThat(eventSource).isNotNull();
            endpoint.println("retry:0");
            endpoint.println("id: some-id-1234");
            Thread.sleep(600);
            endpoint.println("");
            pause();

            assertThat(endpoint.requests).hasSize(2);
            assertThat(endpoint.requests.get(0).getRequestHeaders()).doesNotContainKey("Last-Event-ID");
            assertThat(endpoint.requests.get(1).getRequestHeaders()).containsEntry("Last-Event-ID",
                    singletonList("some-id-1234"));
        }
        verify(errorListener).accept(any(SocketTimeoutException.class));
    }

    @Test
    void checkAuthenticationHeader() {
        Consumer<Exception> errorListener = mock(Consumer.class);
        try (MockEndpoint endpoint = new MockEndpoint()) {
            URI uri = endpoint.uri;
            URI uriWithUser = URI.create(uri.getScheme() + "://fritz:S3cr3t@" + uri.getHost() + ":" + uri.getPort() + uri.getPath());
            try (EventSource eventSource = new EventSource(uriWithUser)
                    .onError(errorListener)
                    .onMessage(mock(Consumer.class))
            ) {
                assertThat(eventSource).isNotNull();
                pause();
                assertThat(endpoint.requests).hasSize(1);
                HttpExchange exchange = endpoint.requests.get(0);
                assertThat(exchange.getRequestHeaders()).hasSizeGreaterThanOrEqualTo(4)
                        .containsEntry("Authorization", singletonList("Basic ZnJpdHo6UzNjcjN0"))
                        .containsEntry("Accept", singletonList("text/event-stream, text/plain;q=0.9, text/*;q=0.5"))
                        .containsEntry("Accept-Encoding", singletonList("identity"))
                        .containsEntry("Cache-Control", singletonList("no-store"));
            }
        }
        verifyNoInteractions(errorListener);
    }

    @Test
    void addRetryHeader() {
        Consumer<Exception> errorListener = mock(Consumer.class);
        try (MockEndpoint endpoint = new MockEndpoint(); EventSource eventSource = new EventSource(endpoint.uri)
                .onError(errorListener)
                .onMessage(mock(Consumer.class))
        ) {
            endpoint.headers.put("Retry-After", "42");
            pause();
            assertThat(eventSource.retryMillis()).isEqualTo(42);
        }
        verifyNoInteractions(errorListener);
    }

    @Test
    void checkReadTimeout() throws Exception {
        Consumer<Exception> errorListener = mock(Consumer.class);
        Consumer<URI> openListener = mock(Consumer.class);
        try (MockEndpoint endpoint = new MockEndpoint(); EventSource eventSource = new EventSource(endpoint.uri)
                .readTimeout(500)
                .onError(errorListener)
                .onOpen(openListener)
                .onMessage(mock(Consumer.class))
        ) {
            assertThat(eventSource).isNotNull();
            endpoint.println("retry:0");
            Thread.sleep(600);
            endpoint.println("");
            pause();
        }
        verify(openListener, times(2)).accept(any(URI.class));
        verify(errorListener).accept(any(SocketTimeoutException.class));
    }

    @Test
    void handle301Redirect() {
        Consumer<Exception> errorListener = mock(Consumer.class);
        Consumer<URI> openListener = mock(Consumer.class);
        Consumer<EventSource.Message> listener = mock(Consumer.class);
        try (MockEndpoint endpoint1 = new MockEndpoint(); MockEndpoint endpoint2 = new MockEndpoint();
             EventSource eventSource = new EventSource(endpoint1.uri)
                     .onError(errorListener)
                     .onOpen(openListener)
                     .onMessage(listener)
        ) {
            assertThat(eventSource).isNotNull();

            endpoint1.headers.put("Location", endpoint2.uri.toASCIIString());
            endpoint1.fail(301, "permanent redirect");

            endpoint2.println("data: some-data");
            endpoint2.println("");

            pause();
            verify(openListener).accept(endpoint2.uri);
        }
        verifyNoInteractions(errorListener);
    }
}
