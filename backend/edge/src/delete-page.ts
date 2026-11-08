/**
 * The web page of account deletion (A65 §6, Google Play «Account deletion»): a link that works
 * without the app. Static: no cookies, no analytics, no form fields, nothing is asked except a
 * Google sign-in, which proves who deletes (the step-up of A65 §6). The page sends only the Google
 * ID token to `POST /account/delete`; the Worker reads `sub` from it and nothing else.
 *
 * WHOOP on the web needs an https redirect registered at WHOOP (Agent 9): until then a WHOOP-only
 * account is deleted in the app, and the page says so.
 *
 * Texts: черновик Агента 5, ждёт ревью Агентов 4, 7 и 10. Neutral form (A61 §4.1).
 */

export const DELETE_PAGE_SCRIPT = "/account/delete.js";

/** The Web client id is not a secret, but only a plain id may go into the page. */
const CLIENT_ID_RE = /^[A-Za-z0-9.-]{1,200}$/;

const CSP = [
  "default-src 'none'",
  `script-src 'self' https://accounts.google.com/gsi/client`,
  "connect-src 'self' https://accounts.google.com/gsi/",
  "frame-src https://accounts.google.com/gsi/",
  "style-src 'unsafe-inline' https://accounts.google.com/gsi/style",
  "img-src 'self' data:",
  "base-uri 'none'",
  "form-action 'none'",
  "frame-ancestors 'none'",
].join("; ");

const HEADERS = {
  "Cache-Control": "no-store",
  "X-Content-Type-Options": "nosniff",
  "Referrer-Policy": "no-referrer",
  "Content-Security-Policy": CSP,
};

export const DELETE_TEXTS = {
  title: "Удаление аккаунта HoT",
  lead: "Здесь можно удалить аккаунт Health of a Titan без приложения.",
  whatTitle: "Что удаляется",
  what: [
    "Аккаунт HoT, привязанные входы Google и WHOOP, все сессии на всех устройствах.",
    "Токены WHOOP: доступ HoT к WHOOP отзывается.",
    "Резервные копии на сервере HoT, если они были.",
  ],
  phone:
    "Данные о здоровье, профиль, фото и «О себе» хранятся только на телефоне. Телефон сотрёт их сам при следующем запуске HoT. Если приложение уже удалено — на телефоне ничего не осталось.",
  keep: "Сервер хранит только запись «аккаунт удалён» без идентификаторов. Технические копии у провайдера исчезают в течение 30 дней.",
  appTitle: "В приложении",
  app: "Аватар › Настройки › Аккаунт › Удалить аккаунт.",
  webTitle: "Здесь, через Google",
  confirm: "Понимаю: удаление нельзя отменить.",
  googleOff: "Вход через Google на этой странице пока не настроен. Удалить аккаунт можно в приложении.",
  whoopOnly: "Если вход в HoT был только через WHOOP — удалить аккаунт пока можно только в приложении.",
  googleHint: "Google не передавал HoT никаких токенов, отзывать там нечего. Убрать HoT из списка сторонних приложений: myaccount.google.com/connections.",
} as const;

function escapeHtml(text: string): string {
  return text.replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]!);
}

