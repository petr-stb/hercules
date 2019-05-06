package ru.kontur.vostok.hercules.graphite.client;

import java.util.Collection;

/**
 * GraphiteMetricDataSender
 *
 * @author Kirill Sulim
 */
@FunctionalInterface
public interface GraphiteMetricDataSender {
    void send(Collection<GraphiteMetricData> data) throws Exception;
}
