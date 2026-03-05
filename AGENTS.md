AI Agent Rules

- Treat this repository as a production plugin; keep changes minimal and focused.
- Prefer TabooLib APIs(E:\源代码\知识库\taboolib) and adapters over raw Bukkit where available.
- Do not change gameplay logic without a clear request.
- Keep configuration backwards compatible; if a breaking change is necessary, document it.
- Do not remove existing comments unless outdated; add new comments only for non-obvious logic.
- Avoid destructive git actions; never rewrite history.
- Update or add configuration keys in `src/main/resources/settings.yml` when new behavior is introduced.
- All new Kotlin code must be null-safe and follow the existing style.
- Run `./gradlew build` after code changes when possible.