export function deletePage(googleClientId: string | undefined): Response {
  const t = DELETE_TEXTS;
  const clientId = googleClientId && CLIENT_ID_RE.test(googleClientId) ? googleClientId : "";
  const web = clientId
    ? `<label class="confirm"><input type="checkbox" id="hot-confirm"> ${escapeHtml(t.confirm)}</label>
    <div id="hot-google" hidden></div>
    <p id="hot-status" role="status" aria-live="polite"></p>
    <script src="https://accounts.google.com/gsi/client" async defer></script>
    <script src="${DELETE_PAGE_SCRIPT}" defer></script>`
    : `<p>${escapeHtml(t.googleOff)}</p>`;
  const html = `<!doctype html>
<html lang="ru">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex">
<title>${escapeHtml(t.title)}</title>
<style>
  :root { color-scheme: dark; }
  body { margin: 0; background: #0b0d12; color: #e8e6e1; font: 16px/1.5 system-ui, sans-serif; }
  main { max-width: 640px; margin: 0 auto; padding: 32px 16px 48px; }
  h1 { font-size: 26px; margin: 0 0 12px; }
  h2 { font-size: 18px; margin: 28px 0 8px; }
  ul { padding-left: 20px; }
  .muted { color: #a9a7a1; font-size: 14px; }
  .confirm { display: flex; gap: 10px; align-items: flex-start; margin: 12px 0 16px; }
  #hot-status { min-height: 24px; }
</style>
</head>
<body>
<main id="hot-delete" data-client-id="${escapeHtml(clientId)}">
  <h1>${escapeHtml(t.title)}</h1>
  <p>${escapeHtml(t.lead)}</p>
  <h2>${escapeHtml(t.whatTitle)}</h2>
  <ul>${t.what.map((line) => `<li>${escapeHtml(line)}</li>`).join("")}</ul>
  <p>${escapeHtml(t.phone)}</p>
  <p class="muted">${escapeHtml(t.keep)}</p>
  <h2>${escapeHtml(t.appTitle)}</h2>
  <p>${escapeHtml(t.app)}</p>
  <h2>${escapeHtml(t.webTitle)}</h2>
  ${web}
  <p class="muted">${escapeHtml(t.whoopOnly)}</p>
  <p class="muted">${escapeHtml(t.googleHint)}</p>
</main>
</body>
</html>`;
  return new Response(html, { status: 200, headers: { "Content-Type": "text/html; charset=utf-8", ...HEADERS } });
}

/**
 * The page script: a fresh server nonce, the Google button (Identity Services) after the
 * confirmation, then `POST /account/delete {id_token}`. Nothing is stored in the browser.
 */
const SCRIPT = `"use strict";
(function () {
  var root = document.getElementById("hot-delete");
  var clientId = root && root.dataset.clientId;
  var box = document.getElementById("hot-confirm");
  var slot = document.getElementById("hot-google");
  var status = document.getElementById("hot-status");
  if (!clientId || !box || !slot || !status) return;
  var say = function (text) { status.textContent = text; };
  var ready = false;
  function start() {
    if (ready || !window.google || !box.checked) return;
    ready = true;
    fetch("/auth/nonce", { method: "POST" }).then(function (r) {
      if (!r.ok) throw new Error("nonce");
      return r.json();
    }).then(function (n) {
      window.google.accounts.id.initialize({ client_id: clientId, nonce: n.nonce, callback: onCredential, auto_select: false });
      slot.hidden = false;
      window.google.accounts.id.renderButton(slot, { theme: "filled_black", size: "large", text: "continue_with" });
    }).catch(function () { ready = false; say(${JSON.stringify("Нет связи с сервером HoT. Попробуй ещё раз.")}); });
  }
  function onCredential(response) {
    if (!box.checked) { say(${JSON.stringify("Сначала отметь подтверждение.")}); return; }
    say(${JSON.stringify("Удаляю…")});
    fetch("/account/delete", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ id_token: response.credential })
    }).then(function (r) {
      if (r.ok) { slot.hidden = true; say(${JSON.stringify("Аккаунт удалён. На телефоне данные сотрутся при следующем запуске HoT.")}); }
      else if (r.status === 404) { say(${JSON.stringify("Аккаунта HoT с этим Google нет.")}); }
      else { say(${JSON.stringify("Не получилось. Обнови страницу и попробуй ещё раз.")}); }
    }).catch(function () { say(${JSON.stringify("Нет связи с сервером HoT. Попробуй ещё раз.")}); });
  }
  box.addEventListener("change", start);
  window.onGoogleLibraryLoad = start;
  start();
})();
`;

export function deletePageScript(): Response {
  return new Response(SCRIPT, { status: 200, headers: { "Content-Type": "text/javascript; charset=utf-8", ...HEADERS } });
}
