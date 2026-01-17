# Getting started

Lineage mods are plain JVM jars. A mod is discovered by scanning for the
`@LineageModInfo` annotation and instantiating the annotated class.

## Minimal mod

Kotlin:

```kotlin
@LineageModInfo(
    id = "hello",
    name = "Hello Mod",
    version = "1.0.0",
    apiVersion = "0.1.0",
    authors = ["YourName"]
)
class HelloMod : LineageMod() {
    override fun onEnable() {
        context.logger.info("Hello from Lineage!")
    }
}
```

Java:

```java
@LineageModInfo(
    id = "hello",
    name = "Hello Mod",
    version = "1.0.0",
    apiVersion = "0.1.0",
    authors = {"YourName"}
)
public final class HelloMod extends LineageMod {
    @Override
    public void onEnable() {
        context.getLogger().info("Hello from Lineage!");
    }
}
```

## Packaging

- Build a jar that includes your mod class and resources.
- Place the jar into `mods/` next to the proxy `config.toml`.
- Per-mod data is stored under `mods/<mod-id>/`.

## Dependency

When the API is published to Maven Central, depend on the `api` artifact.
Until then, build from source and depend on the `:api` module.
