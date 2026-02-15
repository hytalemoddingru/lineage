# Contributing

Thanks for contributing to Lineage Proxy.
This guide keeps changes reviewable, testable, and production-safe.

## Before you start

- For large changes, open an issue first and align scope before implementation.
- Keep each Pull Request focused on one problem area.
- If behavior or API changes, update `docs/modding/` in the same PR.

## Local environment

- JDK: 21
- Build tool: Gradle wrapper (`./gradlew`)
- Backend-mod compile dependency:
  - repository: `https://maven.hytale.com/release`
  - artifact: `com.hypixel.hytale:Server`
  - version source: `hytaleServerVersion` in `gradle.properties`

## Development workflow

1. Create a feature branch.
2. Implement the change with tests.
3. Run local validation commands.
4. Open PR with clear description and risk notes.

## Validation commands

- Full tests: `./gradlew test`
- Release jars: `./gradlew :proxy:shadowJar :backend-mod:shadowJar`
- Optional formatting: `./gradlew spotlessApply`

## PR checklist

- The change is scoped and intentionally small.
- Tests were added or updated where behavior changed.
- Existing tests pass locally.
- Docs were updated if API/config/runtime behavior changed.
- No secrets, binaries, or machine-local files were added.

## Security notes

- Do not open public issues with exploitable security details.
- Report security-sensitive findings directly to maintainers first.

## Legal

By submitting a Pull Request, you confirm you have the right to submit the work.
Your contribution may be used and redistributed under the project license (GNU AGPL v3.0).
