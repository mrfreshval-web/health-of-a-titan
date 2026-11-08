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
| `POST /llm/mentor` | сессия + allowlist | **Винс v1** (A62, D-056): одна реплика разговора, см. ниже |
| `/whoop/webhook`, `/push/*`, остальные `/llm/*`, `/stt`, `/tts` | вебхук-HMAC / сессия + allowlist | заглушки `501` до своих срезов |

Без `WHOOP_CLIENT_ID`, `WHOOP_CLIENT_SECRET`, `TOKEN_ENC_KEY` или привязки `WHOOP_VAULT` маршруты WHOOP отвечают `503 not_configured`.

## Аккаунт HoT (A65, D-062; срезы А1 и А2)

| Маршрут | Доступ | Что делает |
|---|---|---|
| `POST /auth/nonce` | лимит по IP | одноразовый `nonce` (5 мин) для Google и миграции |
| `POST /auth/google` | лимит по IP | ID-токен Google (подпись по JWKS, `aud`, `iss`, `exp`, `iat`, `nonce`) + ключ устройства → сессия; нет аккаунта → `404 no_account` и `signup_ticket` |
| `POST /auth/signup` | лимит по IP | `signup_ticket` + код приглашения → новый аккаунт |
| `POST /auth/refresh` | лимит по IP | ротация refresh, подпись ключом устройства; повтор старого refresh в 30 с — тот же ответ, позже — отзыв всей сессии |
| `POST /auth/logout` · `/auth/logout-all` | access | выйти · выйти везде (`session_epoch++`) |
| `POST /auth/migrate` | `Bearer hs1.…` + ключ устройства | старая сессия среза 1 → аккаунт с identity WHOOP; `hs1` гасится |
| `POST /auth/invites` | access, только аккаунты основателя | код приглашения (26 символов, 14 дней) |

- `POST /whoop/token` теперь возвращает сессию аккаунта (`access`, `refresh`, `account_id`, `identities`) и `whoop_access_token`. Новый пользователь WHOOP без аккаунта — `403 invite_required` (ничего не хранится, грант отозван); пользователь из `ALLOWED_WHOOP_USER_IDS` получает аккаунт сразу (переходный период).
- Access — JWS EdDSA (Ed25519, `kid`), 15 мин, не хранится. Refresh `hr1.<sid>.<32 байта>`: в хранилище только HMAC с «перцем» (ключ из `TOKEN_ENC_KEY`), текущий и предыдущий.
- Хранилище — Durable Object `AccountVault` за интерфейсом `AccountStore`; токены WHOOP — `WhoopVault` за `TokenVault` (перенос в РФ — срез Б1, A65 §7.2).
- Секреты: `AUTH_SIGNING_KEY` (`node scripts/gen-signing-key.mjs | gh secret set AUTH_SIGNING_KEY`), `GOOGLE_WEB_CLIENT_ID`; при ротации ключа — `AUTH_SIGNING_KEY_PREVIOUS` на 15 минут. Без них маршруты `/auth/*` и `/whoop/token` отвечают `503 not_configured`.

## Винс: `POST /llm/mentor` (A62, D-056)

Код — `src/mentor/` (`router.ts` — маршрутизация по теме, `prompt.ts` — системный промпт, `budget.ts` — деньги и `MentorMeter`, `mentor.ts` — обработчик). Клиент Anthropic — официальный `@anthropic-ai/sdk`.

- **Запрос телефона:** `{v:1, task:"reply"|"summary", persona:"sensei"|"bro"|"butler", tier_floor, crisis_level, expert_topics, context:{address_form, restrictions, flags, summaries, about?}, messages:[{role, content}]}`. Неизвестные поля и коды отбрасываются; фото, данные WHOOP и Health Connect, причины `ED_CARE` в контракте не предусмотрены.
- **Маршрут по цене ошибки темы (A62 §2):** болтовня и поддержка — `claude-haiku-5-5`; питание — Haiku; цели, стресс, «Работа», тренировки — `claude-sonnet-5-5`; симптомы, анализы, лекарства, боль и травма, беременность, сигналы РПП, психологическая тяжесть, режим «Здоровье» и тренировки при «уровне эксперта» — `claude-opus-5-5`. Лифт только вверх: телефон присылает `tier_floor` — высший уровень разговора. CrisisGuard уровня 1 — не ниже Sonnet. Классификатор Haiku — следующий шаг, словарь остаётся полом.
- **Кризис:** `crisis_level ≥ 2` модель не видит никогда — ответ `{type:"crisis"}` без вызова и вне лимитов. Модель — второй слой: на риск для жизни она отвечает только `[[SOS]]`, Worker превращает это в `{type:"crisis", level:2}`, телефон открывает «Помощь» (112 · 911).
- **Деньги (D-056):** `MentorMeter` (Durable Object на пользователя, миграция `v3-mentor-meter`) хранит только суммы в USD за день и месяц. Мягкий порог $0,40 в день — разговор на Haiku (темы высокого уровня остаются на Opus); жёсткий $1,50 в день и $10 в месяц — `429 {error:"budget_exhausted"}`; не больше 12 запросов в минуту. Дни и месяцы — по UTC.
- **Кэш промпта:** неизменная часть промпта первой, затем характер и ContextPack с точками кэша. Opus и Sonnet — с серверным `fallbacks: "default"` (бета `server-side-fallback-2026-07-01`) на случай отказа модели.
- **Ошибки:** нет `ANTHROPIC_API_KEY` или ключ отклонён → `503 {error:"not_configured", missing:"ANTHROPIC_API_KEY"}`; нет привязки `MENTOR_METER` → `503 … "MENTOR_METER"` (без счётчика денег модель не вызывается); модель вне `LLM_MODELS` → `503 … "LLM_MODELS"`.
- **Логи:** как и везде — только `{m, s, ms}`; ни слов, ни тем, ни моделей.

Системный промпт и тексты — **черновик, ждут ревью Агентов 3 и 10** (раскрытие ИИ — 7).

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
   - `CLOUDFLARE_API_TOKEN`, `CLOUDFLARE_ACCOUNT_ID` — уже есть со среза 0;
   - `ANTHROPIC_API_KEY` — ключ Claude API для Винса (console.anthropic.com → API Keys); в консоли Anthropic поставь лимит расходов $30 в месяц на всё (A62 §2).
3. **Деплой Worker:** после мержа в `main` workflow **Backend (hot-edge)** задеплоит Worker; адрес — `https://hot-edge.<account>.workers.dev` (виден в логе шага deploy и в дашборде Cloudflare). Проверка: `curl https://hot-edge.<account>.workers.dev/health`.
4. **GitHub Variable** (Settings → Secrets and variables → Actions → **Variables**): `EDGE_BASE_URL` = адрес Worker из шага 3 (не секрет). Затем перезапустить Android CI на `main` и поставить новый APK.

Ротация `TOKEN_ENC_KEY`: задать новый секрет → деплой → в приложении «Переподключить» (старые записи не расшифруются: до нового входа синхронизация будет говорить «сервер HoT не отвечает», новый вход перезапишет запись).
