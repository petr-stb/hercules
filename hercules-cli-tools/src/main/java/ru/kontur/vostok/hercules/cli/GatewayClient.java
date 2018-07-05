package ru.kontur.vostok.hercules.cli;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import ru.kontur.vostok.hercules.protocol.Event;
import ru.kontur.vostok.hercules.protocol.Variant;
import ru.kontur.vostok.hercules.protocol.encoder.Encoder;
import ru.kontur.vostok.hercules.protocol.encoder.EventBuilder;
import ru.kontur.vostok.hercules.protocol.encoder.EventWriter;
import ru.kontur.vostok.hercules.util.args.ArgsParser;
import ru.kontur.vostok.hercules.util.properties.PropertiesUtil;
import ru.kontur.vostok.hercules.uuid.UuidGenerator;

import javax.xml.bind.DatatypeConverter;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.Properties;

public class GatewayClient {

    private static final EventWriter eventWriter = new EventWriter();

    private static String server;

    public static void main(String[] args) throws Exception {

        Map<String, String> parameters = ArgsParser.parse(args);
        Properties properties = PropertiesUtil.readProperties(parameters.getOrDefault("gateway-client.properties", "gateway-client.properties"));

        server = "http://" + properties.getProperty("server");

/*
        for (int i = 0; i < 1000; ++i) {
            sendEvents("test-elastic-sink", generateEvents(10000));
        }*/

        //sendSingleEvent("test-elastic-sink", generateEvent());
        sendEvents("test-elastic-sink", generateEvents(10));

        Unirest.shutdown();
    }

    private static void sendSingleEvent(String streamName, Event event) throws Exception {

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        Encoder encoder = new Encoder(stream);
        encoder.writeInteger(1); // count
        eventWriter.write(encoder, event);

        HttpResponse<String> response = Unirest.post(server + "/stream/send")
                .queryString("stream", streamName)
                .header("apiKey", "test")
                .body(stream.toByteArray())
                .asString();

        System.out.println(response.getStatusText());
    }

    private static void sendEvents(String streamName, Event[] events) throws Exception {

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        Encoder encoder = new Encoder(stream);
        encoder.writeInteger(events.length);
        for (Event event : events) {
            eventWriter.write(encoder, event);
        }

        HttpResponse<String> response = Unirest.post(server + "/stream/send")
                .queryString("stream", streamName)
                .header("apiKey", "test")
                .body(stream.toByteArray())
                .asString();

        System.out.println(response.getStatusText());
    }

    private static Event[] generateEvents(int count) {
        Event[] events = new Event[count];
        for (int i = 0; i < count; ++i) {
            events[i] = generateEvent(i);
        }
        return events;
    }

    private static Event generateEvent(int i) {
        EventBuilder eventBuilder = new EventBuilder();
        eventBuilder.setVersion(1);
        eventBuilder.setEventId(UuidGenerator.getClientInstance().next());
        eventBuilder.setTag("sample-tag", Variant.ofString("sample value"));
        eventBuilder.setTag("sample-long", Variant.ofLong(123L));
        eventBuilder.setTag("sample-flag", Variant.ofFlag(true));
        eventBuilder.setTag("sample-float", Variant.ofFloat(0.123456789f));
        eventBuilder.setTag("sample-double", Variant.ofDouble(0.123456789));
        eventBuilder.setTag("project", Variant.ofString("awesome-project"));
        eventBuilder.setTag("env", Variant.ofString("production"));
        if (0 == i % 2) {
            eventBuilder.setTag("index", Variant.ofString("tstidx0"));
        } else {
            eventBuilder.setTag("index", Variant.ofString("tstidx1"));
        }

        Event result = eventBuilder.build();

        System.out.println("Event created: 0x" + DatatypeConverter.printHexBinary(result.getBytes()));
        return result;
    }
}
