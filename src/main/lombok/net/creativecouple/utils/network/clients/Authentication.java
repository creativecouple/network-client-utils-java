package net.creativecouple.utils.network.clients;

import lombok.experimental.UtilityClass;

import java.net.URLConnection;
import java.util.Base64;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Utility class offering methods to handle authorization for client requests.
 */
@UtilityClass
public final class Authentication {

    /**
     * Turns an optional user-info part of the connection's URL
     * into a Basic Authorization request header.
     * <p>
     * A URL connection with user-info, e.g. <code>https://my-user:secret-pw@example.com/some/resource</code>,
     * will use a request header like <code>Authorization: Basic bXktdXNlcjpzZWNyZXQtcHc=</code>.
     * <p>
     * Usage example:
     * <pre>
     *     URLConnection connection = myURL.openConnection();
     *     Authentication.addUserInfoHeader(connection);
     *     try(InputStream input = connection.getInputStream) {
     *         …
     *     }
     * </pre>
     *
     * @param connection a URL connection
     */
    public static void addUserInfoHeader(URLConnection connection) {
        String userInfo = connection.getURL().getUserInfo();
        if (userInfo != null && !userInfo.isEmpty()) {
            String base64 = Base64.getEncoder().encodeToString(userInfo.getBytes(UTF_8));
            connection.addRequestProperty("Authorization", "Basic " + base64);
        }
    }

}
