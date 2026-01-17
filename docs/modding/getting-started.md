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

Lineage API is published on Maven Central.

Gradle Kotlin DSL:

```kotlin
dependencies {
    implementation("ru.hytalemodding.lineage:api:0.1.0")
}
```

Gradle Groovy DSL:

```groovy
dependencies {
    implementation "ru.hytalemodding.lineage:api:0.1.0"
}
```

Maven:

```xml
<dependency>
  <groupId>ru.hytalemodding.lineage</groupId>
  <artifactId>api</artifactId>
  <version>0.1.0</version>
</dependency>
```

You can still build from source and depend on the local `:api` module when needed.
