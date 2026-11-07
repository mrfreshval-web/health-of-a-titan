# tools/lottie — Lottie Титана

Генератор Lottie-файлов (Агент 17, D-037; бриф `docs/design/training/animator-brief.md`). Он строит
топ-20 упражнений и 5 героических моментов из ключевых поз `content/training/poses.yaml`, то есть из
тех же поз, что и Compose-риг (`TitanPose.kt`).

## Сборка

```sh
cd tools/lottie
python3 -m hotlottie.build                 # все файлы
python3 -m hotlottie.build ex-pullup       # один файл
python3 -m hotlottie.keyposes              # дописать/исправить ключевые позы в poses.yaml
python3 ../../content/training/scripts/check_training.py   # проверить позы
```

Результат пишется в `android/feature/training/src/main/assets/lottie/<id>.json`, а список файлов с
маркерами — в `manifest.json`. Нужен только Python 3 и PyYAML.

## Модули

| Модуль | Что делает |
|---|---|
| `rig.py` | Прямая кинематика 12 суставов, как в `TitanPose.kt`; IK на две кости |
| `shapes.py` | Анатомические контуры: плечо, предплечье, бедро, голень, таз, грудь, голова, набедренная повязка |
| `motion.py` | Сценарий движения: переходы между позами, easing (anticipation/overshoot), опоры (IK), пружины для follow-through, дыхание на удержаниях, маркеры |
| `figure.py` | Сборка фигуры: кости как слои с родителями, линия света — градиентный штрих, knockout, внутренние линии |
| `lottie.py` | Подгонка ключей кривыми Эрмита (несколько ключей на секунды движения), JSON-строители |
| `props.py` | Пол, стена, косяк, турник, гиря, палка, резинка, ремень |
| `exercises.py` | Сценарии 20 упражнений |
| `heroes.py` | 5 героических моментов: колонны из `HotMarkGeometry.kt`, кольцо, вспышка |
| `keyposes.py`, `posetool.py` | Новые и исправленные ключевые позы (приземление на пол, IK для опор) |

## Контракт с приложением

Имена групп — по брифу: `titan_line` (градиент из 3 стопов), `titan_knockout`, `titan_inner`,
`props_line`, `floor`, а в героях — `columns_line`, `columns_flutes`, `columns_knockout`, `ring`,
`ring_next`. Плеер (`TitanLottie.kt`) перекрашивает их под режим и тему через dynamic properties.

Маркеры: `poster` — статичный ключевой кадр для «Удалить анимацию». У удержаний есть `enter`,
`hold_loop_start`, `hold_loop_end` и `exit`: вход играется один раз, затем дыхание крутится в петле.
Повторы растягиваются на цикл темпа пользователя.

Ограничения: 60 fps, 1024×1024, ≤150 КБ на файл (сейчас ≤97 КБ), ≤40 слоёв. Без выражений.
Trim paths используются только в героях. Всё это проверяет `TitanLottieTest`.

## Превью

```sh
npm install                          # lottie-web; playwright с Chromium
node render/render.js storyboard out.png ../../android/feature/training/src/main/assets/lottie/ex-pullup.json
node render/render.js frames dir/ <file.json> 20 320 [rovno|razgon|berezhno|light]
node render/render.js sheet _top20.png 5 <files…>
```

Готовые превью лежат в `brand/lottie/` в папке проекта. Там раскадровки по 6 кадров, GIF,
`_top20.png` и `_heroes.png`.
