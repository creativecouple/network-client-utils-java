# Network Client Utils

![License](https://img.shields.io/badge/license-MIT-blue.svg)
![Maven Central](https://img.shields.io/maven-central/v/de.creativecouple.utils/network-client-utils)

`Network-Client-Utils` is a lightweight Java library providing utility classes for handling network-related tasks.
This library is designed to make it easy for developers to handle various resources,
such as `text/uri-list` (URI lists) and `text/event-stream` (Server-Sent Events).

## Features

- **UriList**: Seamlessly handle lists of URIs with the `UriList` class, 
that supports lazily streaming from `text/uri-list` resources.
- **EventSource**: Easily implement Server-Sent Events (SSE) in your Java applications,
inspired by the JavaScript `EventSource` API.

## Installation (latest version "0.2.1")

You only need to install [`de.creativecouple.utils:network-client-utils`](https://mvnrepository.com/artifact/de.creativecouple.utils/network-client-utils/latest)
as dependency in your Java/Kotlin/Scala project definition,

<details>
<summary>as Maven dependency in your <code>pom.xml</code>,</summary>

```xml
<dependencies>
    …
    <dependency>
        <groupId>de.creativecouple.utils</groupId>
        <artifactId>network-client-utils</artifactId>
        <version>0.2.1</version>
    </dependency>
</dependencies>
```
</details>
<details>
<summary>as Gradle dependency in your <code>build.gradle</code>,</summary>

```gradle
implementation group: 'de.creativecouple.utils', name: 'network-client-utils', version: '0.2.1'
```
</details>
<details>
<summary>or as Scala dependency in your <code>build.sbt</code>.</summary>

```scala
libraryDependencies += "de.creativecouple.utils" % "network-client-utils" % "0.2.1"
```
</details>


## Usage

### UriList

The `UriList` class provides an easy way to parse lists of URIs,
which can be fetched as `List<URI>` or `Stream<URI>` from any local or remote `text/uri-list` resource.

#### Example

```java
import de.creativecouple.network.utils.UriList;

import java.io.StringReader;
import java.util.List;
import java.util.stream.Stream;

public class UriListExample {
  public static void main(String[] args) {
    URI remoteFile = URI.create("http://example.com/uri-list");
    
    // as list
    List<URI> uris = UriList.getFrom(remoteFile);
    uris.forEach(System.out::println);

    // as stream
    Stream<URI> uriStream = UriList.streamFrom(remoteFile);
    uriStream.filter(…).forEach(…);
  }
}
```

#### Benefits

- **Ease of Use**: Quickly parse URI lists from strings or streams.
- **Standards Compliant**: Fully compliant with the `text/uri-list` MIME-type standard as specified
  by [IANA](https://www.iana.org/assignments/media-types/text/uri-list)
  and [RFC 2483](https://www.ietf.org/rfc/rfc2483.txt).
- **Robust Parsing**: Handles comments and blank lines gracefully, ensuring a clean list of URIs.

### EventSource

The `EventSource` class allows your Java applications to receive
real-time updates from servers via Server-Sent Events (SSE).
This class closely mimics the JavaScript `EventSource` API,
providing a familiar interface for Java developers.

#### Example

```java
import de.creativecouple.network.utils.EventSource;
import de.creativecouple.network.utils.EventSource.Message;

import java.net.URI;

public class EventSourceExample {
    public static void main(String[] args) {
        EventSource eventSource = new EventSource("http://example.com/events");

        // listen to any message
        eventSource.onMessage(message -> {
            System.out.println("Received " + message.type() + " event: " + message.data());
        });

        // register a particular event type listener
        eventSource.addEventListener("my-type", message -> {
            System.out.println("Received my-type event: " + message.data());
        });

        // listen to errors
        eventSource.onError(error -> {
            System.err.println("Error: " + error.getMessage());
        });
    }
}
```

#### Benefits

- **Real-time Updates**: Receive server events in real-time,
ideal for applications that require live data streams.
- **Familiar API**: Inspired by the JavaScript `EventSource` API,
making it easy to use for developers familiar with front-end development.
- **Flexible and Configurable**: Customize connection parameters,
handle reconnection logic, and manage event listeners with ease.
- **Bandwith friendly**: Only connects to the event-stream resource
when there are event listeners present.

## Documentation

For detailed usage instructions and API documentation,
please refer to the following official resources:

- [EventSource API](https://developer.mozilla.org/en-US/docs/Web/API/EventSource)
- [Server-Sent Events (SSE)](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events)
- [HTML Standard: Server-Sent Events](https://html.spec.whatwg.org/multipage/server-sent-events.html#server-sent-events)
- [IANA MIME-type for text/uri-list](https://www.iana.org/assignments/media-types/text/uri-list)
- [RFC 2483](https://www.ietf.org/rfc/rfc2483.txt)

## Contributing

We welcome contributions to improve the `Network-Client-Utils` library.
Feel free to submit pull requests, open issues,
or fork the repository to make enhancements.

## License

This project is licensed under the MIT License -
see the [LICENSE](LICENSE) file for details.

## Acknowledgments

This library is inspired by the need for robust,
standards-compliant tools for handling network-related tasks in Java.
We hope it serves the community well.
