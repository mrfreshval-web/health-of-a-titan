# WIP · S1 «Профиль и шина → тренировка» (Р-1, вето Агента 3)

Ветка `claude/today-s1-training-safety` от `origin/main@e92e2ba`. Работа остановлена по команде координатора и переходит в новый тред.

## Что сделано
- Прочитаны аудит `reviews/2026-11-08-today-dependency-audit.md` (§3, Р-1, S1), A19 §1.2 и §6, A40 §2.5, §4.2, §8, §11.1, A60 §3, §7.
- Прочитан код: `domain/safety` (SafetyFlag, SafetyPolicy, SafetyState, SafetyProjector), `domain/training` (TrainingEngine, TrainingProfile), `feature/training` (TrainingDay, TrainingScreen/TrainingViewModel, WorkoutController, TrainingNavigation, TrainingTexts), `domain/engine` (DayEngine, TrainingSelector, Model, DayTexts, Templates, RuleSet), `core/model/profile` (UserConstraints, ProfileDictionary), `core/database/ProfileStore`, `integration/whoop/safety/SafetyBus`, `app/di/TodayModule`, `app/training/TrainingModule`, `content/profile/constraints.yaml`, `content/training/rules.yaml`.
- **Код не менялся.** В ветке только этот файл.

## Что осталось (план)
1. **`domain/safety`**: потребитель `SafetyConsumer.TRAINING` с полной колонкой в `SafetyPolicy` (исчерпывающий `when`, L-05).
   - EMERGENCY / CRISIS_L2 / CRISIS_L3 → STOP (+ `NO_LOAD`); в тренировке `HELP_FIRST` → `TrainingFlag.EMERGENCY` (TG-01).
   - SICK → `NO_LOAD` → `TrainingFlag.SICK` (TG-02, мобильность ≤ 20 мин Z1).
   - MEDS_HR, CHRONIC, INJURY, SCREEN_NOT_CLEARED → `INTENSITY_CAP` (как колонка TODAY).
   - PREGNANT → новый `Restriction.NO_PROGRAM` (TG-05, constraints `life_stage.pregnancy: program none, route doctor`).
   - POSTPARTUM_12M → `NO_PROGRAM` весь срок (правило есть только для 6 мес.) — **⚑3**.
   - LACTATING → `INTENSITY_CAP` (в constraints только `route: doctor`) — **⚑3**.
   - ED_CARE → `ED_HIDDEN` + новый `NO_EXTRA_SESSIONS` → `TrainingFlag.ED_CARE` (TG-06).
   - AGE_65P, MEDS_PSY → `INTENSITY_CAP` (правила нет, строже) — **⚑3**; MENOPAUSE, HEALTH_ANXIETY, данные → ∅ — **⚑3**; RETURN/LIGHT_WEEK приходят флагами дня (∅ в шине).
   - Тест: в `SafetyBusRulesTest` — у каждого флага есть политика TRAINING; unknown → strictest.
2. **`domain/training/TrainingEngine`**: `TrainingFlag.INTENSITY_CAP` (те же ограничения, что TG-04: Классика, RPE ≤ 6, без hiit/sprint/metcon/strength_periodized, без heavy_load/valsalva/max_speed/inversion; общий приватный хелпер с TG-04 — **⚑3**: перенос TG-04 на шину) и `TrainingFlag.NO_PROGRAM` (ворота TG-05). Строки «Почему так»: `NO_PROGRAM` дополнить текстом из `rules.yaml TG-05 note_ru` («Обсуди нагрузки с врачом.»); маршруты `route: physio / doctor / doctor_before_intensity` из `profile_codes` → строки из A40 §11.1 («к врачу или физиотерапевту») и A19 §6.1 («Обсуди с врачом, какие нагрузки тебе подходят»). Проверить, парсит ли `ProfileCodeRule` поле `route`.
3. **`feature/training/TrainingProfileSource.kt`** (новый): `UserConstraints` + `ProfileDictionary` → `TrainingProfile`:
   - `injuries`: пресет → `region → status` (при двух пресетах на регион — строже: acute > recovering > chronic_stable);
   - `movementLimits = dict.movementLimits(c)` (включая пресеты беременности и давления);
   - `lifeStages = c.lifeStage`, `medicalFlags = c.medical`;
   - медфлаги → ответы скрининга (A19 §6.1): `heart_bp_meds` → S1; `mode: safe` (diabetes, kidney, gi_disease, other_chronic, bariatric) → S4; `anticoagulants` → S5; без отметки «врач разрешил» → `screening_not_cleared` (TG-04) — **⚑3** (строже; UI «врач разрешил» нет, Р-14).
   - Без согласия S4 профиль пуст (как в `ProfileStore`).
