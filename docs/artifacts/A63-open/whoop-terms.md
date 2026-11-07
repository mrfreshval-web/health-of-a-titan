# A62 · Условия WHOOP API: что они значат для HoT и что делать

> **Версия:** v0.1 · **Статус:** черновик на ревью Агентов 7, 9, 2 · **Дата:** 2026-11-07
> **Основание:** D-051 («WHOOP — юридическое ревью, Health Connect равный источник, D-039 на паузе»), D-039, R-11, A05 (L-05), A25 §7, `A58-audit/tech.md` (§1 п. 2, §7, §10), `A58-audit/market.md` (§4 п. 1, §5).
> **Владелец:** Агент 7 · **Участники:** Агент 9 (интеграция, Health Connect), Агент 2 (рынок, отношения с WHOOP).
> **Оговорка:** это разбор ИИ-агентом, не юридическое заключение. До внешних пользователей текст условий смотрит практикующий юрист (право штата Массачусетс).

## 1. Что прочитано

Страницы developer.whoop.com, прочитаны 2026-11-07. **Даты редакции на странице условий нет** — сохраняем копию страницы при каждой проверке и сравниваем.

- [WHOOP API Terms of Use](https://developer.whoop.com/api-terms-of-use/)
- [App Approval](https://developer.whoop.com/docs/developing/app-approval/)
- [API Rate Limiting](https://developer.whoop.com/docs/developing/rate-limiting/)
- [Support](https://developer.whoop.com/docs/developing/support/)
- [Partner API Overview](https://developer.whoop.com/docs/partner/overview/)
- [Design Guidelines](https://developer.whoop.com/docs/developing/design-guidelines/)

## 2. Ключевые пункты (цитаты)

Цитаты короткие, дословно со страницы условий; полный текст — по ссылке.

| § | Цитата | Что это значит для HoT |
|---|---|---|
| 4.2 (2) | «Scrape, build databases, or otherwise create permanent copies of WHOOP Data» — запрещено | Вся история WHOOP в Room (D-039) — прямой конфликт. Это ядро R-11 |
| 4.2 (2) | «keep cached copies longer than permitted by the cache header» — запрещено | Окно 28–35 дней законно, только если заголовки кэша его допускают. **Надо проверить реальные `Cache-Control` ответов API v2** (Агент 9) |
| 4.2 (3) | «Copy, translate, modify, create a derivative work of, sell, lease, lend, convey» — запрещено | Неясно, являются ли наши показатели (ORS, базы, недельные агрегаты) «derivative work». Это главный вопрос письма |
| 4.2 (1) | «Expose WHOOP Data to other users or to third parties without explicit opt-in consent» — запрещено | Передача уровней из данных WHOOP в LLM (ContextPack) — только с явным opt-in (A58-audit/tech §7) |
| 3.1 (3) | «…to any third party even if an End User consents to such use» | Часть запрета на продажу и передачу данных третьим лицам, где согласие пользователя **не спасает**. Граница между «процессором, работающим на нас» (Anthropic, Cloudflare) и «третьей стороной» не прописана — вопрос в письмо |
| 3.1 (13) | «Compete, directly or indirectly, with WHOOP or its products and services (including the WHOOP Platform) in any manner.» | Самый широкий пункт. WHOOP Coach с памятью и недельным планом пересекается с наставником HoT (A58-audit/market §4). Толкует WHOOP |
| 3.1 (5) | «Create any Applications that function substantially the same as the APIs.» | Нас не касается, если мы не перепродаём доступ к данным |
| 1.5 | «limiting the number of API requests that Company may make, or the number of users Company may serve» | WHOOP вправе ограничить число пользователей |
| 1.6 | «WHOOP may suspend access to the APIs by Company or Company Applications without notice» | Доступ может пропасть в любой день. Продукт обязан жить без WHOOP |
| 7.1 | «WHOOP reserves the right to terminate the Terms with Company or discontinue the APIs»; после прекращения — «delete any cached or stored content that was permitted under the Terms» | При отзыве удаляем всё, что получили от WHOOP. Наши собственные показатели — серая зона (вопрос в письмо) |
| 2.1 | «be expressly authorized by each end user of the Application» | OAuth-согласие — уже есть |
| 2.3 | «will provide and adhere to a privacy policy for Company Applications» | Политика должна перечислять LLM и бэкенд (A05: текущее «never shared» станет ложью) |
| 2.5 | «Company must not use WHOOP data in any advertisements without the prior written consent of WHOOP.» | Скриншоты с данными WHOOP в сторе и рекламе — только с письменного согласия |
| 1.12 | «Access to and use of the APIs is currently provided at no charge.» | Сейчас бесплатно; §1.5 позволяет ввести плату за превышение лимитов |
| Право | «governed by and construed in accordance with the laws of the Commonwealth of Massachusetts» | Споры — суды округа Саффолк, Массачусетс |

**Документация разработчика:**
- App Approval: «Apps can be used for development immediately with a limit of 10 WHOOP members.» Для одобрения: соблюдать Design and Brand Guidelines, корректные имя, e-mail и URL политики конфиденциальности в Developer Dashboard, протестировать хотя бы с одним участником WHOOP. Срок рассмотрения не указан. **Следствие:** 5–20 знакомых (этап 2) — это больше 10, значит одобрение нужно до этапа 2.
- Rate limiting: по умолчанию 100 запросов в минуту и 10 000 в день; увеличение — по запросу. Для 20 человек хватает с запасом.
- Partner API: «available exclusively to WHOOP-approved healthcare partners» — это интеграции для лабораторий и клиник. **Нам не подходит**; публичной партнёрской программы для приложений-коучей на сайте нет.
- Support: обратная связь через форму, «responses to this form may take some time». Адрес в условиях — apisupport@whoop.com.

## 3. Где HoT сейчас нарушает или рискует

1. **D-039 (вся история в недельных агрегатах)** — §4.2 (2) и (3). Уже на паузе по D-051; до ответа WHOOP не снимать.
2. **Хранение `MetricDaily` / `BaselineSnapshot` дольше кэша** — серая зона §4.2 (3).
3. **Оркестрация дня в `:integration:whoop`** — техника: при отзыве доступа (§1.6) пропадает весь день, а не один источник (A58-audit/tech §1 п. 2).
4. **ContextPack в Claude API** — §4.2 (1) требует opt-in; §3.1 (3) может трактоваться шире.
5. **Позиционирование «слой поверх WHOOP»** (A58-audit/market §5) — честно, но подсвечивает §3.1 (13). Публично — без данных WHOOP в рекламе (§2.5) и без логотипа вне Brand Guidelines.

## 4. Три стратегических варианта

### Вариант 1. Письменное разъяснение (и, если повезёт, партнёрство)
**Суть:** отправить письмо (§6) сейчас, параллельно подать App Submission. Строить дальше по ответу.
**Риски:**
- ответа может не быть неделями (форма поддержки «may take some time»); партнёрской программы для коучей нет;
- письменный ответ «нет» закрывает серую зону хуже, чем молчание;
- письмо привлекает внимание к §3.1 (13): WHOOP может решить, что мы конкурент, и отказать в одобрении;
- ответ поддержки — не поправка к договору; WHOOP может изменить условия в одностороннем порядке.
**Для MVP:** ничего не ломает. Пока нет ответа — окно 28–35 дней, D-039 на паузе. Этап 1 (только основатель) не зависит от ответа; этап 2 (> 10 человек) зависит от одобрения приложения.

### Вариант 2. Только производные показатели, Health Connect — основной источник
**Суть:** физиология по умолчанию из Health Connect (Pixel Watch и любые часы). WHOOP читается в память на окно кэша, храним только свои показатели в физиологических единицах (A25 P4, D-009), без recovery, strain и прочих полей WHOOP.
**Риски:**
- снимает §4.2 (2), но **не** снимает §4.2 (3): «derivative work» может покрывать и наши показатели;
- HRV и сон Pixel Watch не сравнимы с WHOOP: две базы, повторная калибровка (A58-audit/tech §5);
- основатель живёт на WHOOP — dogfooding ослабнет, если основной источник у него вторичен;
- [проверить, Агент 9] пишет ли приложение WHOOP данные в Health Connect и какие; если да, чтение через HC идёт по правилам Health Connect, но условия членства WHOOP могут их ограничивать.
**Для MVP:** + 3–4 дня на полноценный адаптер HC (A58-audit/tech, «Сигналы без WHOOP»), версии баз по источнику (`source` в `MetricDaily`), вынести оркестрацию дня из `:integration:whoop`. «Магическое утро» на Pixel Watch менее богатое.

### Вариант 3. WHOOP — один необязательный источник с первого дня
**Суть:** домен не знает про WHOOP. День работает на любом из трёх входов: Health Connect, WHOOP, ручной режим (самоотчёт, sRPE). WHOOP — плагин, который можно выключить без потери дня, истории и показателей. Хранение по правилам варианта 2.
**Риски:**
- больше работы сейчас: интерфейсы источников, ручной режим как полноценный путь;
- для владельцев WHOOP опыт не лучше, чем у конкурентов на WHOOP; маркетинговая история «русский слой поверх WHOOP» слабеет;
- история показателей HoT при смене источника начинает новую версию базы — это надо объяснить пользователю.
**Для MVP:** + 3–4 дня (сигналы без WHOOP) + рефакторинг графа модулей (`:core:data`, оркестрация вне `:integration:whoop`), который аудит и так требует (A58-audit/tech §7 п. 7). Отзыв доступа WHOOP превращается из «конца продукта» в «минус один источник». Совпадает с D-051 («Health Connect — равный источник») и с позиционированием «любые часы на Health Connect» (A58-audit/market §4 п. 2).

### Сравнение

| | 1. Разъяснение | 2. Производные + HC | 3. Необязательный источник |
|---|---|---|---|
| Снимает R-11 | только если ответ «да» | частично (§4.2 (2)) | частично + переживает отзыв |
| Риск отзыва доступа (§1.6) | не меняет | снижает | снимает как риск существования |
| Работа до MVP | ~0 | +3–4 дня | +3–4 дня + рефакторинг, который и так нужен |
| Опыт основателя на WHOOP | без изменений | хуже | без изменений, если WHOOP подключён |

## 5. Рекомендация

**Вариант 3 как архитектура + письмо из варианта 1 сейчас + правило хранения из варианта 2 до ответа.**

1. Сегодня: отправить письмо (§6) и подать App Submission — нужно до этапа 2 (> 10 человек).
2. До ответа: окно 28–35 дней, храним только собственные показатели HoT, D-039 на паузе, opt-in на передачу в LLM.
3. В MVP: день и история не зависят от WHOOP; WHOOP, Health Connect и ручной режим — равные входы.
4. Если WHOOP ответит «производные можно хранить» — снимаем паузу с D-039 (импорт истории как источник агрегатов). Если «нельзя» — история строится только с момента подключения и из Health Connect.
5. Публично (A59, D-052/D-053): не говорить «стоит поверх WHOOP» и не показывать данные WHOOP в рекламе без письменного согласия (§2.5).

## 6. Письмо в WHOOP (черновик, отправляет основатель)

Кому: apisupport@whoop.com (или форма на [developer.whoop.com/docs/developing/support](https://developer.whoop.com/docs/developing/support/)).
Без секретов: не указываем client secret, токены, данные пользователей. Client ID можно не указывать — достаточно имени приложения в Dashboard.

```text
Subject: API Terms clarification — on-device storage and derived metrics (Health of a Titan)

Hello WHOOP Developer team,

I am the founder of Health of a Titan, a Russian-language daily routine and
coaching app for Android, registered in the Developer Dashboard as
"[App name in Dashboard]". It reads WHOOP data through the official API with
each member's OAuth consent. Today I am its only user; I plan a closed test
with 5–20 people.

Before we go further I want to make sure we follow the API Terms of Use.
Could you please confirm the following?

1. Storage (Section 4.2). We keep WHOOP Data only on the member's own phone,
   encrypted, for a rolling window of up to 35 days. Is that within the
   "cache header" rule? What cache period applies to the v2 endpoints?

2. Derived metrics (Section 4.2(3)). From that window we compute our own
   metrics in physiological units (for example, a 7-day average of HRV in ms
   or a sleep-timing score) and keep them after the raw data expires.
   We do not store or show Recovery, Strain or other WHOOP scores.
   Is keeping these derived metrics allowed?

3. History import. May we read a member's full history once, compute weekly
   aggregates on the device, and discard the raw data right away?

4. AI processing (Sections 3.1 and 4.2(1)). With explicit opt-in, the app
   sends short derived summaries (no raw WHOOP Data, no identifiers) to an AI
   provider that processes them only on our behalf. Is this allowed, and is
   such a processor a "third party" under the Terms?

5. Disconnect. When a member disconnects WHOOP, we delete all WHOOP Data.
   May the member keep their own derived metrics in the app?

6. Approval and scope. We will submit the app for approval to go above
   10 members. Is there anything in a routine-and-coaching app that WHOOP
   would see as competing under Section 3.1(13)? Is there a partner program
   for apps like ours?

A short written answer would help us a lot. I am happy to share designs or
a demo build.

Thank you,
[Founder name]
Founder, Health of a Titan
[Contact e-mail]
```

## Передача
- **Артефакт:** A62-open/whoop-terms v0.1, черновик на ревью 7, 9, 2.
- **Главное в 3 пунктах:** (1) условия запрещают постоянные копии (§4.2 (2)), производные работы (§4.2 (3)) и конкуренцию «прямо или косвенно» (§3.1 (13)), а доступ можно отозвать без уведомления (§1.6); даты редакции нет; (2) до одобрения приложения — не больше 10 участников WHOOP, значит одобрение нужно до этапа 2; партнёрская программа есть только для медицинских партнёров; (3) рекомендация: вариант 3 (WHOOP — необязательный источник) + письмо сейчас + хранение только собственных показателей до ответа.
- **Принятые допущения:** окно 28–35 дней допустимо, пока заголовки кэша это позволяют; ответ поддержки не меняет договор.
- **Открытые вопросы и кому:** реальные `Cache-Control` API v2 и запись WHOOP в Health Connect — Агенту 9; толкование «derivative work» и «third party» — живому юристу через Агента 7; оценка реакции WHOOP на §3.1 (13) и судьба позиционирования «поверх WHOOP» — Агенту 2.
- **Риски:** право — §3.1 (13) толкует WHOOP; техника — оркестрация дня в `:integration:whoop`; бизнес — потеря «магического утра» у владельцев WHOOP при отзыве доступа.
- **Следующий шаг и кому передаю:** основателю — отправить письмо и подать App Submission; Агентам 5 и 9 — план выноса оркестрации дня и адаптера HC; Агенту 7 — обновить политику конфиденциальности (A05).
- **Что нужно утвердить основателю:** вариант 3 как курс; отправку письма от своего имени; D-039 остаётся на паузе до ответа.
