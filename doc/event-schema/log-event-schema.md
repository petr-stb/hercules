# LogEvent

Schema of LogEvent described below:

```yaml
LogEvent:
  utcOffset?: Long # Utc offset on 100ns ticks
  level?: String # Log level, possible values are Debug, Info, Warn (Warning), Error, Fatal.
  message?: String # Rendered message
  messageTemplate?: String # Message template
  exception?: Exception # Exception
  stackTrace?: String # Exception tree string representation (stacktrace)
  properties?: Properties # Key-value dictionary where values are primitives or string representation in case of object
Exception:
  type?: String # Exception runtime type
  message?: String # Exception message
  innerExceptions?: Vector<Exception> # Inner exceptions
  stackFrames?: Vector<StackFrame> # Exception stack frames
StackFrame:
  function?: String # Name of function
  type?: String # Type where function is declared
  file?: String # File name
  line?: Integer # Line number
  column?: [Short, Integer] # Column number
Properties:
  project?: String
  application?: String
  service?: String
  environment?: String
```

Tags `properties/project`, `properties/application`, `properties/service` and `properties/environment` are [common tags](../../hercules-protocol/doc/common-tags.md).

## LogEvent usages

- Elastic Sink processes LogEvent as described in [its documentation](../../hercules-elastic-sink/doc/log-event-schema.md)
