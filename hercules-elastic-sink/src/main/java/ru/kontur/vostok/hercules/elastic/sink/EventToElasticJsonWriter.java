package ru.kontur.vostok.hercules.elastic.sink;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import ru.kontur.vostok.hercules.json.EventToJsonWriter;
import ru.kontur.vostok.hercules.protocol.Container;
import ru.kontur.vostok.hercules.protocol.Event;
import ru.kontur.vostok.hercules.protocol.Variant;
import ru.kontur.vostok.hercules.protocol.util.ContainerUtil;
import ru.kontur.vostok.hercules.tags.CommonTags;
import ru.kontur.vostok.hercules.util.time.TimeUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

public final class EventToElasticJsonWriter {
    private static final String TIMESTAMP_FIELD = "@timestamp";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.nnnnnnnnnX")
            .withZone(ZoneOffset.UTC);

    private static final JsonFactory FACTORY = new JsonFactory();

    public static void writeEvent(OutputStream stream, Event event, boolean mergePropertiesToRoot) throws IOException {
        try (JsonGenerator generator = FACTORY.createGenerator(stream, JsonEncoding.UTF8)) {
            generator.writeStartObject();
            generator.writeStringField(TIMESTAMP_FIELD, FORMATTER.format(TimeUtil.unixTicksToInstant(event.getTimestamp())));

            if (mergePropertiesToRoot) {
                final Optional<Container> properties = ContainerUtil.extract(event.getPayload(), CommonTags.PROPERTIES_TAG);
                if (properties.isPresent()) {
                    for (Map.Entry<String, Variant> tag : properties.get()) {
                        if (TIMESTAMP_FIELD.equals(tag.getKey())) {
                            continue;// Ignore @timestamp tag since it is special field for elastic events
                        }
                        EventToJsonWriter.writeVariantAsField(generator, tag.getKey(), tag.getValue());
                    }
                }
            }

            for (Map.Entry<String, Variant> tag : event.getPayload()) {
                if (mergePropertiesToRoot && CommonTags.PROPERTIES_TAG.getName().equals(tag.getKey())) {
                    continue;
                }
                if (TIMESTAMP_FIELD.equals(tag.getKey())) {
                    continue;// Ignore @timestamp tag since it is special field for elastic events
                }
                EventToJsonWriter.writeVariantAsField(generator, tag.getKey(), tag.getValue());
            }

            generator.writeEndObject();
        }
    }

    private EventToElasticJsonWriter() {
        /* static class */
    }
}
