# Mod metadata

Metadata is provided via `@LineageModInfo` on the mod main class.

Fields:

- `id`: lowercase identifier, 1-32 chars, `[a-z0-9_-]+`
- `name`: human name, 1-64 chars, `[A-Za-z0-9 _.-]+`
- `version`: `MAJOR.MINOR.PATCH`
- `apiVersion`: `MAJOR.MINOR.PATCH`
- `authors`: list of author names
- `description`: optional text
- `dependencies`: required dependencies
- `softDependencies`: optional dependencies
- `website`, `license`: optional strings

Dependency entries accept version constraints:

```
core
core>=1.2.0
worlds^2.0.0
```

If a required dependency is missing or does not satisfy the constraint,
the mod will not load.
