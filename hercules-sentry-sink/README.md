# Hercules Sentry Sink
Sentry Sink is used to move Log Event with exceptions from Kafka to Sentry.

## Settings
Application is configured through properties file.

### Sink settings
`sink.pattern` - pattern of streams are subscribed by consumers 

`sink.consumer.bootstrap.servers` - list of Apache Kafka hosts

`sink.sender.sentry.url` - URL of Sentry

`sink.sender.sentry.token` - token of Sentry user. It is used for authentication on Sentry

`sink.sender.sentry.level` - log level. Logs with this level and higher levels could be sent to Sentry. Default value: `WARNING`

`sink.sender.sentry.retryLimit` - the number of attempts to send event with retryable errors, default value: `3`

### Application context settings
`context.environment` - id of environment

`context.zone` - id of zone

`context.instance.id` - id of instance

### HTTP Server settings
`http.server.host` - server host, default value: `0.0.0.0`

`http.server.port` - server port

### Graphite metrics reporter settings
`metrics.graphite.server.addr` - hostname of graphite instance to which metrics are sent, default value: `localhost`

`metrics.graphite.server.port` - port of graphite instance, default value: `2003`

`metrics.graphite.prefix` - prefix added to metric name

`metrics.period` - the period with which metrics are sent to graphite, default value: `60`

## Command line
`java $JAVA_OPTS -jar hercules-sentry-sink.jar application.properties=file://path/to/properties/file`

## Quick start
### Initialization

Stream with log events should be predefined.

### `application.properties` sample:
```properties
sink.sender.sentry.url=https://sentry.io
sink.sender.sentry.token=1234567890768132cde645f1ba1bcd4ef67ab78cd9ef89801a45be5747c68f87
sink.sender.sentry.level=warning

sink.consumer.bootstrap.servers=localhost:9092
sink.pattern=mystream

context.environment=dev
context.zone=default
context.instance.id=single

http.server.port=6511

metrics.graphite.server.addr=localhost
metrics.graphite.server.port=2003
metrics.graphite.prefix=myprefix
metrics.period=60
```
