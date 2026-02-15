# Proxy Auth, Join and Transfer Flow (v0.4.0)

This document describes the runtime flow exactly as implemented in `proxy` and `backend-mod`.

## Source of truth

- `proxy/src/main/kotlin/ru/hytalemodding/lineage/proxy/net/QuicSessionHandler.kt`
- `proxy/src/main/kotlin/ru/hytalemodding/lineage/proxy/net/handler/ConnectPacketInterceptor.kt`
- `proxy/src/main/kotlin/ru/hytalemodding/lineage/proxy/net/handler/StreamBridge.kt`
- `proxy/src/main/kotlin/ru/hytalemodding/lineage/proxy/control/ControlPlaneService.kt`
- `proxy/src/main/kotlin/ru/hytalemodding/lineage/proxy/player/PlayerTransferService.kt`
- `proxy/src/main/kotlin/ru/hytalemodding/lineage/proxy/net/BackendAvailabilityTracker.kt`
- `backend-mod/src/main/kotlin/ru/hytalemodding/lineage/backend/LineageBackendMod.kt`
- `backend-mod/src/main/kotlin/ru/hytalemodding/lineage/backend/security/TokenValidator.kt`
- `backend-mod/src/main/kotlin/ru/hytalemodding/lineage/backend/control/BackendControlPlaneService.kt`

## 1. Initial join and authenticated handshake

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant P as Proxy QUIC listener
    participant SI as Stream0 interceptor
    participant PB as Proxy->Backend QUIC
    participant B as Backend server
    participant BM as Backend mod

    C->>P: QUIC/TLS connect
    P->>P: QuicSessionHandler.channelActive()
    P->>P: capture client cert (if present)
    C->>SI: Stream 0 Connect packet
    SI->>SI: ConnectPacketInterceptor.channelRead()
    SI->>SI: decode/validate Connect + routing decision
    SI->>SI: issue proxy token (client cert + proxy cert)
    SI->>SI: rewrite Connect referralSource/referralData

    SI->>PB: ensure backend channel (with fallback policy)
    PB->>B: Open backend QUIC/TLS
    SI->>B: Forward modified Connect

    B->>BM: PacketAdapters inbound Connect bridge
    BM->>BM: validate referral source + TokenValidator.validate()
    BM->>B: apply client cert attr + server cert

    B->>C: continue AUTHENTICATED flow (identity/grant/token)
    B-->>P: token validation notice (control-plane)
    P->>P: ControlPlaneService.handleTokenValidation()
```

Key implementation points:

- Stream `0` interception and packet rewrite: `ConnectPacketInterceptor`.
- Proxy token injection: `TokenService.issueToken(...)` from `ConnectPacketInterceptor`.
- Backend cert policy and ALPN checks before stream bridge: `QuicSessionHandler.connectBackend(...)`.
- Final backend-side validation and cert context apply: `LineageBackendMod.registerHandshakeBridge()` + `LineageBackendMod.onPlayerConnect()`.

## 2. Server transfer flow (`/transfer`)

```mermaid
sequenceDiagram
    autonumber
    participant A as Admin/Player command sender
    participant P as Proxy command layer
    participant TS as PlayerTransferService
    participant CPS as ControlPlaneService (proxy)
    participant BCM as BackendControlPlaneService
    participant B as Current backend
    participant C as Client

    A->>P: /transfer <backend>
    P->>TS: requestTransferDetailed(player, target)
    TS->>TS: validate player/backend/status
    TS->>CPS: sendTransferRequest(correlationId, referralData)

    CPS->>BCM: CONTROL TRANSFER_REQUEST
    BCM->>B: referToServer(proxyHost, proxyPort, referralData)
    BCM-->>CPS: CONTROL TRANSFER_RESULT

    C->>P: reconnect via proxy using referralData
    P->>P: validate transfer token in Connect interceptor
    P->>P: route to requested backend
```

Key implementation points:

- Command entry: `TransferCommand.execute(...)`.
- Transfer request orchestration: `PlayerTransferService.requestTransferDetailed(...)`.
- Control-plane encode/send/verify: `ControlPlaneService` and `BackendControlPlaneService`.
- Transfer token consume path: `ConnectPacketInterceptor.resolveBackend(...)`.

## 3. Backend status, fallback and reconnect behavior

```mermaid
sequenceDiagram
    autonumber
    participant BCM as BackendControlPlaneService
    participant CPS as ControlPlaneService (proxy)
    participant BAT as BackendAvailabilityTracker
    participant SI as ConnectPacketInterceptor
    participant QSH as QuicSessionHandler

    BCM-->>CPS: BACKEND_STATUS ONLINE/OFFLINE heartbeat
    CPS->>BAT: markReportedOnline/markReportedOffline

    SI->>BAT: status(selectedBackend)
    alt selected backend offline
        SI->>SI: pick fallback backend if available
    end

    QSH->>BAT: connect failure -> markUnavailable
    QSH->>QSH: connectBackendWithFallback(...)
```

Key implementation points:

- Backend status heartbeats and offline burst on stop: `BackendControlPlaneService.start()/stop()`.
- Proxy status ingestion and state update: `ControlPlaneService.handleBackendStatus(...)`.
- Connect-time reroute + connect-time fallback retries: `ConnectPacketInterceptor.resolveBackend(...)` and `QuicSessionHandler.connectBackendWithFallback(...)`.

## 4. Security invariants enforced by this flow

- Backend token validation is never bypassed; backend validates referral token and context.
- Proxy and backend control-plane messages are envelope-validated (sender/time/ttl/nonce replay/payload limits).
- Backend selection for join/transfer is bounded by availability tracker and deterministic fallback logic.
- Stream bridging starts only after backend channel and handshake path are in a valid state.
