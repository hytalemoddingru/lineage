# Services

`ServiceRegistry` lets mods share instances with each other.

```kotlin
val key = ServiceKey(MyService::class.java)
context.serviceRegistry.register(key, MyService())
```

Retrieve later:

```kotlin
val service = context.serviceRegistry.get(key)
```

## Built-in services

Lineage also publishes built-in services for mods:

- `LocalizationService.SERVICE_KEY`
- `TextRendererService.SERVICE_KEY`
- `RoutingStrategy.SERVICE_KEY`

Example:

```kotlin
val i18n = context.serviceRegistry.get(LocalizationService.SERVICE_KEY)
val text = context.serviceRegistry.get(TextRendererService.SERVICE_KEY)

val line = i18n?.render(player, "help_header", mapOf("count" to "3"))
val styled = text?.renderForPlayer(player, "<gradient:#ff0000:#00ffcc>Hello</gradient>")
```