4. **`TrainingDay.kt` / `TrainingRequests`**: `request(...)` получает `codes` из профиля и флаги из `restrictions(TRAINING)`; ED_CARE и EMERGENCY из шины (как в аудите S1).
5. **`TrainingViewModel`**: `combine` с `ProfileStore.profile` и `SafetyBus.state` (через небольшой класс `TrainingSafety` в feature:training); `start()` не запускает Hidden. **Плеер**: `PlayerViewModel` слушает шину и ставит паузу при `HELP_FIRST` во время тренировки.
6. **DayEngine / TrainingSelector**: в `domain/engine` — чистый `TrainingGuard` в `DayInput` (`hide: EMERGENCY|NO_PROGRAM|DOCTOR`, `noLoad`, `gentle`, `noIntervals`, `notes`). Шаг 0 селектора: hide → REST L3 без слота + строка «обсуди с врачом»; noLoad → как SICK; gentle → только Zone 2 лёгкая / мобильность / прогулка (A19 §6.1: «только T08 в лёгком темпе, T14, T15»); noIntervals → интервалы в Zone 2. В `trainingDetail` — подписи ограничений из `constraints.yaml movement_limits[].ru` («Без прыжков и ударной нагрузки»). Новые `RuleRef` в `RuleSet`.
   - `DayPlanRepository` (integration/whoop) получает `TrainingGuardSource` (fun interface, по умолчанию NONE); привязка — в `app/training/TrainingModule.kt` (не трогать `TodayModule`/`WhoopModule`/`HotApp` без нужды: там срезы С1, S2, S3).
7. **Тесты** (и в деле дня, и в плеере):
   - `TrainingProfileSourceTest`: колено → нет упражнений с `high_impact` и паттерном `jump`; беременность → `pregnancy` → Hidden NO_PROGRAM + строка про врача; давление → без `valsalva`.
   - `TrainingEngineTest`: коды из реального `constraints.yaml` + `rules.yaml`; `INTENSITY_CAP` → `rpeMax ≤ 6`.
   - `TrainingRequestsTest`: шина → флаги; `WorkoutSteps.of(session)` без прыжков (плеер).
   - `DayScenariosTest`/селектор: беременность → REST без слота; `INTENSITY_CAP` → нет силовой/интервалов; колено → подпись ограничения.
8. Коммиты (англ., с трейлерами), push, PR в main по-русски, CI до зелёного (Room 15 не трогать, KSP 2.3.12, кризисный протокол и 112/911 не менять).

## Известные проблемы и вопросы
- Проверить, что все упражнения паттерна `jump` в `exercises.yaml` несут тег `high_impact`; иначе пресет `knee_injury` (статус recovering → код `knee_pain`) прыжки не уберёт — тогда строже: запрет паттерна `jump` для `knee_pain` (**⚑3**).
- Пресеты травм в UI имеют фиксированный статус (`recovering`/`chronic_stable`), острой травмы (`acute` → `route: physio`) из UI не выбрать.
- Стоп-сигналы F1–F5 (TG-07) никто не записывает («Мне плохо» не пишет код F; Р-11/S5). В S1 «стоп-сигналы» — группа STOP шины.
- У флагов шины кроме CRISIS и ED_CARE нет производителей (Р-15): колонка TRAINING для них — политика на будущее; фактически профиль идёт кодами профиля.
- `ProfileDictionary` в приложении: проверить, где грузится `constraints.json` (питание) и переиспользовать.
- Тексты «Почему так» и подписи — ревью Агентов 3 и 10.
