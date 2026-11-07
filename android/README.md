# MATUTA — Android-приложение HoT

Нативное приложение для Pixel 9 Pro XL: Kotlin, Jetpack Compose, Material 3, Hilt, Room + SQLCipher.
Архитектура — `docs/artifacts/A14-architecture.md` (v0.2), экраны — `A11-ux-screens.md`.
`applicationId = com.healthofatitan.app`, MATUTA — кодовое имя.

## Что есть в срезе 0

| Часть | Где |
|---|---|
| Версии — только в каталоге | `gradle/libs.versions.toml` (Gradle 9.8.2, AGP 9.5.0, Kotlin 2.4.21, KSP 2.3.13, Compose BOM 2026.09.00, Hilt 2.60.1, Room 2.8.5, SQLCipher 4.20.0, Tink 1.24.0, core-splashscreen 1.2.0) |
| Convention-плагины | `build-logic/convention`: `hot.android.application`, `hot.android.library`, `hot.android.feature`, `hot.jvm.library`, `hot.hilt`, `hot.room`, `hot.content` |
| Правила модулей | `./gradlew checkModuleGraph`: feature ↛ feature, domain только чистый Kotlin, integration ↛ feature, core ↛ feature/domain/integration |
| Навигация | `:app` — 4 вкладки «Сегодня · Наставник · Неделя · Я» + маршрут `sos` вне вкладок; кнопка SOS в верхней панели каждой вкладки; плитка быстрых настроек «HoT SOS»; ярлык SOS на иконке |
| SOS | `:feature:sos` — офлайн-экран, кнопки «988 — поговорить» и «911» открывают набор номера (звонок подтверждает пользователь) |
| Тема «Колоннада» (A36, D-028) | `:core:ui/theme` — `HotTheme`: тёмная «Ночь» по умолчанию и светлая «Мрамор» (A36 §9), `Kolonnada` (палитра), `HotColors` (грани, режимы Разгон/Ровно/Бережно, линии `rule`/`hairline`, `textTertiary`), `HotTypography` + `HotType` (Cormorant Garamond для заголовков от 20 sp, Manrope для текста и цифр с `tnum`), `HotShapes` (4/8/12/18/28 dp). Красный — только `error` для флагов и экстренной помощи (`RedOnlyForFlagsTest`). Material You выключен по умолчанию |
| Компоненты | `:core:ui/component` — `HotTopBar` (локап «знак + HoT», SOS-капсула, надстрочник, заголовок антиквой), `HotBackground` («ночное небо»), `HotCard` (тон + волосяная линия, золотой карниз), `HotPendingCard` (пунктир «ещё нет данных»), `HotOverline`, `HotLockup`/`HotMark`; `SecureScreen` (`FLAG_SECURE`) |
| Знак и иконки | адаптивная иконка (`mipmap-anydpi/ic_launcher.xml`: фон и тематический слой — вектор, передний план — WebP по плотностям со свечением), `ic_stat_hot` для уведомлений, трезвый ярлык SOS, линейные иконки A36 §5. Всё собирается из `brand/mark-v2/` скриптами `brand/mark-v2/android/` (§ 11 README знака) |
| Сплэш | `androidx.core:core-splashscreen`, тема `Theme.Hot.Starting`: фон «Графит» `#11151E`, `avd_hot_splash` — «пульс оживляет титана», 1000 мс; держится до конца анимации только при холодном запуске с рабочего стола, SOS не ждёт |
| Шрифты | `core/ui/src/main/res/font/`: Cormorant Garamond Medium, SemiBold, Medium Italic; Manrope Light, Regular, SemiBold — статичные начертания, Latin + Cyrillic, ≈ 480 КБ. Лицензия SIL OFL 1.1: `core/ui/src/main/assets/licenses/OFL-*.txt` (попадают в APK) |
| Шифрование | `:core:security` — `K_db` (32 байта, обёрнуты ключом Android Keystore), `K_field` (Tink AEAD); `:core:database` — Room + SQLCipher, таблица `consent_record`, `PRAGMA secure_delete = ON`, экспорт схемы в `core/database/schemas/` |
| Контент | `content/` (корень репозитория) → задача `:app:contentCompile` → JSON в assets + `content/manifest.json`; битый YAML или нет `version`/`reviewedBy` — сборка падает с именем файла |
| Тесты | `NoRawWhoopColumnsTest` (в схеме Room нет сырых полей WHOOP), roundtrip Tink AEAD, «красный только для флагов», навигация и SOS (Robolectric + Compose) |

