package de.creativecouple.network.utils;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.nio.charset.StandardCharsets.UTF_8;

@Getter
@RequiredArgsConstructor
public final class UriList implements Iterable<URI> {

    private final URI uri;

    public static List<URI> getFrom(URI uri) {
        return streamFrom(uri).collect(Collectors.toList());
    }

    public static Stream<URI> streamFrom(URI uri) {
        return StreamSupport.stream(new UriList(uri).spliterator(), false);
    }

    private static final URI EOF_URI = URI.create("");

    @Override
    @SneakyThrows
    public Iterator<URI> iterator() {
        URLConnection connection = uri.toURL().openConnection();
        connection.addRequestProperty("Accept", "text/uri-list, text/plain;q=0.9, text/*;q=0.5");
        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), UTF_8));
        return new Iterator<URI>() {

            private final AtomicReference<URI> nextURI = new AtomicReference<>();

            @Override
            public boolean hasNext() {
                return nextURI.updateAndGet(uri -> uri == null ? loadNextUri() : uri) != EOF_URI;
            }

            @Override
            public URI next() {
                return hasNext() ? nextURI.getAndSet(null) : null;
            }

            @SneakyThrows
            private URI loadNextUri() {
                for (String line; (line = reader.readLine()) != null; ) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    return URI.create(new URL(connection.getURL(), line).toString());
                }
                return EOF_URI;
            }
        };
    }
}
