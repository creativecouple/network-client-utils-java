package de.creativecouple.network.utils;

import org.junit.jupiter.api.Test;

import static de.creativecouple.network.utils.EventSourceStatus.DISCONNECTED;
import static org.assertj.core.api.Assertions.assertThat;

class EventSourceTest {

    @Test
    void instantiateEventSource() {
        EventSource eventSource = new EventSource();
        assertThat(eventSource).isNotNull();
        assertThat(eventSource.getStatus()).isEqualTo(DISCONNECTED);
    }
}
