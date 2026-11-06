# hot-edge — тонкий Worker HoT

Cloudflare Worker по A14 §5: прячет секреты (client secret WHOOP, ключи API), не хранит данных о здоровье и не логирует тела запросов. Код — `src/`, тесты — `test/` (Vitest), конфиг — `wrangler.toml`.

```bash
cd backend/edge
npm ci
npm test            # Vitest: crypto, state/PKCE, vault (single-flight, allowlist), маршруты
npm run typecheck
```

CI: `.github/workflows/backend.yml` — typecheck и тесты на каждый push; деплой в Cloudflare только с `main` и только если заданы `CLOUDFLARE_*`. Секреты Worker передаются в `wrangler secret put` из GitHub Secrets в том же workflow; пустые пропускаются.

## Маршруты (срез 1)

| Маршрут | Доступ | Что делает |
|---|---|---|
| `GET /health` | — | `{status:"ok", service, version}` |
| `POST /whoop/authorize {code_challenge}` | лимит 20/мин на IP | `client_id`, `redirect_uri`, скоупы и подписанный `state` (HMAC, живёт 10 мин) с зашитым PKCE `code_challenge` |
| `POST /whoop/token {code, state, code_verifier}` | лимит 20/мин на IP, `state` + PKCE | обмен кода (с `client_secret`), `user_id` из профиля. Не в allowlist → доступ в WHOOP отзывается, ничего не хранится, ответ `403 {error:"not_allowed", whoop_user_id}`. Иначе токены → `WhoopVault`, ответ `{session, access_token, expires_at, whoop_user_id}` |
| `POST /whoop/access` | `Authorization: Bearer <сессия>` | свежий access-токен (≥ 5 мин жизни); `401 reauth_required`, если WHOOP отозвал вход |
| `POST /whoop/disconnect` | сессия | отзыв доступа в WHOOP + стирание хранилища → `204` |
| `POST /whoop/refresh` | — | **удалён** (404): refresh-токен телефону не выдаётся |
| `/whoop/webhook`, `/push/*`, `/llm/*`, `/stt`, `/tts` | вебхук-HMAC / сессия + allowlist | заглушки `501` до своих срезов |

Без `WHOOP_CLIENT_ID`, `WHOOP_CLIENT_SECRET`, `TOKEN_ENC_KEY` или привязки `WHOOP_VAULT` маршруты WHOOP отвечают `503 not_configured`.

## Как хранится токен WHOOP (A18 И1.1a, A14 ADR-04)

- **Durable Object `WhoopVault`**, один на WHOOP `user_id` (`idFromName`), SQLite-хранилище (миграция `v1-whoop-vault`).
- Refresh- и access-токены зашифрованы **AES-256-GCM**; ключ выводится HKDF-SHA256 из `TOKEN_ENC_KEY` (32 байта), отдельный ключ — для HMAC `state`. В AAD — `user_id` и назначение, поэтому шифротекст нельзя переставить между пользователями или полями. Формат `v1.<iv>.<ct>` — задел на ротацию ключа.
- **Single-flight:** WHOOP ротирует refresh-токен при каждом обновлении. Внутри DO одно обновление за раз; параллельные запросы ждут его результат, а перед обновлением запись перечитывается, чтобы не тратить уже повёрнутый токен.
- `invalid_grant` от WHOOP → хранилище стирается, телефон получает `reauth_required`.
- **Сессия устройства** `hs1.<user_id>.<32 случайных байта>`: в DO лежит только её SHA-256, сравнение за постоянное время. Новый вход заменяет сессию. Каждый запрос заново проверяет `ALLOWED_WHOOP_USER_IDS`.
- **Логи:** одна строка `{m, s, ms}` (метод, статус, длительность). Ни токенов, ни тел, ни `user_id` — это проверяют тесты.

## Чек-лист основателя (до среза 1)

Значения — никогда в чат и в код. В GitHub Codespaces репозитория `gh secret set ИМЯ` спрашивает значение сам.

1. **WHOOP Developer Dashboard** (developer.whoop.com → твоё приложение):
   - Redirect URI: `com.healthofatitan.app://oauth/whoop`
   - Scopes: `offline read:recovery read:cycles read:sleep read:workout read:profile read:body_measurement`
2. **GitHub Secrets** (Settings → Secrets and variables → Actions → Secrets):
   - `WHOOP_CLIENT_ID`, `WHOOP_CLIENT_SECRET` — из дашборда WHOOP;
   - `TOKEN_ENC_KEY` — `openssl rand -base64 32 | gh secret set TOKEN_ENC_KEY` (сохрани копию в менеджере паролей; при смене ключа все входы придётся повторить);
   - `ALLOWED_WHOOP_USER_IDS` — твой WHOOP ID (числа через запятую). Если не знаешь его: оставь пустым, войди в приложении — HoT покажет номер на экране «доступа пока нет», впиши и дождись деплоя;
   - `CLOUDFLARE_API_TOKEN`, `CLOUDFLARE_ACCOUNT_ID` — уже есть со среза 0.
3. **Деплой Worker:** после мержа в `main` workflow **Backend (hot-edge)** задеплоит Worker; адрес — `https://hot-edge.<account>.workers.dev` (виден в логе шага deploy и в дашборде Cloudflare). Проверка: `curl https://hot-edge.<account>.workers.dev/health`.
4. **GitHub Variable** (Settings → Secrets and variables → Actions → **Variables**): `EDGE_BASE_URL` = адрес Worker из шага 3 (не секрет). Затем перезапустить Android CI на `main` и поставить новый APK.

Ротация `TOKEN_ENC_KEY`: задать новый секрет → деплой → в приложении «Переподключить» (старые записи не расшифруются: до нового входа синхронизация будет говорить «сервер HoT не отвечает», новый вход перезапишет запись).
