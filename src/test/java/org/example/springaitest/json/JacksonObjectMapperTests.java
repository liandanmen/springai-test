package org.example.springaitest.json;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JacksonObjectMapperTests {

    private final JacksonObjectMapper mapper = new JacksonObjectMapper();

    @Test
    void serializesJavaTimeValuesUsingConfiguredFormats() throws Exception {
        assertEquals("\"2026-10-20\"", mapper.writeValueAsString(LocalDate.of(2026, 10, 20)));
        assertEquals("\"2026-10-20 14:35\"",
                mapper.writeValueAsString(LocalDateTime.of(2026, 10, 20, 14, 35)));
        assertEquals("\"14:35:42\"", mapper.writeValueAsString(LocalTime.of(14, 35, 42)));
    }

    @Test
    void deserializesJavaTimeValuesUsingConfiguredFormats() throws Exception {
        assertEquals(LocalDate.of(2026, 10, 20), mapper.readValue("\"2026-10-20\"", LocalDate.class));
        assertEquals(LocalDateTime.of(2026, 10, 20, 14, 35),
                mapper.readValue("\"2026-10-20 14:35\"", LocalDateTime.class));
        assertEquals(LocalTime.of(14, 35, 42), mapper.readValue("\"14:35:42\"", LocalTime.class));
    }

    @Test
    void ignoresUnknownProperties() throws Exception {
        Message message = mapper.readValue("{\"text\":\"hello\",\"unknown\":true}", Message.class);

        assertEquals("hello", message.text());
    }

    record Message(String text) {
    }
}
