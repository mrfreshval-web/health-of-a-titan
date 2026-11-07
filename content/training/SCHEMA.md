# content/training — схема

Библиотека тренировок «Здоровья Титана». Устроена так же, как хокку (`content/haiku/`): **библиотека + правила + генерация + валидатор**. Обоснование и продуктовая логика — `docs/artifacts/A40-training-system.md`.

| Файл | Что внутри | Кто правит | Ревью |
|---|---|---|---|
| `exercises.yaml` | упражнения: техника, ошибки, подсказки, дыхание, противопоказания, замены, ключевые позы для анимации Титана | Агент 12 | Агент 3 (противопоказания, безопасность) |
| `templates.yaml` | шаблоны тренировок из блоков; версии по режимам дня | Агент 12 | Агент 3; Агент 4 (тайминги плеера) |
| `rules.yaml` | закрытые словари, уровни и методики, тесты подготовленности, ворота и потолки режимов, коды профиля, модификаторы дня, правило «мобильность везде», прогрессия, разгрузка, настройки плеера, рамки генерации, стоп-слова | Агент 12 | Агент 3 (медицина), Агент 10 (слова о теле и вине) |
| `scripts/check_training.py` | валидатор библиотеки и сессий-кандидатов | Агент 13 | — |
| `_tests/` | пример запроса и два кандидата: проходящий и отклоняемый | Агент 13 | — |

Проверка: `python3 content/training/scripts/check_training.py` (код 0 — без ошибок; предупреждения допустимы). Запускается в CI вместе с `scripts/check_haiku.py`.

## 1. Цепочка слоёв

```
уровень → цель → методика → шаблон → блоки → упражнения → параметры
levels    goal    methods     templates  blocks   exercises    sets/reps/time/rest/rpe/tempo
(rules)   (vocab) (rules)     (templates.yaml)    (exercises.yaml)  (item → default)
```

Каждый слой сужает следующий и ссылается на него только по `id`. Движок идёт сверху вниз; на каждом слое применяет фильтры (режим дня, профиль, место, инвентарь, время). Ни один фильтр не делает тренировку тяжелее.

## 2. Упражнение (`exercises.yaml → exercises[]`)

| Поле | Тип | Правило |
|---|---|---|
| `id` | `ex-<kebab>` | навсегда; не переиспользовать; снять — `retired: true` |
| `name_ru`, `name_en` | строка | `name_ru` уникально |
| `a19` | `E01…E33` | необязательно: связь с таблицей A19 §2.2 |
| `level` | `classic · advanced · extreme` | **нижний** уровень, где упражнение может появиться |
| `category` | словарь `vocab.category` | `mobility`, `stretch`, `breathing` — для правила «мобильность везде» |
| `pattern` | словарь `vocab.pattern` | нужен для замен и запретов по профилю |
| `muscles` | `{primary: [], secondary: []}` | словарь `vocab.muscles`; `primary` обязателен |
| `equipment` | список | **любой из** (достаточно одного); `none` — без инвентаря |
| `place` | `home · gym · outdoor · hotel` | где упражнение делается |
| `measure` | `reps · reps_per_side · time · time_per_side · distance` | определяет обязательное поле в `default` |
| `impact` | `low · medium · high` | `high` требует тега `high_impact` или `max_speed` |
| `risk_tags` | список `rules.risk_tags` | свойства движения; по ним режут профиль и режим дня |
| `default` | `{sets, reps \| time_s \| distance_m, rest_s, tempo, rpe, zone}` | параметры по умолчанию |
| `technique_ru` | 2–5 шагов | на «ты», глагол первым |
| `mistakes_ru`, `cues_ru` | списки | частые ошибки и короткие подсказки для голоса плеера |
| `breathing_ru` | строка | как дышать |
| `contraindications` | коды профиля (`rules.profile_codes`) | `extreme` всегда содержит `screening_not_cleared`; тег `inversion` — `hypertension` и `eye_pressure` |
| `regressions`, `progressions`, `swaps` | `ex-…` | ↓ проще, ↑ сложнее, ⇄ та же задача иначе; регрессия не выше уровнем |
| `animation` | `{view, loop, props, poses[]}` | ≥ 2 ключевых позы `{key, ru}`; `key` — snake_case, по нему художник и аниматор делают кадры `TitanPose` (A40 §6) |

