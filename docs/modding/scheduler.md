# Scheduler

Schedule work on the proxy runtime with `Scheduler`.

```kotlin
val handle = context.scheduler.runLater(Duration.ofSeconds(5)) {
    context.logger.info("Delayed task")
}
```

Use `runSync`, `runAsync`, or `runRepeating` depending on your needs.
Cancel with `handle.cancel()`.
