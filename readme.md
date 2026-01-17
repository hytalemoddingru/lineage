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
- `backend-mod`: server-side token validation and fingerprint bridge
- `agent`: minimal Java agent patch for certificate binding
- `api`: modding API
- `shared`: shared token and protocol utilities

## Quick start

Build artifacts:

```
gradle :proxy:shadowJar :backend-mod:shadowJar :agent:shadowJar
```

Prerequisite for backend-mod:

- Place `HytaleServer.jar` in `libs/` (this file is not included in the repo).

Run the proxy:

```
java -jar proxy/build/libs/lineage-proxy-<version>.jar [config.toml]
```

Install backend pieces:

- Copy `backend-mod/build/libs/lineage-backend-mod-<version>.jar` into the server mods folder.
- Start the server with `-javaagent:/path/to/lineage-agent-<version>.jar`.

Configure:

- Proxy config: `config.toml` (auto-created on first run).
- Backend config: `<server data dir>/config.toml` (auto-created on first run).
- `security.proxy_secret` must match backend `proxy_secret`.

## Documentation

- Modding guide (Markdown): `docs/modding/index.md`
- API reference (Dokka): `build/dokka/html/index.html`

## Support

- USDT (TRC20): `TA27e9E1hqB3iGJhf4FNp1U4FP9rWVF7HL`
- RU card (MIR): `2200 7017 1528 7212`
- Payment link: https://pay.cloudtips.ru/p/fc42043c

by [@amanomasato](https://github.com/amanomasato) supported by [@hytalemoddingru](https://github.com/hytalemoddingru)
