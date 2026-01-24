# Events

Register listener objects with the `EventBus`. Methods annotated with
`@EventHandler` and a single `Event` parameter are invoked when posted.

```kotlin
class ExampleListener {
    @EventHandler
    fun onEvent(event: Event) {
        // handle event
    }
}
```

Registration:

```kotlin
context.eventBus.register(ExampleListener())
```

Routing events require the `ROUTING` capability.
Security events require the `SECURITY` capability.

To stop listening, call `unregister` with the same listener instance.
