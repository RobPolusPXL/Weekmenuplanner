// Opstart, tabbladen en tekenen. Elke wijziging in de toestand tekent het actieve scherm opnieuw.
import { esc, icon, renderInto, refreshSheets, closeAllSheets } from './ui.js';
import * as S from './store.js';
import { state } from './store.js';
import { isConfigured, createFirebaseDb } from './data/db-firebase.js';
import { createMemoryDb } from './data/db-memory.js';
import * as week from './views/week.js';
import * as dishes from './views/dishes.js';
import * as grocery from './views/grocery.js';
import * as pantry from './views/pantry.js';
import * as more from './views/more.js';
import { applyAccent } from './theme.js';
import * as account from './views/account.js';

const TABS = [
  { id: 'week', label: 'Week', icon: 'week', view: week },
  { id: 'gerechten', label: 'Gerechten', icon: 'book', view: dishes },
  { id: 'boodschappen', label: 'Boodschappen', icon: 'cart', view: grocery },
  { id: 'voorraad', label: 'Voorraad', icon: 'kitchen', view: pantry },
  { id: 'meer', label: 'Meer', icon: 'more', view: more },
];

const app = document.getElementById('app');
let active = TABS.find(t => `#${t.id}` === location.hash)?.id ?? (location.hash === '#statistieken' ? 'meer' : 'week');
const tab = () => TABS.find(t => t.id === active);

function shell() {
  const badge = id => {
    const n = id === 'boodschappen' ? S.uncheckedGroceryCount() : id === 'voorraad' ? S.lowStock().length : 0;
    return n ? `<span class="badge">${n > 99 ? '99+' : n}</span>` : '';
  };
  const tags = `${state.online ? '' : '<span class="offline">Offline</span>'}${state.demo ? '<span class="demo-tag">Demo</span>' : ''}`;
  return `<nav class="tabbar" aria-label="Hoofdmenu">
      <div class="brand"><img src="icons/icon-192.png" alt="" class="brand-ic"><span class="brand-name">Maaltijdplanner</span></div>${TABS.map(t => `<a href="#${t.id}" class="tab${t.id === active ? ' on' : ''}" data-tab="${t.id}" ${t.id === active ? 'aria-current="page"' : ''}>
      <span class="tab-ic">${icon(t.icon)}${badge(t.id)}</span><span class="tab-label">${t.label}</span></a>`).join('')}</nav>
    <main class="content" id="content">${tags ? `<div class="status-tags">${tags}</div>` : ''}${tab().view.render()}</main>`;
}

function render() {
  if (!state.ready) { app.innerHTML = '<div class="gate"><div class="spinner" aria-label="Laden"></div></div>'; return; }
  if (!state.user) { renderInto(app, account.renderLogin()); return; }
  if (!state.householdLoaded) { app.innerHTML = '<div class="gate"><div class="spinner" aria-label="Laden"></div></div>'; return; }
  if (!state.household) { renderInto(app, account.renderSetup()); return; }
  renderInto(app, shell());
  const t = document.getElementById('toast');
  t.textContent = state.toast ?? '';
  t.classList.toggle('show', Boolean(state.toast));
}

let frame = 0;
function scheduleRender() {
  if (frame) return;
  frame = requestAnimationFrame(() => {
    frame = 0;
    render();
    refreshSheets();
    const t = document.getElementById('toast');
    t.textContent = state.toast ?? '';
    t.classList.toggle('show', Boolean(state.toast));
  });
}

// ---------- Gebeurtenissen (één keer, op de hele app) ----------

app.addEventListener('click', e => {
  const tabEl = e.target.closest('[data-tab]');
  if (tabEl) {
    e.preventDefault();
    if (active !== tabEl.dataset.tab) {
      active = tabEl.dataset.tab;
      history.replaceState(null, '', `#${active}`);
      scheduleRender();
      window.scrollTo({ top: 0 });
    }
    return;
  }
  const el = e.target.closest('[data-action]');
  if (!el || !app.contains(el)) return;
  const a = el.dataset.action;
  if (!state.household) { account.onGateAction(a); return; }
  if (a === 'household') { account.openHouseholdSheet(); return; }
  if (a === 'week-prev') S.previousWeek();
  else if (a === 'week-next') S.nextWeek();
  else if (a === 'week-now') S.currentWeek();
  else tab().view.onAction?.(a, el, e);
  scheduleRender();
});

app.addEventListener('input', e => {
  if (!state.household) { account.onGateInput(e.target); return; }
  tab().view.onInput?.(e.target, scheduleRender);
});

app.addEventListener('change', e => {
  if (state.household) tab().view.onChangeEvent?.(e.target);
});

app.addEventListener('submit', e => {
  e.preventDefault();
  if (!state.household) { account.onGateSubmit(e.target); return; }
  tab().view.onSubmit?.(e.target, scheduleRender);
});

window.addEventListener('hashchange', () => {
  if (location.hash === '#statistieken') location.hash = '#meer';
  const t = TABS.find(x => `#${x.id}` === location.hash);
  if (t && t.id !== active) { active = t.id; closeAllSheets(); scheduleRender(); }
});

dishes.setRerender(scheduleRender);
S.onChange(scheduleRender);

// ---------- Start ----------

applyAccent();

async function boot() {
  const params = new URLSearchParams(location.search);
  const demo = params.has('demo');
  if (!demo && !isConfigured()) {
    app.innerHTML = account.renderNotConfigured();
    return;
  }
  render();
  try {
    const database = demo ? createMemoryDb() : await createFirebaseDb();
    await S.start(database, { demo });
  } catch (e) {
    console.error(e);
    app.innerHTML = `<div class="gate"><div class="gate-card"><h1 class="title">Kan niet starten</h1><p class="sub">${esc(e.message || 'Onbekende fout')}</p><p class="muted small">Controleer je internetverbinding en herlaad de pagina.</p></div></div>`;
  }
}

// Nieuwe versie online? Dan meteen herladen, zodat geen enkel open toestel met oude code op de gedeelde gegevens rekent.
if ('serviceWorker' in navigator && location.protocol === 'https:') {
  const hadController = Boolean(navigator.serviceWorker.controller);
  let reloading = false;
  navigator.serviceWorker.addEventListener('controllerchange', () => {
    if (!hadController || reloading) return;
    reloading = true;
    location.reload();
  });
  navigator.serviceWorker.register('sw.js').then(reg => {
    const check = () => reg.update().catch(() => {});
    document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'visible') check(); });
    window.addEventListener('online', check);
    setInterval(check, 10 * 60 * 1000);
  }).catch(() => {});
}

boot();
