package com.bingo.app.common.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Serializes LocalDateTime as ISO-8601 WITH the JVM default zone offset
 * (e.g. 2026-09-10T13:39:03.548+03:00). Without an offset, clients interpret
 * the naive timestamp in their own local timezone, skewing relative ages such
 * as the notification bell's "3h ago".
 */
public class LocalDateTimeZoneSerializer extends JsonSerializer<LocalDateTime> {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    @Override
    public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        gen.writeString(FORMATTER.format(value.atZone(ZoneId.systemDefault())));
    }
}