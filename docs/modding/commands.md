# Commands

Commands are registered through `CommandRegistry`.

```kotlin
class PingCommand : Command {
    override val name = "ping"
    override val aliases = listOf("pong")
    override val description = "Basic connectivity test."
    override val permission: String? = null

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
