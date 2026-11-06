# Health of a Titan (HoT)

Personal health companion built on the **WHOOP API** — sleep by stages, recovery
(HRV, resting HR) and strain analysis for a single owner. Work in progress.

See [PRIVACY.md](./PRIVACY.md).

## Проект (RU)

Экосистема здоровья и эффективной жизни. MVP — Android-приложение для Pixel 9 Pro XL
с данными WHOOP и Pixel Watch. Работу ведёт команда из 16 ИИ-агентов:

- [docs/TEAM.md](docs/TEAM.md) — команда, промпты, фазы, артефакты, архитектура
- [.claude/agents/](.claude/agents/) — системные промпты агентов (генерируются `scripts/build_agents.py`)
- [docs/decisions.md](docs/decisions.md) — журнал решений, [docs/risks.md](docs/risks.md) — реестр рисков

## Код

| Папка | Что |
|---|---|
| [android/](android/README.md) | приложение MATUTA (Kotlin, Compose), сборка в GitHub Actions |
| [backend/edge/](backend/edge/) | тонкий Cloudflare Worker `hot-edge` без хранения данных о здоровье |
| [content/](content/) | контент экспертов (YAML с `version` и `reviewedBy`), компилируется в APK |
| [.github/workflows/](.github/workflows/) | CI: gitleaks, тесты, APK, деплой Worker |

## Сборка и установка на Pixel

1. GitHub → **Actions** → **Android CI** → последний зелёный запуск на `main` → **Artifacts** → `matuta-<N>` (подписанный release) или `matuta-debug-<N>`; внутри zip — APK. Когда настроена подпись, APK есть и в **Releases** → `build-<N>`.
2. На Pixel: Настройки → Приложения → Специальный доступ → «Установка неизвестных приложений» → разрешить Chrome или «Файлы».
3. Открыть APK → «Установить» (на предупреждение Play Protect — «Всё равно установить»).
4. Новые сборки ставить поверх: данные сохраняются, пока ключ подписи тот же.

Подробности, секреты подписи и ручной чек-лист — [android/README.md](android/README.md).