## 3. Шаблон (`templates.yaml → templates[]`)

| Поле | Правило |
|---|---|
| `id` | `tpl-<cl\|ad\|ex\|mob>-<kebab>`; префикс совпадает с уровнем (`mob` = `all`) |
| `version` | растёт при любом изменении; `PlanItem` хранит `id@version` (ADR-05) |
| `level` | `classic · advanced · extreme · all` (`all` — только мобильность, растяжка, дыхание, лёгкое кардио уровня classic) |
| `method` | из `rules.methods`, уровень методики = уровень шаблона |
| `goal`, `place`, `equipment` | словари; упражнения шаблона должны находить хотя бы один свой инвентарь в `equipment` шаблона |
| `duration_min` | сумма `blocks[].min` в пределах ±10 % (не меньше ±3 мин) |
| `intensity` | `{rpe, zone?, time_cap_min?, note_ru}`; RPE не выше потолка уровня |
| `modes` | **все три** режима: `BOOST`, `STEADY`, `GENTLE`; значение `{use: full \| lighter}` или `{swap: tpl-…}`; методика, запрещённая в режиме (`rules.regime_caps`), обязана уйти в `swap` |
| `lighter` | как облегчать: `sets_delta`, `rounds_delta`, `duration_pct`, `volume_pct` (≤ 0), `rpe_max`, `drop_blocks`, `drop_exercises`, `format_change`; **нельзя** выбрасывать `warmup` и `cooldown` |
| `blocks[]` | `{type, min, format, rounds?, minutes?, rest_between_rounds_s?, items[]}` |
| `items[]` | `{ex, sets?, reps \| time_s \| distance_m, per_side?, rest_s?, tempo?, rpe?, zone?, pair?, alt[]?, note_ru?}` |
| `why_ru` | одна фраза «почему так», видна под карточкой |
| `contraindications` | коды профиля; `advanced`/`extreme` — `screening_not_cleared`; `extreme` — ещё `extreme_not_consented` |

**Правило M-ALL «мобильность и растяжка везде»** (проверяет валидатор): первый блок — `warmup` ≥ 3 мин с хотя бы одним упражнением `mobility`; последний — `cooldown` ≥ 3 мин с хотя бы одной растяжкой `stretch`, заканчивается растяжкой или дыханием. Ровно одна разминка и одна заминка.

Форматы блоков: `flow` (по очереди без счёта подходов), `straight_sets`, `superset` (пары по букве `pair`), `circuit` (`rounds`), `emom` (`minutes`), `amrap` (`minutes` или `rounds`), `for_time` (с `time_cap_min` в `intensity`), `intervals`, `steady`, `skill_practice`.

## 4. Сессия-кандидат (генерация Claude, A40 §4.4)

Когда шаблона под время × место × инвентарь × профиль нет, движок просит Claude собрать сессию **только из существующих упражнений и блоков**. Формат — как у шаблона, но:
- `id: gen-<8 hex>`; `modes` и `lighter` не нужны (сессия уже под режим дня);
- к ней прилагается запрос (`_tests/request-knee.yaml`): `level`, `regime`, `place`, `equipment`, `minutes`, `profile[]`.

```
python3 content/training/scripts/check_training.py --candidate _tests/candidate-ok.yaml --request _tests/request-knee.yaml
```

Кандидат отклоняется, если: уровень выше пользовательского; методика или RPE выше потолка режима; упражнение несёт запрещённый профилем или режимом `risk_tag` или паттерн; в его `contraindications` есть код пользователя; не подходит место, инвентарь или время; нарушено M-ALL; в текстах есть стоп-слово. Две неудачи подряд — движок берёт ближайший детерминированный шаблон.

## 5. Как добавить упражнение (чек-лист агента)
1. Новый `id`, `level` — нижний уровень, `category`, `pattern`, `risk_tags` по словарю.
2. Техника 2–5 шагов, 2 ошибки, 2 подсказки, дыхание.
3. Противопоказания — только кодами профиля; если нужен новый код, сначала `rules.profile_codes` и общий профиль `content/profile/constraints.yaml` (A41).
4. Хотя бы одна замена или регрессия, если есть противопоказания.
5. 2–4 ключевые позы с `key` и описанием — по ним делается анимация.
6. Запусти валидатор; отдай на ревью Агенту 3.
