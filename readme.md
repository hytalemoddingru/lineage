![Lineage Proxy](docs/assets/lineage-proxy-logo.svg)

# Lineage Proxy

[![Maven Central](https://img.shields.io/maven-central/v/ru.hytalemodding.lineage/api.svg?label=maven%20central%20(api))](https://central.sonatype.com/artifact/ru.hytalemodding.lineage/api)
[![Maven Central](https://img.shields.io/maven-central/v/ru.hytalemodding.lineage/shared.svg?label=maven%20central%20(shared))](https://central.sonatype.com/artifact/ru.hytalemodding.lineage/shared)
[![Kotlin](https://img.shields.io/badge/kotlin-2.3.0-blue)](https://kotlinlang.org/)

Lineage is a QUIC/TLS proxy for Hytale that keeps the official AUTHENTICATED
flow intact while still allowing full control over routing and handshake metadata.

> [!WARNING]
> **WORK IN PROGRESS**
>
> This project is currently in **active development** and APIs are subject to change.
> It has not yet been battle-tested in large-scale production environments. Use at your own risk.
> We are actively working on core stabilization and preparing for the **v1.x.x** release.

## What it includes

- `proxy`: QUIC/TLS listener, routing, mod loader, messaging
- `backend-mod`: server-side token validation (agentless) and optional fingerprint bridge
- `agent`: legacy Java agent patch for certificate binding (deprecated fallback)
- `api`: modding API
- `shared`: shared token and protocol utilities

## Quick start

Build artifacts:

```
gradle :proxy:shadowJar :backend-mod:shadowJar :agent:shadowJar
```

Prerequisite for backend-mod:

- Place `HytaleServer.jar` in `libs/` (this file is not included in the repo).
- Ensure the server is running in AUTHENTICATED mode.

Run the proxy:

```
java -jar proxy/build/libs/lineage-proxy-<version>.jar
```

Optional: pass a custom config path instead of the default `config.toml` in the working directory.

Install backend pieces:

- Copy `backend-mod/build/libs/lineage-backend-mod-<version>.jar` into the server mods folder.
- Agentless mode is the default and recommended path.
- Optional (deprecated): start the server with `-javaagent:/path/to/lineage-agent-<version>.jar` only if `javaagent_fallback = true` is enabled in backend config.

Configure:

- Proxy config: `config.toml` (auto-created on first run).
- Backend config: `<server data dir>/config.toml` (auto-created on first run).
- `security.proxy_secret` must match backend `proxy_secret`.
- Proxy `messaging.host`/`messaging.port` must match backend `messaging_host`/`messaging_port` when messaging is enabled.
- Keep `messaging.enabled` and backend `messaging_enabled` consistent across nodes.

Config highlights:

- `referral.host` / `referral.port` define the referral source injected into Connect.
- `limits.*` controls protocol sanity limits (language, identity token, username, referral data, host, connect size).
- `rate_limits.*` provides basic per-IP and per-session abuse protection.
- Backend config adds `agentless` (default), `javaagent_fallback`, `require_authenticated_mode`, `replay_window_millis`, and `replay_max_entries` for security controls.
- Proxy tokens use v3 (nonce + cert binding) with v1/v2 compatibility.

Transfer:

- Use `/transfer <backendId>` on the backend server to issue a referral to the proxy.
- If a command name conflict exists, use `/lineage:transfer <backendId>`.

## Documentation

- https://lineage.hytalemodding.ru (modding guide + API reference)

## Dependency

Gradle Kotlin DSL:

```kotlin
dependencies {
    implementation("ru.hytalemodding.lineage:api:0.3.0")
    implementation("ru.hytalemodding.lineage:shared:0.3.0")
}
```

Gradle Groovy DSL:

```groovy
dependencies {
    implementation "ru.hytalemodding.lineage:api:0.3.0"
    implementation "ru.hytalemodding.lineage:shared:0.3.0"
}
```

Maven:

```xml
<dependencies>
  <dependency>
    <groupId>ru.hytalemodding.lineage</groupId>
    <artifactId>api</artifactId>
    <version>0.3.0</version>
  </dependency>
  <dependency>
    <groupId>ru.hytalemodding.lineage</groupId>
    <artifactId>shared</artifactId>
    <version>0.3.0</version>
  </dependency>
</dependencies>
```

## Support

- USDT (TRC20): `TA27e9E1hqB3iGJhf4FNp1U4FP9rWVF7HL`
- RU card (MIR): `2200 7017 1528 7212`
- Payment link: https://pay.cloudtips.ru/p/fc42043c

by [@amanomasato](https://github.com/amanomasato) supported by [@hytalemoddingru](https://github.com/hytalemoddingru)
