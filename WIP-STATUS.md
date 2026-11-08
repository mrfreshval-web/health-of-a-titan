# WIP: срез S2 «Сегодня без WHOOP» (разрыв Р-2)

Ветка `claude/today-s2-no-whoop` от `origin/main@e92e2ba`. PR не открыт. Работа передана в новый тред.

## Что сделано
- Прочитан аудит `reviews/2026-11-08-today-dependency-audit.md` (§6 Р-2, §7.1, §8 S2), D-051, D-057 п. 5, A09 REG-05, A46 §4.1/4.1a.
- Код **не менялся**. Составлен план ниже, проверены точки входа.

## Найденные точки (почему тупик)
- `domain/today/TodayModel.kt:79`: `!whoopConnected -> NO_WHOOP` стоит раньше всех сценариев. `MeRules` прячет режим и данные.
- `domain/today/Tasks.kt:120`: в `NO_WHOOP` единственное дело «Подключить WHOOP».
- `domain/today/Rhythm.kt:65,69,74`: в `NO_WHOOP` только «Рассвет» (неактивный), без окон и без «Закрыть день».
- `domain/today/Supports.kt:169`: `noData = scenario == NO_WHOOP`.
- `integration/whoop/day/DayPlanRepository.refresh()`: без WHOOP план ждёт до 09:30 (`MANUAL_FROM`). Health Connect сна в движок не идёт.
- `feature/today/DayUiState.eveningFor` и `BlockRhythm` требуют план. Если план строится сразу, «Закрыть день» появляется.
- `domain/engine/DayTexts.MANUAL` говорит «WHOOP ещё не посчитал ночь». Без WHOOP этот текст неверен.
- Health Connect: только `READ_STEPS` (`integration/healthconnect/PhoneSteps.kt`, манифест модуля, `app/.../health/HealthRationaleActivity.kt`).

## План (что осталось)
1. **Домен `today`.** Ввести `DataSource {WHOOP, HEALTH_CONNECT, MANUAL, NONE}`, а в `DayFacts` добавить `healthConnect` и `selfReported`. Переименовать `NO_WHOOP` в `NO_SOURCE` (4.1), добавить `SELF` (4.1a). Порядок в `Scenarios.of`:
   - SAFETY;
   - RETURN;
   - NIGHT_PENDING (только с WHOOP);
   - GENTLE;
   - EVENING;
   - AFTER_WORKOUT;
   - без WHOOP: NO_SOURCE или SELF;
   - AFTER_DAWN;
   - DAY.
2. **`MeRules`** получает `source`:
   - без WHOOP режим не выше «Ровно» (REG-05) и с пометкой `estimate` («оценка»);
   - при NONE режим скрыт и стоит фраза «Я ещё не знаю тебя»;
   - ночь и пульс покоя берутся из Health Connect;
   - Заряд и калибровка остаются только для WHOOP.
3. **`TaskRules.pick`.** Убрать фиксированное «Подключить WHOOP», дела недели идут как обычно. «Подключить источник» становится мягкой карточкой: `TodayView.connect`, внутри блока «Я сегодня», кнопки WHOOP и Health Connect плюс «Не сейчас».
4. **`RhythmRules`.** Обычные ритуалы, включая «Закрыть день», и окна тела из плана `MANUAL`.
5. **`Supports`.** `noData` только для `NO_SOURCE`.
6. **`DayPlanRepository`.** Новые параметры `whoopConnected: suspend () -> Boolean` и `otherNight: suspend (LocalDate) -> Double?`. Без WHOOP план строится сразу, без ожидания 09:30. `sleepHours` берётся из Health Connect, флаг `MANUAL` остаётся, потолок «Ровно» уже стоит в `RegimeEngine`. В `DataFacts.whoopConnected` нужен отдельный текст MANUAL без WHOOP. DI: `WhoopModule.provideDayPlans` получает `WhoopCredentialStore`, а привязку источника ночи Health Connect нужно добавить в `app/di/TodayModule.kt`, потому что `:integration:whoop` не зависит от healthconnect.
7. **Health Connect.** Нужны:
   - `READ_SLEEP` и `READ_RESTING_HEART_RATE` в манифесте модуля;
   - `HealthNightRepository` (сон прошлой ночи: сессии, которые кончились с 18:00 вчера до 14:00 сегодня, минус стадии бодрствования; последний пульс покоя; без источника `com.whoop.android`; только в памяти, R-11);
   - чистый `NightMath` с тестом;
   - `DayViewModel` + `DayUiState` получают `health`;
   - запуск разрешений из карточки, затем перечитать данные и вызвать `morning.onTodayOpened()`.
8. **Тексты.** Все новые строки помечаются как черновик (ждёт ревью 10 и 16; рационале Health Connect ждёт ревью 7). Их нужно добавить в `TodayCopy.all()` и в `GenderNeutralTextsTest.examples`.
9. **Тесты** (`TodayRulesTest`, `TodayPolishTest`):
   - первый день без источника: есть дело недели, есть «Закрыть день», режим скрыт, есть карточка источника;
   - только Health Connect: SELF, режим не выше «Ровно», ночь показана;
   - только ручной ввод: SELF по самоощущению, не выше «Ровно».

   Старые проверки `NO_WHOOP` в `TodayRulesTest:20,35,65,208` и `TodayPolishTest:108-113` нужно переписать.
10. **Room-миграция не нужна** (версия остаётся 15).

## Известные проблемы и вопросы
- Новые разрешения Health Connect (сон, пульс) расширяют D-024 «только шаги». Основание: D-051 и D-057 п. 5. Тексты рационале требуют ревью Агента 7.
- Часы сна из Health Connect попадают в движок. В план сохраняются только производные (режим, тексты «почему»), сырые значения не хранятся.
- «Усердие» по пульсу часов (A46 4.1a) в срез не входит.
- Не трогать `HotApp.kt` (C1), тренировку (S1), «Тарелку дня» (S3), кризисный протокол, 112/911, KSP.
