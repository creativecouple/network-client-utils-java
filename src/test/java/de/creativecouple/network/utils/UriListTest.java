package de.creativecouple.network.utils;

import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UriListTest {

    @Test
    void getFromFileNotFound() {
        assertThatThrownBy(() -> UriList.getFrom(URI.create("file:///path/does/not/exist")))
                .isInstanceOf(FileNotFoundException.class);
    }

    @Test
    void getFromMalformedUrl() {
        assertThatThrownBy(() -> UriList.getFrom(URI.create("unkown://protocol/does/not/exist")))
                .isInstanceOf(MalformedURLException.class);
    }

    @Test
    void emptyBody() throws Exception {
        Path file = Files.createTempFile("uri", ".list");
        assertThat(UriList.getFrom(file.toUri())).isEmpty();
    }

    @Test
    void commentsOnly() throws Exception {
        Path file = Files.createTempFile("uri", ".list");
        Files.write(file, asList("# some comment", "", "#http://example.com/"));
        assertThat(UriList.getFrom(file.toUri())).isEmpty();
    }

    @Test
    void relativeAbsoluteFileUri() throws Exception {
        Path file = Files.createTempFile("uri", ".list");
        Files.write(file,
                asList("foobar", "../foo/bar", "./", "http://example.com", "//some/path", "?some=query#with-fragment"));
        assertThat(UriList.getFrom(file.toUri())).containsExactly(URI.create("file:/tmp/foobar"),
                URI.create("file:/foo/bar"), URI.create("file:/tmp/"), URI.create("http://example.com"),
                URI.create("file://some/path"), URI.create("file:/tmp/?some=query#with-fragment"));
    }

    @Test
    void relativeAbsoluteHttpUri() {
        try (MockEndpoint endpoint = new MockEndpoint()) {
            endpoint.fail(200, "foobar\n" + "../foo/bar\n" + "./\n" + "http://example.com\n" + "//some/path\n"
                    + "?some=query#with-fragment\n");
            String domain = "http://" + endpoint.uri.getHost() + ":" + endpoint.uri.getPort();
            assertThat(UriList.getFrom(endpoint.uri)).containsExactly(URI.create(endpoint.uri + "foobar"),
                    URI.create(domain + "/foo/bar"), endpoint.uri, URI.create("http://example.com"),
                    URI.create("http://some/path"), URI.create(endpoint.uri + "?some=query#with-fragment"));
        }
    }

    @Test
    void handle301Redirect() {
        try (MockEndpoint endpoint1 = new MockEndpoint(); MockEndpoint endpoint2 = new MockEndpoint()) {
            endpoint1.headers.put("Location", endpoint2.uri.toASCIIString());
            endpoint1.fail(301, "redirected");
            endpoint2.fail(200, "foobar\n" + "../foo/bar\n" + "./\n" + "http://example.com\n" + "//some/path\n"
                    + "?some=query#with-fragment\n");
            String domain = "http://" + endpoint2.uri.getHost() + ":" + endpoint2.uri.getPort();
            assertThat(UriList.getFrom(endpoint1.uri)).containsExactly(URI.create(endpoint2.uri + "foobar"),
                    URI.create(domain + "/foo/bar"), endpoint2.uri, URI.create("http://example.com"),
                    URI.create("http://some/path"), URI.create(endpoint2.uri + "?some=query#with-fragment"));
        }
    }
}