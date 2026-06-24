package com.hotak.noonchibot.core.datatype;

import com.hotak.noonchibot.core.event.Event;
import tools.jackson.databind.JsonNode;

import java.util.List;

public interface UserStreamEventParser {
    boolean canParse(JsonNode msg);
    List<Event> parse(JsonNode msg);
}
