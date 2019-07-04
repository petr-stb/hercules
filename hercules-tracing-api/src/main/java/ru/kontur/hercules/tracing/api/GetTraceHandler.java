package ru.kontur.hercules.tracing.api;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import ru.kontur.hercules.tracing.api.cassandra.PagedResult;
import ru.kontur.hercules.tracing.api.json.EventToJsonConverter;
import ru.kontur.vostok.hercules.protocol.Event;
import ru.kontur.vostok.hercules.undertow.util.ExchangeUtil;
import ru.kontur.vostok.hercules.undertow.util.ResponseUtil;
import ru.kontur.vostok.hercules.util.functional.Result;
import ru.kontur.vostok.hercules.util.parsing.Parsers;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * GetTraceHandler
 *
 * @author Kirill Sulim
 */
public class GetTraceHandler implements HttpHandler {

    private static final int DEFAULT_COUNT = 10_000;

    private final TracingReader tracingReader;

    public GetTraceHandler(TracingReader tracingReader) {
        this.tracingReader = tracingReader;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {

        // TODO: Replace with good parameters extractor
        final Result<UUID, String> traceIdResult = ExchangeUtil.extractQueryParam(exchange, "traceId")
                .map(Result::<String, String>ok)
                .orElse(Result.error("Required parameter missing"))
                .flatMap(Parsers::parseUuid);

        if (!traceIdResult.isOk()) {
            ResponseUtil.badRequest(exchange, String.format("Parameter traceId has illegal value: %s", traceIdResult.getError()));
            return;
        }

        // TODO: Replace with good parameters extractor
        final Result<UUID, String> parentSpanIdResult = ExchangeUtil.extractQueryParam(exchange, "parentSpanId")
                .map(Parsers::parseUuid)
                .orElse(Result.ok(null));

        if (!parentSpanIdResult.isOk()) {
            ResponseUtil.badRequest(exchange, String.format("Parameter parentTraceId has illegal value: %s", traceIdResult.getError()));
            return;
        }

        // TODO: Replace with good parameters extractor
        final Result<Integer, String> limitResult = ExchangeUtil.extractQueryParam(exchange, "limit")
                .map(Parsers::parseInteger)
                .orElse(Result.ok(DEFAULT_COUNT));

        if (!limitResult.isOk()) {
            ResponseUtil.badRequest(exchange, String.format("Parameter limit has illegal value: %s", limitResult.getError()));
        }

        // TODO: Replace with good parameters extractor
        final Optional<String> pagingState = ExchangeUtil.extractQueryParam(exchange, "pagingState");

        if (Objects.nonNull(parentSpanIdResult.get())) {
            final PagedResult<Event> traceSpansByTraceIdAndParentSpanId = tracingReader.getTraceSpansByTraceIdAndParentSpanId(
                    traceIdResult.get(),
                    parentSpanIdResult.get(),
                    limitResult.get(),
                    pagingState.orElse(null)
            );

            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
            exchange.getResponseSender().send(EventToJsonConverter.pagedResultAsString(traceSpansByTraceIdAndParentSpanId));
        } else {
            final PagedResult<Event> traceSpansByTraceId = tracingReader.getTraceSpansByTraceId(
                    traceIdResult.get(),
                    limitResult.get(),
                    pagingState.orElse(null)
            );

            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
            exchange.getResponseSender().send(EventToJsonConverter.pagedResultAsString(traceSpansByTraceId));
        }
    }
}
