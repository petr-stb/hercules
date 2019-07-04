package ru.kontur.vostok.hercules.stream.api;

import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.kontur.vostok.hercules.auth.AuthManager;
import ru.kontur.vostok.hercules.configuration.PropertiesLoader;
import ru.kontur.vostok.hercules.configuration.Scopes;
import ru.kontur.vostok.hercules.configuration.util.ArgsParser;
import ru.kontur.vostok.hercules.configuration.util.PropertiesUtil;
import ru.kontur.vostok.hercules.curator.CuratorClient;
import ru.kontur.vostok.hercules.health.CommonMetrics;
import ru.kontur.vostok.hercules.health.MetricsCollector;
import ru.kontur.vostok.hercules.kafka.util.serialization.VoidDeserializer;
import ru.kontur.vostok.hercules.meta.stream.StreamRepository;
import ru.kontur.vostok.hercules.util.application.ApplicationContextHolder;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;


public class StreamApiApplication {

    private static final Logger LOGGER = LoggerFactory.getLogger(StreamApiApplication.class);

    private static HttpServer server;
    private static CuratorClient curatorClient;
    private static ConsumerPool<Void, byte[]> consumerPool;
    private static AuthManager authManager;
    private static MetricsCollector metricsCollector;

    public static void main(String[] args) {
        long start = System.currentTimeMillis();

        try {
            Map<String, String> parameters = ArgsParser.parse(args);

            Properties properties = PropertiesLoader.load(parameters.getOrDefault("application.properties", "file://application.properties"));

            Properties httpServerProperties = PropertiesUtil.ofScope(properties, Scopes.HTTP_SERVER);
            Properties curatorProperties = PropertiesUtil.ofScope(properties, Scopes.CURATOR);
            Properties consumerProperties = PropertiesUtil.ofScope(properties, Scopes.CONSUMER);
            Properties metricsProperties = PropertiesUtil.ofScope(properties, Scopes.METRICS);
            Properties contextProperties = PropertiesUtil.ofScope(properties, Scopes.CONTEXT);

            ApplicationContextHolder.init("Hercules stream API", "stream-api", contextProperties);

            curatorClient = new CuratorClient(curatorProperties);
            curatorClient.start();

            consumerPool = new ConsumerPool<>(consumerProperties, new VoidDeserializer(), new ByteArrayDeserializer());
            consumerPool.start();

            StreamRepository repository = new StreamRepository(curatorClient);

            metricsCollector = new MetricsCollector(metricsProperties);
            metricsCollector.start();

            StreamReader streamReader = new StreamReader(
                    PropertiesUtil.ofScope(properties, "stream.api.reader"),
                    consumerPool,
                    metricsCollector);

            authManager = new AuthManager(curatorClient);
            authManager.start();

            CommonMetrics.registerCommonMetrics(metricsCollector);

            server = new HttpServer(
                    httpServerProperties,
                    authManager,
                    new ReadStreamHandler(streamReader, authManager, repository),
                    new SeekToEndHandler(authManager, repository, consumerPool),
                    metricsCollector
            );
            server.start();
        } catch (Throwable t) {
            LOGGER.error("Error on starting stream API", t);
            shutdown();
            return;
        }

        Runtime.getRuntime().addShutdownHook(new Thread(StreamApiApplication::shutdown));

        LOGGER.info("Stream API started for {} millis", System.currentTimeMillis() - start);
    }

    private static void shutdown() {
        long start = System.currentTimeMillis();
        LOGGER.info("Started Stream API shutdown");
        try {
            if (server != null) {
                server.stop();
            }
        } catch (Throwable t) {
            LOGGER.error("Error on stopping server");
            //TODO: Process error
        }
        try {
            if (authManager != null) {
                authManager.stop();
            }
        } catch (Throwable t) {
            LOGGER.error("Error on stopping auth manager");
            //TODO: Process error
        }
        try {
            if (metricsCollector != null) {
                metricsCollector.stop();
            }
        } catch (Throwable t) {
            LOGGER.error("Error on stopping metrics collector");
            //TODO: Process error
        }

        try {
            if (consumerPool != null) {
                consumerPool.stop(5_000, TimeUnit.MILLISECONDS);
            }
        } catch (Throwable t) {
            LOGGER.error("Error on stopping consumer pool", t);
            //TODO: Process error
        }

        try {
            if (curatorClient != null) {
                curatorClient.stop();
            }
        } catch (Throwable t) {
            LOGGER.error("Error on stopping curator client", t);
            //TODO: Process error
        }

        LOGGER.info("Finished Stream API shutdown for {} millis", System.currentTimeMillis() - start);
    }
}
