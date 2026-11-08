# WIP · S3 «Тарелка дня» на «Сегодня» (Р-3)

Ветка `claude/today-s3-plate` от `origin/main@e92e2ba`. Работа остановлена по просьбе Оркестратора и переходит в новый тред.

## Что сделано
- Прочитаны аудит `reviews/2026-11-08-today-dependency-audit.md` (§4, §6 Р-3/Р-16, §8 S3) и A41 (§3, §6.3, §8, §10).
- Изучен код: `feature/nutrition` (`PlateOfDayCard`, `NutritionRepository`, `DayFacts`, `NutritionNavigation`), `domain/nutrition/NutritionEngine`, `feature/today` (`TodayScreen.todayView`, `TodayBlocks`, `TodayPlaces.SupportScreen`, `DayViewModel`), `domain/today/Supports`, `TodayCopy`, `domain/safety` (`SafetyPolicy`, `EdCareState.afterExit`), `app/navigation/HotApp.kt`, `app/di/TodayModule.kt`.
- **Кода в ветке нет**: изменений в `android/` не делалось.

## Найдено при разборе
1. После `71182b4` `PlateOfDay(...)` не вызывается; маршрут `NUTRITION_MEAL` зарегистрирован, но ни один экран на него не ведёт. `itemRoute()` в `feature/today/DayItems.kt` нигде не вызывается (мёртвый код).
2. **Ужин расходится с NUT-01.** `DayEngine` (NUT-01) ставит ужин так, чтобы он кончался за 3 ч до отбоя: окно `[отбой−3:45; отбой−3:00]`. `NutritionEngine` ставит ужин на `отбой − 150` мин (отбой−2:30). При отбое 23:00 тарелка покажет «Ужин 20:30», а опора «сон сегодня» — «ужин до 20:00».
3. `ED_CARE` на «Сегодня»: опора «питание» показывает «0 из 4»; в `spoken` опоры «усердие» есть «… ккал», в источнике — «активные калории». `HIDE_CALORIES`, `HIDE_FOOD_TALK` и `HIDE_FOOD_WINDOWS` в `todayView` не применяются.
4. Модуль питания учитывает только `edCare.active` с шины (`underEdCare`). Скрытия после выхода из режима (`afterExit`: окна еды и калории остаются закрытыми) и `HIDE_FOOD_WINDOWS` при беременности и кормлении он не применяет.

## План (что осталось)
1. **Модель для «Сегодня»** — `domain/today/TodayFood.kt`: `FoodMeal(slot, title, timeMin?, optional)` и `TodayFood(plate: String?, meals, hideTimes, hideCounts, hideCalories, quiet)`, где `quiet` = `HIDE_FOOD_TALK`. Плюс `mealsPlanned` = число основных (не optional) слотов.
2. **Источник** — `fun interface TodayFoodSource { fun food(day: LocalDate): Flow<TodayFood> }` в `feature/today`. Внедряется в `DayViewModel` через второй `combine` в `extras`; новое поле `DayUiState.food`, при ошибке источника — `orElse(TodayFood())`.
3. **Привязка в `:app`** — новый файл `app/di/TodayFoodModule.kt` (`TodayModule` не трогать: его могут менять другие срезы). Работает от `NutritionRepository.state(clock)` и `SafetyBus.state.restrictions(SafetyConsumer.TODAY)`. Чистую функцию `todayFood(NutritionState, Set<Restriction>)` покрыть тестом в `app/src/test` на YAML из `content/` (как в `PlateOfDayCardTest`).
4. **Ужин по NUT-01** — в `NutritionDayInput` добавить `dinnerMin: Int? = null` и в движке взять `dinnerAt = input.dinnerMin ?: bedtime − 150`. `DayFacts.input` передаёт `startMin` пункта плана `DayEngine.Ids.DINNER`. Тест: отбой 23:00 → 00:00, ужин сдвигается на час. **Нужно ревью Агента 3:** меняется время ужина в плане питания.
5. **«Сегодня»**:
   - в `BlockSupports` под столбиками — компактная строка «Тарелка дня · <шаблон>» с приёмами и временем (без времени при `hideTimes`); тап ведёт в `HotRoute.NUTRITION_DAY`; при `quiet` строку не показывать;
   - `Supports.build`: `mealsPlanned` берётся из плана; при `hideCounts` опора «питание» показывается без «N из M»; при `hideCalories` из `spoken` и `source` опоры «усердие» убираются калории;
   - `SupportScreen` (NUTRITION): список приёмов берётся из `food.meals`, `TodayCopy.MEALS` остаётся запасным вариантом; добавить ссылку «Тарелка дня» (`SupportActions.onOpen`, `SupportRoute` получает `onOpenRoute`);
   - удалить мёртвый `itemRoute` или подключить его.
6. **Маршрут** — `HotRoute.NUTRITION_DAY = "nutrition_day"` в `core/model`. В `nutritionScreens(...)` добавить параметр `onOpen: (String) -> Unit = {}`, composable `NUTRITION_DAY` показывает `PlateOfDay` в прокрутке, тап по приёму ведёт в `nutritionMeal(slot)`. В `HotApp.kt` только две строки: `onOpen = open` и `NESTED[NUTRITION_DAY]` под вкладкой «Сегодня».
7. **Тесты**:
   - `TodayRulesTest`/`SupportsTest`: `mealsPlanned` из плана; при `ED_CARE` нет цифр и калорий;
   - тест «Тарелки дня» на «Сегодня»: строка видна, при `ED_CARE` в ней нет цифр;
   - тест ужина вместе с отбоем (`NutritionEngineTest`/`DayFacts`);
   - навигация «Сегодня → Тарелка дня → блюдо» в `HotAppNavigationTest` (stateful=false: при `TodayUiState()` строки нет, проверить через `NUTRITION_DAY` напрямую или на тестовом состоянии).
8. Коммит, `git push`, PR через `gh api` (описание по-русски, атрибуция), довести CI до зелёного.

## Тексты на ревью 3 и 10 (будущие)
- «Тарелка дня» — уже существует (`NutritionTexts.PLATE_OF_DAY`), в `TodayCopy` дублируется как есть.
- Новое значение опоры «питание» в бережном режиме (черновик, например «по себе») — **черновик, ревью 10 и 3**, пометить в `TodayCopy`.
- Решение для Агента 10: при `HIDE_FOOD_TALK` компактная строка тарелки на «Сегодня» не показывается, план открывается только из «Опоры › питание».

## Известные проблемы и риски
- Конфликты возможны в `TodayScreen.kt` (`todayView`), `DayViewModel.kt`, `Supports.kt` (их меняют S2 и S9) и в `HotApp.kt` (С1). Правки держать точечными.
- Сдвиг ужина меняет план питания у всех. Нужно ревью Агента 3, хотя правило NUT-01 уже принято.
- Сборка и CI на этой ветке не запускались.
