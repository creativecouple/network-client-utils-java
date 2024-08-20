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

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import java.io.BufferedReader;
import java.io.IOException;
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

/**
 * A utility class that represents a list of URIs obtained from a remote URI
 * that points to a text file in the "text/uri-list" format.
 * <p>
 * The "text/uri-list" MIME type is a standard format for representing a list of URIs
 * as plain text. Each line in the file represents a single URI, with optional comment
 * lines starting with '#'. Empty lines are ignored.
 * <p>
 * For more details about the "text/uri-list" MIME type, see:
 * <ul>
 *   <li><a href="https://www.iana.org/assignments/media-types/text/uri-list">IANA: text/uri-list</a></li>
 *   <li><a href="https://www.ietf.org/rfc/rfc2483.txt">RFC 2483: URI Resolution Services Necessary for URN Resolution</a></li>
 * </ul>
 * <p>
 * This class implements {@link Iterable}, allowing it to be used in enhanced
 * for-loops or to be converted into a {@link Stream}.
 * Basic authentication is supported via the URI's user-info part.
 *
 * @author Peter Liske (CreativeCouple)
 */
@Getter
@RequiredArgsConstructor
public final class UriList implements Iterable<URI> {

    /**
     * -- GETTER --
     * Returns the source URI pointing to the local or remote text file containing the list of URIs.
     *
     * @return The source URI pointing to the local or remote text file containing the list of URIs.
     */
    private final URI uri;

    /**
     * Special marker URI used to indicate the end of the list when iterating.
     */
    private static final URI EOF_URI = URI.create("");

    /**
     * Retrieves a list of URIs from the given URI by reading the remote file
     * and parsing it.
     *
     * @param uri The URI pointing to the local or remote text file containing the list of URIs.
     * @return A {@link List} of {@link URI} objects obtained from the remote file.
     * @throws IOException If an I/O error occurs while reading from the remote file.
     */
    public static List<URI> getFrom(URI uri) {
        return streamFrom(uri).collect(Collectors.toList());
    }

    /**
     * Creates a stream of URIs from the given URI by reading the remote file
     * and parsing it. The stream lazily loads URIs as it reads the file.
     *
     * @param uri The URI pointing to the local or remote text file containing the list of URIs.
     * @return A {@link Stream} of {@link URI} objects obtained from the remote file.
     * @throws IOException If an I/O error occurs while reading from the remote file.
     */
    public static Stream<URI> streamFrom(URI uri) {
        return StreamSupport.stream(new UriList(uri).spliterator(), false);
    }

    /**
     * Returns an iterator over elements of type {@link URI}.
     * <p>
     * The iterator reads URIs from the remote file line by line, skipping empty lines
     * and comments (lines starting with '#').
     *
     * @return An {@link Iterator} of {@link URI} elements.
     * @throws IOException If the remote connection cannot be established.
     */
    @Override
    @SneakyThrows
    public Iterator<URI> iterator() {
        URLConnection connection = uri.toURL().openConnection();
        connection.addRequestProperty("Accept", "text/uri-list, text/plain;q=0.9, text/*;q=0.5");
        Authentication.addUserInfoHeader(connection);
        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), UTF_8));
        return new Iterator<URI>() {

            private final AtomicReference<URI> nextURI = new AtomicReference<>();

            /**
             * Returns {@code true} if the iteration has more URIs.
             * This method will load the next URI if not already loaded.
             *
             * @return {@code true} if the iteration has more elements, otherwise {@code false}.
             * @throws IOException If an I/O error occurs while reading from the input stream.
             */
            @Override
            public boolean hasNext() {
                return nextURI.updateAndGet(uri -> uri == null ? loadNextUri() : uri) != EOF_URI;
            }

            /**
             * Returns the next URI in the iteration. This method should only be
             * called if {@link #hasNext()} returns {@code true}.
             *
             * @return The next {@link URI} in the iteration, or {@code null} if there are no more elements.
             * @throws IOException If an I/O error occurs while reading from the input stream.
             */
            @Override
            public URI next() {
                return hasNext() ? nextURI.getAndSet(null) : null;
            }

            /**
             * Reads the next URI from the file, skipping empty lines and comments.
             * If no more URIs are available, returns the EOF_URI marker.
             *
             * @return The next valid {@link URI}, or {@link UriList#EOF_URI} if none are available.
             * @throws IOException If an I/O error occurs while reading from the input stream.
             */
            @SneakyThrows
            private URI loadNextUri() {
                for (String line; (line = reader.readLine()) != null;) {
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
