# MATUTA — Android-приложение HoT

Нативное приложение для Pixel 9 Pro XL: Kotlin, Jetpack Compose, Material 3, Hilt, Room + SQLCipher.
Архитектура — `docs/artifacts/A14-architecture.md` (v0.2), экраны — `A11-ux-screens.md`.
`applicationId = com.healthofatitan.app`, MATUTA — кодовое имя.

## Что есть в срезе 0

| Часть | Где |
|---|---|
| Версии — только в каталоге | `gradle/libs.versions.toml` (Gradle 9.8.2, AGP 9.5.0, Kotlin 2.4.21, KSP 2.3.13, Compose BOM 2026.09.00, Hilt 2.60.1, Room 2.8.5, SQLCipher 4.20.0, Tink 1.24.0) |
| Convention-плагины | `build-logic/convention`: `hot.android.application`, `hot.android.library`, `hot.android.feature`, `hot.jvm.library`, `hot.hilt`, `hot.room`, `hot.content` |
| Правила модулей | `./gradlew checkModuleGraph`: feature ↛ feature, domain только чистый Kotlin, integration ↛ feature, core ↛ feature/domain/integration |
| Навигация | `:app` — 4 вкладки «Сегодня · Наставник · Неделя · Я» + маршрут `sos` вне вкладок; кнопка SOS в верхней панели каждой вкладки; плитка быстрых настроек «HoT SOS»; ярлык SOS на иконке |
| SOS | `:feature:sos` — офлайн-экран, кнопки «988 — поговорить» и «911» открывают набор номера (звонок подтверждает пользователь) |
| Тема | `:core:ui` — тёмная по умолчанию, seed `#C9A23A`, токены граней и режимов A11 §12.1; красный — только `error` для флагов и экстренной помощи; `SecureScreen` (`FLAG_SECURE`) |
| Шифрование | `:core:security` — `K_db` (32 байта, обёрнуты ключом Android Keystore), `K_field` (Tink AEAD); `:core:database` — Room + SQLCipher, таблица `consent_record`, `PRAGMA secure_delete = ON`, экспорт схемы в `core/database/schemas/` |
| Контент | `content/` (корень репозитория) → задача `:app:contentCompile` → JSON в assets + `content/manifest.json`; битый YAML или нет `version`/`reviewedBy` — сборка падает с именем файла |
| Тесты | `NoRawWhoopColumnsTest` (в схеме Room нет сырых полей WHOOP), roundtrip Tink AEAD, «красный только для флагов», навигация и SOS (Robolectric + Compose) |

Ещё не сделано в срезе 0 (следующие шаги A14 §9): онбординг с дисклеймером и согласием (тексты — Агент 7), «Удалить всё», `BiometricGate`, `:core:datastore`, R8 для release.

## Сборка

Нужны JDK 21 и Android SDK (platform 37).

```bash
cd android
./gradlew checkModuleGraph :core:model:test testDebugUnitTest :app:assembleDebug
```

Конфиг сборки (`BuildConfig.WHOOP_CLIENT_ID`, `BuildConfig.EDGE_BASE_URL`) берётся из переменных окружения или из `android/local.properties` (в git не попадает; образец — `local.properties.example`). Это не секреты: client secret WHOOP живёт только в Worker.

## CI (GitHub Actions)

`.github/workflows/android-ci.yml` на каждый push и PR:
1. gitleaks по всей истории;
2. `checkModuleGraph`, `contentCompile`;
3. unit-тесты, lint;
4. debug APK → artifact `matuta-debug-<N>`;
5. **только если заданы секреты `HOT_KEYSTORE_*`**: подписанный release APK → artifact `matuta-<N>`, а на `main` — pre-release `build-<N>` с файлом `matuta-<N>.apk`. Без секретов шаг пропускается с пометкой в логе, сборка зелёная.

`versionCode` = номер запуска CI, поэтому каждая следующая сборка ставится поверх предыдущей.

### Секреты (вводит основатель, A14 §10)

Никаких значений в чат и в код. В GitHub Codespaces репозитория:

```bash
keytool -genkeypair -v -keystore hot.jks -keyalg RSA -keysize 4096 -validity 10000 -alias hot
base64 -w0 hot.jks | gh secret set HOT_KEYSTORE_BASE64
gh secret set HOT_KEYSTORE_PASSWORD   # пароль хранилища
gh secret set HOT_KEY_ALIAS           # hot
gh secret set HOT_KEY_PASSWORD        # пароль ключа
```

Сохраните `hot.jks` и пароли в менеджере паролей. **Потеря ключа = переустановка с потерей данных.**

## Сборка и установка на Pixel

1. **Скачать APK.** GitHub → вкладка **Actions** → workflow **Android CI** → последний зелёный запуск на `main` → блок **Artifacts** внизу страницы → `matuta-<N>` (release, если настроена подпись) или `matuta-debug-<N>`. Скачается zip, внутри APK. Если подпись настроена, проще взять файл из **Releases** → `build-<N>` → `matuta-<N>.apk` прямо на телефоне.
2. **Разрешить установку.** На Pixel: Настройки → Приложения → Специальный доступ → «Установка неизвестных приложений» → разрешить приложению, которым открываете APK (Chrome или «Файлы»).
3. **Установить.** Открыть APK из «Загрузок» → «Установить». Если Play Protect предупредит о неизвестном разработчике — «Подробнее» → «Всё равно установить».
4. **Обновлять поверх.** Новую сборку ставьте так же, поверх старой: данные сохраняются, пока APK подписан тем же ключом (`versionCode` растёт сам).

С компьютера то же самое: `adb install -r matuta-<N>.apk`.

Важно про ключи: debug и release подписаны разными ключами, поэтому переход с debug-сборки на release требует один раз удалить приложение (данные debug-версии пропадут). Debug-ключ в CI кешируется, чтобы debug-сборки ставились друг поверх друга, но кеш GitHub может истечь (7 дней без запусков) — тогда тоже понадобится переустановка. Для ежедневного использования — только release.

### Ручной чек-лист среза 0 (на Pixel, 5 минут)

1. Открываются 4 вкладки «Сегодня · Наставник · Неделя · Я», внизу панель вкладок.
2. На каждой вкладке справа вверху кнопка SOS (спасательный круг) → экран SOS за 1 тап; «988 — поговорить» и «911» открывают «Телефон» с набранным номером, звонок не начинается сам.
3. Шторка → карандаш → перетащить плитку «HoT SOS» → тап по ней открывает SOS (на заблокированном телефоне — после разблокировки).
4. Долгое нажатие на иконку HoT → ярлык «SOS» открывает SOS.
5. База зашифрована (только debug-сборка, нужен `adb`):
   ```bash
   adb exec-out run-as com.healthofatitan.app cat databases/hot.db > hot.db
   sqlite3 hot.db ".tables"   # ожидаем: Error: file is not a database
   ```
