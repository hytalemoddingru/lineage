# Lifecycle

`LineageMod` exposes three lifecycle hooks:

- `onLoad(context)`: called once after the mod is constructed and the
  `ModContext` is ready.
- `onEnable()`: called after all mods are loaded and dependency order is resolved.
- `onDisable()`: called during shutdown or reload.

Use `onLoad` for wiring services and `onEnable` for runtime logic.