Ещё не сделано в срезе 0 (следующие шаги A14 §9): онбординг с дисклеймером и согласием (тексты — Агент 7), «Удалить всё», `BiometricGate`, `:core:datastore`, R8 для release.

## Что добавил срез 1 «WHOOP подключён, HoT знает мою норму» (A18 И1.1–И1.3)

| Часть | Где |
|---|---|
| Вход в WHOOP | `:integration:whoop` — AppAuth 0.11.1 (браузер, redirect `com.healthofatitan.app://oauth/whoop`), PKCE создаётся на телефоне, `client_id`/`state` даёт Worker (`/whoop/authorize`), код меняет Worker (`/whoop/token`). Телефон хранит только **сессию устройства** (Tink AEAD) и access-токен **в памяти**; refresh-токен живёт в Worker (A14 ADR-04) |
| Токены | `WhoopAccessTokens` — single-flight (Mutex): параллельные запросы делят один `/whoop/access`; `reauth_required` стирает сессию → статус «Вход в WHOOP истёк» |
| Окно данных | `WhoopApi` + `WhoopWindowMapper` — 28 дней (35 с запасом) cycles/recovery/sleep/workouts **в памяти**; день = дата пробуждения; в БД попадают только производные |
| Показатели | `:domain:metrics` (чистый Kotlin) — ORS, SLP, SLPDEBT, TRL, ECAP («Заряд», 50 = норма), LRB («Весы»); робастный z (медиана/MAD, n ≥ 14), EWMA 7, покрытие ≥ 60 % (A25) |
| База | `:core:database` v2 — `metric_daily` (`metricId@ver`, D-023), `baseline_snapshot`, `sync_state`; миграция 1→2 вручную (`MigrationTest`); `NoRawWhoopColumnsTest` расширен (`step_count`, `zone_*`, пульс, sleep need) |
| Фон | WorkManager: синхронизация при входе, при открытии приложения (если данным > 15 мин), раз в 3 часа и повтор с backoff без сети («Обновлю, когда будет связь») |
| Экраны | «Я → Подключения → WHOOP» (подключить, статус, «Твоя норма N из 14», синхронизировать, отключить с удалением токенов, экран «доступа нет» с WHOOP ID); «Сегодня» — карточка «Состояние»: «Учусь твоей норме: нужно ~14 дней», затем Заряд словами + число, Весы (недогрузка/баланс/нагрузка копится), сон и недосып |
| Сеть | `:core:network` — OkHttp без логирования тел, только HTTPS (`EdgeConfig`) |

## Синхронизация «с вау» и диагностика (после build-54)

| Часть | Где |
|---|---|
| Индикатор синхронизации | `:core:ui` `HotSyncMark` — «Пульс сквозь колоннаду»: комета пульса бежит по линии знака и зажигает колонны и титана, кольцо рисуется по реальному прогрессу (токен → дни → восстановление → сон → тренировки → норма). Успех: кольцо смыкается, волна, галочка, тактильный «Confirm». Ошибка: пунктирное кольцо цвета «Известняк» и «!», без красного. «Удалить анимацию» — статично. Визуал заменяемый: цвета, штрихи и тайминги — `SyncMarkStyle` из токенов темы, фигура — `SyncMarkArt` (`HotMarkArt` из `brand/mark-v2`, `build_sync_mark.py`) |
| Где виден | экран WHOOP (большой знак: тап — обновить, долгое нажатие — журнал), шапка всех вкладок (знак в локапе), «Сегодня» — потянуть вниз |
| Состояние | `WhoopSyncer.progress` (`SyncProgress`: Idle / Running(step) / Succeeded(counts) / Failed(reason, HTTP)), `SyncIndicators` (чистое сопоставление, тесты) |
| Причины сбоя | `SyncFailures.classify`: нет сети, вход истёк, список доступа сервера, WHOOP 403 / 4xx / 5xx / 429, сервер HoT, формат ответа, прочее |
| Журнал | 20 последних попыток в `noBackupFilesDir/whoop_sync_log.json`: время, кто запустил, итог, HTTP-код, шаг, длительность, число дней/ночей/тренировок. Без данных о здоровье и токенов. «Я → WHOOP → Журнал» (или долгое нажатие на знак), «Скопировать» |
| «Сегодня» до нормы | `WhoopGlance` — последняя ночь и потребность во сне, ВСР и пульс покоя, последняя тренировка — **только в памяти** (R-11, D-009); после перезапуска — производные из БД (SLP, SLPDEBT, ORS) и синхронизация при открытии |
| Ручная синхронизация | идёт сразу в области приложения, а не ждёт WorkManager; без сети — сразу «Нет сети — обновлю сам» и повтор через WorkManager |

