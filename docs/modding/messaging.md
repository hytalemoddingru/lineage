# Messaging

Messaging provides UDP channels for proxy and backend communication.

Raw channel:

```kotlin
val channel = context.messaging.registerChannel("mods:hello") { message ->
    val text = message.payload.toString(Charsets.UTF_8)
    context.logger.info("Got: {}", text)
}
channel.send("hi".toByteArray())
```

Typed channel:

```kotlin
val typed = MessagingChannels.registerTyped(
    context.messaging,
    "mods:chat",
    Codecs.UTF8_STRING
) { message ->
    context.logger.info("Got: {}", message.payload)
}
typed.send("hello")
```
