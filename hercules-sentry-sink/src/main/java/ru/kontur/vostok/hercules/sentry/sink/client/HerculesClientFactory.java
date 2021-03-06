package ru.kontur.vostok.hercules.sentry.sink.client;

import io.sentry.DefaultSentryClientFactory;
import io.sentry.SentryClient;
import io.sentry.connection.AsyncConnection;
import io.sentry.dsn.Dsn;
import io.sentry.event.helper.ContextBuilderHelper;
import io.sentry.marshaller.json.JsonMarshaller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HerculesClientFactory
 *
 * @author Petr Demenev
 */
public class HerculesClientFactory extends DefaultSentryClientFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(HerculesClientFactory.class);

    @Override
    protected JsonMarshaller createJsonMarshaller(int maxMessageLength) {
        return new HerculesJsonMarshaller(maxMessageLength);
    }

    /**
     * Create Sentry client
     * <p>
     * The overridden method with HerculesSentryClient instead of SentryClient
     *
     * @param dsn Data Source name allowing a direct connection to a Sentry server.
     * @return Sentry client matching dsn
     */
    @Override
    public SentryClient createSentryClient(Dsn dsn) {
        try {
            SentryClient sentryClient = new HerculesSentryClient(createConnection(dsn), getContextManager(dsn));
            sentryClient.addBuilderHelper(new ContextBuilderHelper(sentryClient));
            return configureSentryClient(sentryClient, dsn);
        } catch (Exception e) {
            LOGGER.error("Failed to initialize sentry", e);
            throw e;
        }
    }

    /**
     * Whether or not to wrap the underlying connection in an {@link AsyncConnection}.
     * But the overriding method always return false because
     * {@link AsyncConnection} is not needed and we need to forward exceptions to
     * {@link ru.kontur.vostok.hercules.sentry.sink.SentrySyncProcessor}
     *
     * @param dsn Sentry server DSN which may contain options (see the overridden method)
     * @return false (not to wrap the underlying connection in an {@link AsyncConnection})
     */
    @Override
    protected boolean getAsyncEnabled(Dsn dsn) {
        return false;
    }

    /**
     * Whether or not buffering is enabled.
     * But the overriding method always return false because
     * {@link io.sentry.connection.BufferedConnection} is not needed
     *
     * @param dsn Sentry server DSN which may contain options (see the overridden method)
     * @return false (not buffering is enabled)
     */
    @Override
    protected boolean getBufferEnabled(Dsn dsn) {
        return false;
    }
}