## Сборка

Нужны JDK 21 и Android SDK (platform 37).

```bash
cd android
./gradlew checkModuleGraph :core:model:test :domain:metrics:test testDebugUnitTest :app:assembleDebug
```

Конфиг сборки (`BuildConfig.EDGE_BASE_URL` — адрес Worker `hot-edge`) берётся из переменных окружения или из `android/local.properties` (в git не попадает; образец — `local.properties.example`). В CI — из **repository variable** `EDGE_BASE_URL` (Settings → Secrets and variables → Actions → **Variables**). Это не секрет. `client_id` WHOOP приложение получает от Worker, client secret живёт только в Worker. Без `EDGE_BASE_URL` приложение собирается, а экран WHOOP пишет, что сервер не настроен.

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
6. Знак и «Колоннада» (D-028): на рабочем столе иконка — золотой титан с колоннами в круге (со свечением); при «Тематических значках» — одноцветный силуэт. Холодный запуск: сплэш на графите, пульс прочерчивается слева направо, проявляется титан, около 1 с. Запуск из плитки или ярлыка SOS — без задержки. Шапка вкладок: «знак + HoT» золотом, капсула SOS, надстрочник с датой и заголовок антиквой. Экран SOS — без золота и антиквы.

### Ручной чек-лист среза 1 (на Pixel, 10 минут)

Перед этим основатель задал секреты Worker и переменную `EDGE_BASE_URL` (см. `backend/edge/README.md`, «Чек-лист основателя»), а Backend и Android CI зелёные.

1. Поставить свежий `matuta-<N>.apk` поверх прежнего: приложение открывается, вкладки на месте (миграция базы 1→2 прошла).
2. «Сегодня» без WHOOP: карточка «Утро за пару минут…» и золотая кнопка «Подключить WHOOP» → открывает «Я · Подключения → WHOOP».
3. «Я» → строка WHOOP «Не подключён» → экран WHOOP: что HoT возьмёт, как бережёт данные, кнопка «Подключить WHOOP».
4. «Подключить WHOOP» → страница входа WHOOP в браузере → войти → вернуться в HoT.
   - **Первый вход (WHOOP ID ещё не в списке):** экран «Вход выполнен, доступа пока нет» с номером WHOOP ID и кнопкой «Скопировать». Вписать номер в секрет `ALLOWED_WHOOP_USER_IDS`, дождаться деплоя Backend и нажать «Подключить WHOOP» ещё раз.
   - **В списке:** статус «Подключён», «Загружаю 28 дней данных…», затем «Синхронизация HH:mm» и «Твоя норма: N из 14 ночей» (или «Норма собрана»).
5. «Отмена» на странице WHOOP → «Вход отменён…», ничего не сломано.
6. «Сегодня»: при < 14 ночах — «Учусь твоей норме: нужно ~14 дней» и N из 14; при ≥ 14 — «Заряд» словами («Около твоей нормы» и т. п.) с числом «· норма 50», «Весы» (недогрузка / баланс / нагрузка копится), строка сна. Общего балла нет.
7. Перезапустить приложение (смахнуть из недавних): WHOOP остаётся подключён — вход переживает перезапуск.
8. Режим полёта → «Синхронизировать»: «Обновлю, когда будет связь». Выключить режим полёта — через минуту-две синхронизация проходит сама (WorkManager).
9. «Отключить и удалить токены» → подтверждение → «WHOOP отключён. Токены удалены с телефона и с сервера». В режиме полёта отключение не выполняется и честно говорит, что нужна связь.
10. (debug, `adb`) Сырых данных WHOOP в базе нет: `NoRawWhoopColumnsTest` в CI зелёный; в `adb logcat | grep -i -E "token|bearer|hs1\."` пусто.
