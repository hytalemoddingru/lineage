# Commands

Commands are registered through `CommandRegistry`.

```kotlin
class PingCommand : Command {
    override val name = "ping"
    override val aliases = listOf("pong")
    override val description = "Basic connectivity test."
    override val usage = "ping"
    override val permission: String? = null
    override val flags = emptySet<CommandFlag>()

    override fun execute(context: CommandContext) {
        context.sender.sendMessage("pong")
    }

    override fun suggest(context: CommandContext): List<String> = emptyList()
}
```

Register from your mod:

```kotlin
context.commandRegistry.register(PingCommand())
```

Use `CommandContext.hasPermission(...)` if you want to handle permission
checks manually. `CommandSender.type` is `CONSOLE`, `PLAYER`, or `SYSTEM`.
Permissions are enforced by the proxy. Backend registration is a thin bridge.

Proxy commands are mirrored to backends as native commands:

- `/<namespace>:<command>` is always registered.
- `/<command>` is registered only if the name is not already taken.

The namespace for core commands is `lineage`. For mod commands it is the
mod `id`. If a conflict exists, only the namespaced command is available.

Supported flags:

- `PLAYER_ONLY` blocks non-player senders.
- `HIDDEN` skips registering the non-namespaced form.
