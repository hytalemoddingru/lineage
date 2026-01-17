# Players

`PlayerManager` exposes online proxy sessions. Each `ProxyPlayer`
represents a connected player and can be moved between backends.

```kotlin
val player = context.players.getByName("Example")
player?.sendMessage("Hello from the proxy")
```

Useful fields:

- `ProxyPlayer.id`
- `ProxyPlayer.username`
- `ProxyPlayer.state`
- `ProxyPlayer.backendId`

Transfer example:

```kotlin
player?.transferTo("hub-1")
```
