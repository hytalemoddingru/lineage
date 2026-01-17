# Backends

`BackendRegistry` exposes the configured backend servers.

```kotlin
for (backend in context.backends.all()) {
    context.logger.info("Backend {} -> {}:{}", backend.id, backend.host, backend.port)
}
```

You can move a player to a backend by id:

```kotlin
player.transferTo("hub-1")
```
