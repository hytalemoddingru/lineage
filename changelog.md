## 0.3.0 - 2026-01-24

### Added
- Agentless proxy enforcement with AUTHENTICATED mode checks and strict referralSource validation.
- Control-plane messaging for transfer requests/results and token validation notices.
- Event-based API for player lifecycle, routing, and security signals.
- Capability-based mod API guards to preserve trust boundaries.
- Proxy command registry with namespace support and backend sync bridge.
- Command metadata extensions: usage and flags (`PLAYER_ONLY`, `HIDDEN`).
- Client protocol/build/version logging for diagnostics.
- CI workflow for running tests on GitHub Actions.
- Proxy token v3 with client/proxy certificate binding (v1/v2 compatible).

### Changed
- JavaAgent is deprecated and only used as an explicit fallback.
- Transfer command moved to proxy and mirrored to backend; namespaced fallback on conflicts.
- Control-plane envelopes now enforce versioning and replay rules strictly.

### Tests
- Added tests for control-plane payloads, command registry sync, and updated token validation.

## 0.2.0 - 2026-01-19

### Added
- Protocol limits and sanity checks for Connect payloads and referral fields.
- Proxy token v2 with nonce support and replay protection (v1 compatibility retained).
- Basic rate limiting for connections, handshakes, streams, and invalid packets.
- Configurable referral source for Connect injection.
- Routing strategy API and strategy-based router.
- Replay protection configuration in backend config.

### Changed
- Connect parsing now validates field sizes against configured limits.
- Agent helper parses v2 token payloads for fingerprint extraction.

### Tests
- Added/updated tests for token validation, replay protection, routing, and Connect limits.
