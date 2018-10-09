package ru.kontur.vostok.hercules.kafka.util.processing;

import com.codahale.metrics.Meter;
import org.apache.kafka.clients.consumer.CommitFailedException;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.Serde;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.kontur.vostok.hercules.kafka.util.serialization.EventDeserializer;
import ru.kontur.vostok.hercules.kafka.util.serialization.EventSerde;
import ru.kontur.vostok.hercules.kafka.util.serialization.EventSerializer;
import ru.kontur.vostok.hercules.kafka.util.serialization.UuidSerde;
import ru.kontur.vostok.hercules.protocol.Event;
import ru.kontur.vostok.hercules.util.PatternMatcher;
import ru.kontur.vostok.hercules.util.functional.Result;
import ru.kontur.vostok.hercules.util.properties.PropertiesExtractor;
import ru.kontur.vostok.hercules.util.time.Timer;

import java.util.LinkedList;
import java.util.Properties;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * BulkConsumer
 *
 * @author Kirill Sulim
 */
public class BulkConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(BulkConsumer.class);

    private static final String ID_TEMPLATE = "hercules.sink.%s.%s";

    private static final String POLL_TIMEOUT = "poll.timeout";
    private static final String BATCH_SIZE = "batch.size";

    private final KafkaConsumer<UUID, Event> consumer;

    private final PatternMatcher streamPattern;
    private final int pollTimeout;
    private final int batchSize;

    private final Meter receivedEventsMeter;
    private final Meter processedEventsMeter;
    private final Meter droppedEventsMeter;
    private final com.codahale.metrics.Timer processTimeTimer;

    private final CommonBulkSinkStatusFsm status;

    private final BulkQueue<UUID, Event> queue;


    public BulkConsumer(
            Properties streamsProperties,
            Properties sinkProperties,
            PatternMatcher streamPattern,
            String consumerGroupId,
            CommonBulkSinkStatusFsm status,
            Meter receivedEventsMeter,
            Meter processedEventsMeter,
            Meter droppedEventsMeter,
            com.codahale.metrics.Timer processTimeTimer,
            BulkQueue<UUID, Event> queue
    ) {
        // TODO: Should be loaded from separate namespace. (see HERCULES-31)
        this.batchSize = PropertiesExtractor.getRequiredProperty(streamsProperties, BATCH_SIZE, Integer.class);
        this.pollTimeout = PropertiesExtractor.getRequiredProperty(streamsProperties, POLL_TIMEOUT, Integer.class);

        streamsProperties.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroupId);
        streamsProperties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        streamsProperties.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, batchSize);

        Serde<UUID> keySerde = new UuidSerde();
        Serde<Event> valueSerde = new EventSerde(new EventSerializer(), EventDeserializer.parseAllTags());

        this.streamPattern = streamPattern;
        this.consumer = new KafkaConsumer<>(streamsProperties, keySerde.deserializer(), valueSerde.deserializer());

        this.status = status;

        this.receivedEventsMeter = receivedEventsMeter;
        this.processedEventsMeter = processedEventsMeter;
        this.droppedEventsMeter = droppedEventsMeter;
        this.processTimeTimer = processTimeTimer;

        this.queue = queue;
    }

    public void run() {
        Queue<Future<Result<BulkQueue.RunResult<UUID, Event>, BackendServiceFailedException>>> processingStorages = new LinkedList<>();

        status.markInitCompleted();
        while (status.isRunning()) {
            try {
                status.waitForState(
                        CommonBulkSinkStatus.RUNNING,
                        CommonBulkSinkStatus.STOPPING_FROM_INIT,
                        CommonBulkSinkStatus.STOPPING_FROM_RUNNING,
                        CommonBulkSinkStatus.STOPPING_FROM_SUSPEND
                );
                if (!status.isRunning()) {
                    return;
                }

                consumer.subscribe(streamPattern.getRegexp());

                RecordStorage<UUID, Event> current = new RecordStorage<>(batchSize);
                RecordStorage<UUID, Event> next = new RecordStorage<>(batchSize);

                TimeUnit unit = TimeUnit.MICROSECONDS;
                Timer timer = new Timer(unit, pollTimeout);
                while (status.isRunning()) {
                    /*
                     * Polling phase
                     *
                     * Try to poll new records from kafka until reached batchSize or timeout expired then process all
                     * collected data. If the total count of polled records exceeded batchSize after the last poll extra records
                     * will be saved in next record storage to process these records at the next step of iteration.
                     */
                    timer.reset().start();
                    long timeLeft = pollTimeout;

                    while (current.available() && 0 <= timeLeft) {
                        try {
                            ConsumerRecords<UUID, Event> poll = consumer.poll(timeLeft);
                            for (ConsumerRecord<UUID, Event> record : poll) {
                                if (current.available()) {
                                    current.add(record);
                                } else {
                                    next.add(record);
                                }
                            }
                            timeLeft = timer.timeLeft();
                        }
                        catch (WakeupException e) {
                            /*
                             * Skip wakeup exception as it is termination signal,
                             * then process already polled data
                             */
                            break;
                        }
                    }
                    int count = current.getRecords().size();
                    receivedEventsMeter.mark(count);

                    /*
                     * Queuing phase
                     *
                     * Put all polled data in sender pool queue and get future for processing result
                     */
                    if (0 < count) {
                        processingStorages.add(queue.put(current));
                    }

                    /*
                     * Commit phase
                     *
                     * Send statistics of processed data and commit last fully processed offset
                     */
                    while (!processingStorages.isEmpty() && (processingStorages.element().isDone() || !status.isRunning())) {
                        Result<BulkQueue.RunResult<UUID, Event>, BackendServiceFailedException> result = processingStorages.remove().get();

                        if (!result.isOk()) {
                            throw result.getError();
                        }

                        BulkSenderStat stat = result.get().getStat();
                        if(processingStorages.isEmpty() || !processingStorages.element().isDone()) {
                            RecordStorage<UUID, Event> storage = result.get().getStorage();
                            consumer.commitSync(storage.getOffsets(null));
                        }

                        processedEventsMeter.mark(stat.getProcessed());
                        droppedEventsMeter.mark(stat.getDropped());
                    }

                    processTimeTimer.update(timer.elapsed(), unit);

                    current = next;
                    next = new RecordStorage<>(batchSize);
                }
            }
            catch (CommitFailedException e) {
                LOGGER.warn("Consumer was kicked by timeout");
            }
            catch (BackendServiceFailedException e) {
                LOGGER.error("Backend failed with", e);
                status.markBackendFailed();
            }
            catch (InterruptedException e) {
                LOGGER.error("Waiting was interrupted", e);
            }
            catch (ExecutionException e) {
                LOGGER.error("Execution exception", e);
            }
            finally {
                consumer.unsubscribe();
                while (!processingStorages.isEmpty()) {
                    Future<Result<BulkQueue.RunResult<UUID, Event>, BackendServiceFailedException>> removed = processingStorages.remove();
                    removed.cancel(false);
                }
            }
        }
    }
}
