package de.creativecouple.network.utils;

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

import static de.creativecouple.network.utils.EventSource.Status.CLOSED;
import static de.creativecouple.network.utils.EventSource.Status.CONNECTED;
import static de.creativecouple.network.utils.EventSource.Status.DISCONNECTED;
import static java.nio.file.Files.createTempFile;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
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
        Thread.sleep(100);
    }

    @Test
    void instantiateEventSource() throws Exception {
        URI uri = URI.create("https://example.com/events");
        try (EventSource eventSource = new EventSource(uri)) {
            assertThat(eventSource).isNotNull();
            assertThat(eventSource.uri()).isSameAs(uri);
            assertThat(eventSource.status()).isEqualTo(DISCONNECTED);
        }
    }

    @Test
    void instantiateEventSourceWithString() throws Exception {
        try (EventSource eventSource = new EventSource("https://example.com/events")) {
            assertThat(eventSource).isNotNull();
            assertThat(eventSource.uri()).isEqualTo(URI.create("https://example.com/events"));
            assertThat(eventSource.status()).isEqualTo(DISCONNECTED);
        }
    }

    @Test
    void instantiateEventSourceWithFactory() throws Exception {
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

        try (EventSource eventSource = new EventSource(uriFactory)) {
            assertThat(eventSource).isNotNull();
            assertThat(eventSource.uri()).isNull();

            eventSource.onOpen(openListener);
            eventSource.onError(errorListener);
            eventSource.onMessage(listener);
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
    void closeEventSource() throws Exception {
        try (EventSource eventSource = new EventSource("https://example.com/events")) {
            eventSource.close();
            pause();
            assertThat(eventSource.status()).isEqualTo(CLOSED);
        }
    }

    @Test
    void addErrorListener() throws Exception {
        Consumer<Exception> errorListener = mock(Consumer.class);
        try (EventSource eventSource = new EventSource("https://example.com/events")) {
            eventSource.onError(errorListener);
            pause();
        }
        verifyNoInteractions(errorListener);
    }

    @Test
    void callErrorListenerWrongUrl() throws Exception {
        Consumer<Exception> errorListener = mock(Consumer.class);
        try (EventSource eventSource = new EventSource("protocol-does-not-exist://example.com/events")) {
            eventSource.onError(errorListener);
            eventSource.onMessage(mock(Consumer.class));
            pause();
        }
        verify(errorListener).accept(any(MalformedURLException.class));
    }

    @Test
    void callErrorListenerNotFound() throws Exception {
        Consumer<Exception> errorListener = mock(Consumer.class);
        try (EventSource eventSource = new EventSource("file:///file/does/not/exist")) {
            eventSource.onError(errorListener);
            eventSource.onMessage(mock(Consumer.class));
            pause();
        }
        verify(errorListener).accept(any(FileNotFoundException.class));
    }

    @Test
    void removeErrorListener() throws Exception {
        Consumer<Exception> errorListener = mock(Consumer.class);
        try (EventSource eventSource = new EventSource("protocol-does-not-exist://example.com/events")) {
            eventSource.onError(errorListener);
            eventSource.onError(null);
            eventSource.onMessage(mock(Consumer.class));
            pause();
        }
        verifyNoInteractions(errorListener);
    }

    @Test
    void addOpenListener() throws Exception {
        Consumer<URI> openListener = mock(Consumer.class);
        Consumer<Exception> errorListener = mock(Consumer.class);
        URI uri = createTempFile("events", ".txt").toUri();
        try (EventSource eventSource = new EventSource(uri)) {
            eventSource.onError(errorListener);
            eventSource.onOpen(openListener);
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
        try (EventSource eventSource = new EventSource(uri)) {
            eventSource.onOpen(openListener);
            eventSource.onError(errorListener);
            eventSource.onMessage(mock(Consumer.class));
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
        try (EventSource eventSource = new EventSource(uri)) {
            eventSource.onOpen(openListener);
            eventSource.onError(errorListener);
            eventSource.onOpen(null);
            eventSource.onMessage(mock(Consumer.class));
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
        try (EventSource eventSource = new EventSource(uri)) {
            eventSource.onError(errorListener);
            eventSource.onMessage(listener);
            pause();
        }
        verifyNoInteractions(listener);
        verifyNoInteractions(errorListener);
    }

    @Test
    void callMessageListener() throws Exception {
        Consumer<EventSource.Message> listener = mock(Consumer.class);
        Consumer<Exception> errorListener = mock(Consumer.class);
        try (MockEndpoint endpoint = new MockEndpoint(); EventSource eventSource = new EventSource(endpoint.uri)) {
            eventSource.onError(errorListener);
            eventSource.onMessage(listener);
            endpoint.println(":ping");
            pause();
        }
        verifyNoInteractions(errorListener);
        verify(listener).accept(new EventSource.Message(null, null, "ping"));
    }

    @Test
    void removeMessageListener() throws Exception {
        Consumer<EventSource.Message> listener = mock(Consumer.class);
        Consumer<Exception> errorListener = mock(Consumer.class);
        try (MockEndpoint endpoint = new MockEndpoint(); EventSource eventSource = new EventSource(endpoint.uri)) {
            eventSource.onError(errorListener);
            eventSource.onMessage(listener);
            pause();
            eventSource.onMessage(null);
            endpoint.println(":ping");
            pause();
        }
        verifyNoInteractions(listener);
        verifyNoInteractions(errorListener);
    }

    @Test
    void removeMessageListenerWithoutEventListeners() throws Exception {
        Consumer<EventSource.Message> listener = mock(Consumer.class);
        Consumer<Exception> errorListener = mock(Consumer.class);
        try (MockEndpoint endpoint = new MockEndpoint(); EventSource eventSource = new EventSource(endpoint.uri)) {
            eventSource.onError(errorListener);
            assertThat(eventSource.status()).isEqualTo(DISCONNECTED);
            eventSource.onMessage(listener);
            endpoint.println("retry:0");
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
    void addNamedMessageListener() throws Exception {
        Consumer<EventSource.Message> listener = mock(Consumer.class);
        Consumer<Exception> errorListener = mock(Consumer.class);
        try (EventSource eventSource = new EventSource("https://example.com/events")) {
            eventSource.onError(errorListener);
            eventSource.addEventListener("foobar", listener);
            pause();
        }
        verifyNoInteractions(listener);
        verifyNoInteractions(errorListener);
    }

    @Test
    void addNamedMessageListenerNull() throws Exception {
        try (EventSource eventSource = new EventSource("https://example.com/events")) {
            assertThatThrownBy(() -> eventSource.addEventListener("foobar", null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Test
    void callNamedMessageListener() throws Exception {
        Consumer<EventSource.Message> listener = mock(Consumer.class);
        Consumer<Exception> errorListener = mock(Consumer.class);
        try (MockEndpoint endpoint = new MockEndpoint(); EventSource eventSource = new EventSource(endpoint.uri)) {
            eventSource.onError(errorListener);
            eventSource.addEventListener("foobar", listener);
            endpoint.println("event:foobar");
            endpoint.println("");
            pause();
        }
        verify(listener).accept(new EventSource.Message(null, "foobar", ""));
        verifyNoInteractions(errorListener);
    }

    @Test
    void ignoreUTF8_BOM() throws Exception {
        Consumer<EventSource.Message> listener = mock(Consumer.class);
        Consumer<Exception> errorListener = mock(Consumer.class);
        try (MockEndpoint endpoint = new MockEndpoint(); EventSource eventSource = new EventSource(endpoint.uri)) {
            eventSource.onError(errorListener);
            eventSource.addEventListener("foobar", listener);
            endpoint.println("\uFEFFevent:foobar");
            endpoint.println("");
            pause();
        }
        verify(listener).accept(new EventSource.Message(null, "foobar", ""));
        verifyNoInteractions(errorListener);
    }

    @Test
    void emptyEventName() throws Exception {
        Consumer<EventSource.Message> listener = mock(Consumer.class);
        Consumer<Exception> errorListener = mock(Consumer.class);
        try (MockEndpoint endpoint = new MockEndpoint(); EventSource eventSource = new EventSource(endpoint.uri)) {
            eventSource.onError(errorListener);
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
    void removeNamedMessageListener() throws Exception {
        Consumer<EventSource.Message> listener = mock(Consumer.class);
        Consumer<Exception> errorListener = mock(Consumer.class);
        try (MockEndpoint endpoint = new MockEndpoint(); EventSource eventSource = new EventSource(endpoint.uri)) {
            eventSource.onError(errorListener);
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
    void removeDifferentlyNamedMessageListener() throws Exception {
        Consumer<EventSource.Message> listener = mock(Consumer.class);
        Consumer<Exception> errorListener = mock(Consumer.class);
        try (MockEndpoint endpoint = new MockEndpoint(); EventSource eventSource = new EventSource(endpoint.uri)) {
            eventSource.onError(errorListener);
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
    void addNullNameMessageListener() throws Exception {
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
        try (EventSource eventSource = new EventSource(uri)) {
            eventSource.onBeforeOpen(beforeOpenListener);
            eventSource.onError(errorListener);
            eventSource.onMessage(mock(Consumer.class));
            pause();
        }
        verify(beforeOpenListener).accept(argThat(map -> {
            assertThat(map).hasSizeGreaterThanOrEqualTo(3)
                    .containsEntry("Accept", "text/event-stream, text/plain;q=0.9, text/*;q=0.5")
                    .containsEntry("Accept-Encoding", "identity")
                    .containsEntry("Cache-Control", "no-store");
            return true;
        }));
        verifyNoInteractions(errorListener);
    }

    @Test
    void addRequestHeader() throws Exception {
        Consumer<Exception> errorListener = mock(Consumer.class);
        try (MockEndpoint endpoint = new MockEndpoint(); EventSource eventSource = new EventSource(endpoint.uri)) {
            eventSource.onBeforeOpen(headers -> headers.put("Authentication", "Bearer s3cr3t"));
            eventSource.onError(errorListener);
            eventSource.onMessage(mock(Consumer.class));
            pause();
            assertThat(endpoint.requests).hasSize(1);
            HttpExchange exchange = endpoint.requests.get(0);
            assertThat(exchange.getRequestHeaders()).hasSizeGreaterThanOrEqualTo(4)
                    .containsEntry("Authentication", singletonList("Bearer s3cr3t"))
                    .containsEntry("Accept", singletonList("text/event-stream, text/plain;q=0.9, text/*;q=0.5"))
                    .containsEntry("Accept-Encoding", singletonList("identity"))
                    .containsEntry("Cache-Control", singletonList("no-store"));
        }
        verifyNoInteractions(errorListener);
    }

    @Test
    void checkLastEventIdHeader() throws Exception {
        Consumer<Exception> errorListener = mock(Consumer.class);
        try (MockEndpoint endpoint = new MockEndpoint(); EventSource eventSource = new EventSource(endpoint.uri)) {
            eventSource.readTimeout(500);
            eventSource.onError(errorListener);
            eventSource.onMessage(mock(Consumer.class));
            endpoint.println("retry:0");
            endpoint.println("id: some-id-1234");
            Thread.sleep(600);
            endpoint.println("");
            pause();

            assertThat(endpoint.requests).hasSize(2);
            assertThat(endpoint.requests.get(0).getRequestHeaders())
                    .doesNotContainKey("Last-Event-ID");
            assertThat(endpoint.requests.get(1).getRequestHeaders())
                    .containsEntry("Last-Event-ID", singletonList("some-id-1234"));
        }
        verify(errorListener).accept(any(SocketTimeoutException.class));
    }

    @Test
    void addRetryHeader() throws Exception {
        Consumer<Exception> errorListener = mock(Consumer.class);
        try (MockEndpoint endpoint = new MockEndpoint(); EventSource eventSource = new EventSource(endpoint.uri)) {
            endpoint.headers.put("Retry-After", "42");
            eventSource.onError(errorListener);
            eventSource.onMessage(mock(Consumer.class));
            pause();
            assertThat(eventSource.retryMillis()).isEqualTo(42);
        }
        verifyNoInteractions(errorListener);
    }

    @Test
    void checkReadTimeout() throws Exception {
        Consumer<Exception> errorListener = mock(Consumer.class);
        Consumer<URI> openListener = mock(Consumer.class);
        try (MockEndpoint endpoint = new MockEndpoint(); EventSource eventSource = new EventSource(endpoint.uri)) {
            eventSource.readTimeout(500);
            eventSource.onError(errorListener);
            eventSource.onOpen(openListener);
            eventSource.onMessage(mock(Consumer.class));
            endpoint.println("retry:0");
            Thread.sleep(600);
            endpoint.println("");
            pause();
        }
        verify(openListener, times(2)).accept(any(URI.class));
        verify(errorListener).accept(any(SocketTimeoutException.class));
    }
}
