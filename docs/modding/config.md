# Configuration

Each mod gets its own data folder under `mods/<mod-id>/`.
Use `ConfigManager` to create or load TOML files in that directory.

```kotlin
val config = context.configManager.config(
    name = "settings",
    createIfMissing = true
) {
    """
    enabled = true
    greeting = "hello"
    """.trimIndent()
}
```

Paths are relative to the mod folder. If `name` has no file extension,
`.toml` is appended automatically.

Examples:

- `settings` -> `mods/<id>/settings.toml`
- `nested/chat` -> `mods/<id>/nested/chat.toml`
