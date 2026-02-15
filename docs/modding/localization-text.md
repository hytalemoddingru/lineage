# Localization and Text

Lineage proxy supports file-based localization and bounded markup rendering for
player/system messages.

## Message bundles

- `messages/en-us.toml`
- `messages/ru-ru.toml`

Bundles are created automatically on first start and can be edited live.

Language fallback chain:

1. exact locale (for example `ru-ru`);
2. language family (`ru` -> `ru-ru`, `en` -> `en-us`);
3. default `en-us`.

## Markup renderer

Renderer profiles:

- `game`
- `console`
- `plain`

Supported syntax:

- `&a`, `&l`, `&r` (legacy + section equivalents)
- `<#RRGGBB>`, `&#RRGGBB`
- `<red>...</red>`, `<bold>...</bold>`, `<italic>...</italic>`, `<underline>...</underline>`
- `<gradient:#ff0000:#00ffcc>text</gradient>`

## Runtime limits

`styles/rendering.toml` controls hard limits:

- `max_input_length`
- `max_nesting_depth`
- `max_gradient_chars`
- `max_tag_length`

These bounds are enforced to keep rendering deterministic and safe under malformed input.

## Mod API services

Use `ServiceRegistry`:

- `LocalizationService`:
  - `text(language, key, vars)`
  - `render(player, key, vars)`
  - `send(player, key, vars)`
- `TextRendererService`:
  - `renderForPlayer(player, rawMarkup)`
  - `renderForConsole(rawMarkup)`
  - `renderPlain(rawMarkup)`

## Reload

Use `messages reload` from proxy console (or with permission in-game) to reload:

- `messages/*.toml`
- `styles/rendering.toml`
