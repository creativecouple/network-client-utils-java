package de.creativecouple.network.utils;

import lombok.NoArgsConstructor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public final class UriList {

    public static List<URI> fetch(URI uri) throws IOException, URISyntaxException {
        URL url = uri.toURL();
        URLConnection connection = url.openConnection();
        connection.addRequestProperty("Accept", "text/uri-list, text/plain;q=0.9, text/*;q=0.5");
        try (InputStream body = connection.getInputStream()) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(body, UTF_8));
            List<URI> result = new ArrayList<>();
            for (String line; (line = reader.readLine()) != null; ) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                result.add(new URL(url, line).toURI());
            }
            return result;
        }
    }

}
