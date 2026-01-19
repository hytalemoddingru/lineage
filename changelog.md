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
