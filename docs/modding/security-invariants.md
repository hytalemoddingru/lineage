# Security Invariants

This document defines the non-negotiable security invariants for `v0.4.0` and
links each invariant to concrete runtime guards and tests.

## Invariant 1: Backend accepts proxy-origin traffic only

- Backend proxy-token validation is strict (`v3` only, required cert claims):
  - `backend-mod/src/main/kotlin/ru/hytalemodding/lineage/backend/security/TokenValidator.kt`
- Referral source is validated against configured proxy endpoint:
  - `backend-mod/src/main/kotlin/ru/hytalemodding/lineage/backend/LineageBackendMod.kt`
- Unsafe downgrade is blocked by config (`enforce_proxy` must remain true):
  - `backend-mod/src/main/kotlin/ru/hytalemodding/lineage/backend/config/BackendConfigLoader.kt`

Tests:
- `backend-mod/src/test/kotlin/ru/hytalemodding/lineage/backend/security/TokenValidatorTest.kt`
- `backend-mod/src/test/kotlin/ru/hytalemodding/lineage/backend/handshake/HandshakeInterceptorTest.kt`

## Invariant 2: Backend command execution cannot bypass proxy

- Backend command mirror is populated only from validated proxy snapshots:
  - expected sender check (`proxy`), timestamp window check, replay check.
- Command dispatch is gated by deterministic policy:
  - player sender required, messaging enabled, registry synchronized, non-blank command.

Runtime:
- `backend-mod/src/main/kotlin/ru/hytalemodding/lineage/backend/command/ProxyCommandBridge.kt`
- `backend-mod/src/main/kotlin/ru/hytalemodding/lineage/backend/command/ProxyCommandDispatchPolicy.kt`

Tests:
- `backend-mod/src/test/kotlin/ru/hytalemodding/lineage/backend/command/ProxyCommandBridgeTest.kt`
- `backend-mod/src/test/kotlin/ru/hytalemodding/lineage/backend/command/ProxyCommandDispatchPolicyTest.kt`

## Invariant 3: Routing decision is immutable after finalization

- `RoutingDecision` is one-shot by design and throws on second mutation.

Runtime:
- `api/src/main/kotlin/ru/hytalemodding/lineage/api/routing/RoutingDecision.kt`

Tests:
- `proxy/src/test/kotlin/ru/hytalemodding/lineage/proxy/routing/RoutingDecisionInvariantTest.kt`
- `proxy/src/test/kotlin/ru/hytalemodding/lineage/proxy/routing/EventRouterTest.kt`

## Invariant 4: Replay is rejected inside control window

- Control-plane replay guard:
  - `shared/src/main/kotlin/ru/hytalemodding/lineage/shared/control/ControlReplayProtector.kt`
- Backend handshake replay guard:
  - `backend-mod/src/main/kotlin/ru/hytalemodding/lineage/backend/security/ReplayProtector.kt`

Tests:
- `shared/src/test/kotlin/ru/hytalemodding/lineage/shared/control/ControlReplayProtectorTest.kt`
- `backend-mod/src/test/kotlin/ru/hytalemodding/lineage/backend/security/ReplayProtectorTest.kt`

## Invariant 5: Security failures do not silently downgrade behavior

- Invalid security checks follow deterministic reject paths, not fallback execution.
- Weak/default secrets are fail-fast at config load.
- Legacy agent mode is removed from runtime and configuration.

Primary files:
- `shared/src/main/kotlin/ru/hytalemodding/lineage/shared/security/SecretStrengthPolicy.kt`
- `proxy/src/main/kotlin/ru/hytalemodding/lineage/proxy/config/TomlLoader.kt`
- `backend-mod/src/main/kotlin/ru/hytalemodding/lineage/backend/config/BackendConfigLoader.kt`
