---
title: Operations Runbook
---

# Operations Runbook

This runbook defines safe operational procedures for proxy/backend deployments in `v0.4.0`.

## Safe Defaults

Keep these defaults unless there is a documented exception:

- Proxy:
  - `security.proxy_secret`: strong random value.
  - `[messaging].enabled`: `true` when command/control-plane sync is required.
  - `[messaging].control_*`: keep defaults unless load-testing indicates a required change.
  - `[rate_limits].handshake_concurrent_max`: keep bounded (`256` default).
  - `[rate_limits].routing_concurrent_max`: keep bounded (`256` default).
- Backend:
  - `enforce_proxy = true`.
  - `require_authenticated_mode = true`.
  - `control_expected_sender_id = "proxy"` (or explicit trusted sender id).
  - `proxy_secret` rotation only with overlap window (`proxy_secret_previous`) and planned rollout.

## Startup Sync Procedure

Use this sequence on normal startup:

1. Start proxy with valid config and verify `/health` is `READY` or `DEGRADED`.
2. Start backend-mod and verify config loads without validation errors.
3. Verify command registry sync:
   - backend requests snapshot;
   - proxy sends snapshot;
   - backend marks registry synchronized.
4. Validate control-plane channel with a controlled transfer command.

Expected result:
- No `VERSION_MISMATCH`, `UNEXPECTED_SENDER`, `INVALID_TIMESTAMP`, or `REPLAYED_*` spikes in reject counters.

## Failure Scenario: Messaging Unavailable

Symptoms:
- backend command bridge is not synchronized;
- control-plane transfer path unavailable;
- messaging channel errors in logs.

Actions:

1. Confirm proxy/ backend messaging bind addresses and ports.
2. Confirm shared secret parity (`proxy_secret`) and sender expectations (`control_sender_id`, `control_expected_sender_id`).
3. Keep backend running with command bridge disabled until messaging is healthy.
4. Restore messaging, trigger snapshot sync again, then re-enable command routing.

Do not:
- disable `enforce_proxy`;
- bypass control-plane validation logic.

## Failure Scenario: Registry Desync

Symptoms:
- backend command execution rejected with unsynchronized registry policy;
- command set mismatch between proxy and backend.

Actions:

1. Trigger registry snapshot request from backend.
2. Confirm proxy snapshot encode path has no payload-limit reject.
3. Confirm snapshot sender/version/timestamp/replay checks pass on backend.
4. If desync persists, restart backend bridge component first, then proxy messaging component.

## Failure Scenario: Version Mismatch

Symptoms:
- deterministic reject reason `VERSION_MISMATCH` in command/control-plane paths.

Actions:

1. Stop rollout immediately (no partial deploy).
2. Align proxy/backend/shared module versions.
3. Re-run compatibility tests before re-enabling traffic.
4. Resume rollout only after mismatch counters remain stable at zero.

## Rollback Procedure

Use rollback if production safety guarantees cannot be restored quickly:

1. Stop new deployments.
2. Roll back proxy + backend-mod + shared artifacts as one versioned set.
3. Keep config compatible with rolled-back artifact version.
4. Validate:
   - auth mode requirement,
   - proxy enforcement,
   - control-plane sender/version validation,
   - health endpoint status.

Rollback acceptance:
- login/transfer/control-plane paths are deterministic and stable under smoke load.

## Post-Incident Checklist

After recovery:

1. Save relevant structured logs with correlation ids.
2. Export `/metrics` and `/status` snapshots for incident window.
3. Record root cause, impacted invariants, and remediation PRs.
4. Add or update regression tests for the exact failure mode.

