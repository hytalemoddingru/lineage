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
