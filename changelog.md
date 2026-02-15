## 0.4.0 - 2026-02-15

### Breaking
- JavaAgent mode is fully removed from the project and runtime flow.
- Backend config keys `agentless` and `javaagent_fallback` are now rejected on startup (fail-fast).
- `enforce_proxy` is now mandatory (`true`) in backend-mod v0.4.0.
- `backend-mod` no longer depends on local `HytaleServer.jar` in `libs/`; it now resolves `com.hypixel.hytale:Server` from Hytale Maven repositories.

### Added
- Full backend availability control path:
  - new control-plane message type `BACKEND_STATUS`,
  - periodic backend ONLINE heartbeats and OFFLINE burst on shutdown,
  - proxy-side `BackendAvailabilityTracker` with ONLINE/OFFLINE/UNKNOWN states.
- Deterministic fallback routing for connect/transfer:
  - offline backend bypass on join,
  - fallback backend retry when direct backend connection fails.
- New proxy command set and aliases:
  - `help` (`?`, `помощь`, `sos`),
  - `list` (`online`, `players`) with backend filter and pagination,
  - `info` (`player`),
  - `ping`,
  - `messages` (`locale`, `lang`) reload,
  - `stop` (`exit`, `end`),
  - `transfer` aliases (`server`, `ser`) and `transfer list`.
- Transfer command improvements:
  - player sender can transfer self without explicitly passing own nickname,
  - backend availability checks before request send.
- Interactive proxy console UX:
  - JLine prompt with command history and tab completion,
  - graceful interrupt handling (`Ctrl+C` routes into controlled shutdown),
  - startup banner and runtime version display from build metadata.
- Log lifecycle management:
  - startup archive of `logs/latest.log`,
  - automatic pruning with configurable archive retention.
- Runtime localization system:
  - file-based bundles `messages/<lang>.toml`,
  - reload at runtime (`messages reload`),
  - language fallback chain (`exact -> family -> en-us`).
- Bounded text rendering stack:
  - legacy colors, HEX colors, named tags, gradients,
  - separate rendering profiles for game/console/plain,
  - runtime limits in `styles/rendering.toml`.
- New modding service APIs:
  - `LocalizationService`,
  - `TextRendererService`.
- Observability endpoints and metrics:
  - `/health`, `/status`, `/metrics`,
  - counters for handshake errors, control-plane rejects, routing decisions,
  - messaging latency metrics and session/player gauges.
- Config hardening and admin-oriented defaults:
  - strict unknown-key validation and conflict checks,
  - weak/default secret rejection via shared secret policy,
  - inline config documentation for proxy and backend-mod templates.
- Backend certificate trust modes for proxy->backend QUIC:
  - `STRICT_PINNED` (required pin match),
  - `TOFU` (learn first certificate, track/accept rotations with warning).

### Changed
- Proxy and backend logging pipeline now separates production-friendly logs from deep diagnostics:
  - critical structured events retained with reason/correlation fields,
  - high-noise technical traces moved to debug level where applicable.
- Command registry synchronization became startup-order tolerant:
  - backend can request command snapshot,
  - proxy retries sync until snapshot is received.
- Player command response protocol keeps multiline responses intact (no forced newline flattening).
- Proxy shutdown path is now command-driven and graceful (`stop` command path integrated into runtime close flow).
- Backend shutdown flow now attempts to reroute connected players back to proxy before disconnect phase.
- README restructured for release distribution flow (GitHub Releases first), and bilingual README support added (`readme.md` + `readme_ru.md`).

### Removed
- `agent` module removed from Gradle project layout and source tree.
- Legacy JavaAgent classes and build scripts removed.
- Release/test workflows no longer rely on downloading private backend dependencies from external storage.

### Documentation
- Expanded modding and operations docs:
  - `docs/modding/localization-text.md`
  - `docs/modding/logging-ux.md`
  - `docs/modding/operations-runbook.md`
  - `docs/modding/proxy-auth-routing-flow.md`
  - `docs/modding/security-invariants.md`
- Updated contributor policy and code-of-conduct documents.

### Tests
- Added and updated coverage for:
  - command layer (`help`, `list`, `info`, `ping`, `messages`, `stop`, `transfer`),
  - command registry sync protocol and replay validation,
  - control-plane backpressure/replay/timestamp rules,
  - backend availability and certificate policy behavior,
  - i18n/text rendering limits and message loading,
  - observability and log archive services,
  - player command protocol multiline behavior.

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
