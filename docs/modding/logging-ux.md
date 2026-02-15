---
title: Logging UX
---

# Logging UX

This guide defines deterministic log triage for production operations.
Goal: diagnose incidents using `reason` + `correlationId` + `/metrics`/`/status`
without manual free-form parsing.

## Structured Event Shape

Security-critical proxy/backend logs use one stable key/value format:

- `category` (`handshake`, `transfer`, `control-plane`, `command-gateway`, `command-registry-sync`)
- `severity` (`INFO`, `WARN`, `ERROR`)
- `reason` (fixed reject/result reason)
- `correlationId` (when available)
- extra fields (sorted by key)

Example:

```text
category=control-plane severity=WARN reason=INVALID_TIMESTAMP correlationId=proxy:nonce details=timestamp_window_validation_failed
```

## Correlation Rules

Use this priority order:

1. `correlationId` (primary key).
2. `reason` + short timestamp window.
3. `playerId`/`session.id` for handshake and transfer incidents.

Correlation sources:

- handshake: `session.id` or `playerId`;
- transfer: `TransferRequest.correlationId`;
- control-plane: message `correlationId` or fallback `senderId:nonce`.

## Fast Triage Workflow

1. Check `health`:

```bash
curl -s http://127.0.0.1:9091/health
```

2. Find reject spikes by reason:

```bash
curl -s http://127.0.0.1:9091/metrics | rg "lineage_proxy_(handshake_errors|control_reject|routing_decisions)_total"
```

3. Confirm runtime state:

```bash
curl -s http://127.0.0.1:9091/status
```

4. Trace the top `reason` in logs:

```bash
rg "reason=INVALID_TIMESTAMP|reason=UNEXPECTED_SENDER" proxy.log backend.log
```

5. Trace one `correlationId` across both sides:

```bash
rg "correlationId=proxy:1707433539123" proxy.log backend.log
```

## Reason-to-Action Mapping

- `ALPN_MISMATCH`: client/proxy protocol mismatch. Validate client ALPN and server baseline.
- `CONNECTION_RATE_LIMIT`: source exceeds connection budget. Confirm flood and tune rate limits only after load test.
- `HANDSHAKE_INFLIGHT_LIMIT`: handshake concurrency cap reached. Scale instances or increase cap in controlled steps.
- `INITIAL_ROUTE_DENIED`: routing strategy rejected backend selection. Check backend availability and route policy.
- `INVALID_TIMESTAMP`: clock skew or stale control message. Verify NTP and replay/skew windows.
- `UNEXPECTED_SENDER`: control message sender id mismatch. Check `control_sender_id` and `control_expected_sender_id`.
- `PROXY_TOKEN_REJECTED`: token invalid/signature mismatch/replay. Verify secret parity and rollout order.
- `VERSION_MISMATCH`: mixed artifact versions. Stop partial rollout and align `proxy/backend-mod/shared`.
- `MALFORMED_SNAPSHOT` or `REPLAYED_SNAPSHOT`: registry sync payload integrity/replay failure. Re-run snapshot sync path.
- `TRANSFER_FORWARD_FAILED`: transfer request/result delivery issue. Verify messaging channel health and destination backend id.

## No-Secret Logging Guard

Expected behavior:

- any key containing `token` or `secret` is logged as `<redacted>`;
- raw token payloads are not emitted.

Quick verification:

```bash
rg -n "token=|secret=" proxy.log backend.log | rg -v "<redacted>"
```

If a real secret/token appears in logs:

1. treat it as a security incident;
2. rotate affected secrets immediately;
3. add a regression test for the exact log path before next rollout.
