# Contributing

Thanks for contributing to Lineage Proxy. Please keep changes focused and easy to review.

## Workflow

- Open an issue for large changes before coding.
- Use a feature branch and a clear commit history.
- Keep API changes documented in `docs/modding/`.

## Development

- Build: `gradle :proxy:shadowJar :backend-mod:shadowJar :agent:shadowJar`
- Tests: `gradle :shared:test :proxy:test :backend-mod:test`
- Formatting: `gradle spotlessApply`
- Backend-mod requires `libs/HytaleServer.jar` (not included in the repo).

## Legal

By submitting a Pull Request, you agree that your contribution may be used,
modified, and redistributed by the maintainers under the project license.
You also confirm that you have the right to submit the work and that it is
compatible with the GNU AGPL v3.0 license.
