package de.creativecouple.network.utils;

import lombok.Getter;

import static de.creativecouple.network.utils.EventSourceStatus.DISCONNECTED;

public class EventSource {

    @Getter
    private EventSourceStatus status = DISCONNECTED;
}
