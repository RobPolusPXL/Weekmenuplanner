// Inloggen, huishouden aanmaken of aansluiten, en het huishoudenpaneel.
import { esc, icon, openSheet, closeSheet } from '../ui.js';
import * as S from '../store.js';
import { state } from '../store.js';
import { WEEK_START_OPTIONS } from '../logic/dates.js';

let joinCode = '';

export function renderLogin() {
  return `<div class="gate">
    <div class="gate-card">
      <img class="gate-logo" src="icons/icon-192.png" alt="">
      <h1 class="title">Maaltijdplanner</h1>
      <p class="sub">Gedeelde weekplanner en boodschappenlijst voor Rob en Joke.</p>
      <button class="btn primary full" data-action="sign-in">${googleMark()}Inloggen met Google</button>
    </div></div>`;
}

export function renderSetup() {
  return `<div class="gate">
    <div class="gate-card">
      <h1 class="title">Welkom${state.user?.name ? `, ${esc(state.user.name.split(' ')[0])}` : ''}</h1>
      <p class="sub">Maak een huishouden aan, of sluit aan met de code van je partner.</p>
      <button class="btn primary full" data-action="create">${icon('plus')}Nieuw huishouden</button>
      <div class="divider"><span>of</span></div>
      <form class="input-row" data-form="join">
        <input id="join-code" value="${esc(joinCode)}" maxlength="6" placeholder="Uitnodigingscode" autocapitalize="characters" autocomplete="off">
        <button class="btn" type="submit">Aansluiten</button>
      </form>
      ${state.error ? `<p class="error">${esc(state.error)}</p>` : ''}
      <button class="link-btn" data-action="sign-out">${icon('logout')}Afmelden</button>
    </div></div>`;
}

export function renderNotConfigured() {
  return `<div class="gate">
    <div class="gate-card">
      <h1 class="title">Nog niet gekoppeld</h1>
      <p class="sub">Vul <code>js/firebase-config.js</code> in met de gegevens van de web-app in Firebase. Tot dan kan je de app bekijken met voorbeeldgegevens.</p>
      <a class="btn primary full" href="?demo">Demo bekijken</a>
    </div></div>`;
}

export function onGateAction(action) {
  if (action === 'sign-in') S.signIn();
  else if (action === 'create') S.createHousehold();
  else if (action === 'sign-out') S.signOut();
}
export function onGateInput(el) { if (el.id === 'join-code') joinCode = el.value.toUpperCase(); }
export function onGateSubmit(form) { if (form.dataset.form === 'join') S.joinHousehold(joinCode); }

function googleMark() {
  return '<svg class="ic" viewBox="0 0 24 24" aria-hidden="true"><path fill="#4285F4" d="M21.6 12.2c0-.7-.1-1.4-.2-2H12v3.8h5.4a4.6 4.6 0 0 1-2 3v2.5h3.2c1.9-1.7 3-4.3 3-7.3z"/><path fill="#34A853" d="M12 22c2.7 0 5-.9 6.6-2.5l-3.2-2.5c-.9.6-2 1-3.4 1-2.6 0-4.8-1.8-5.6-4.1H3.1v2.6A10 10 0 0 0 12 22z"/><path fill="#FBBC05" d="M6.4 13.9a6 6 0 0 1 0-3.8V7.5H3.1a10 10 0 0 0 0 9z"/><path fill="#EA4335" d="M12 6c1.5 0 2.8.5 3.8 1.5l2.9-2.9A10 10 0 0 0 3.1 7.5l3.3 2.6C7.2 7.8 9.4 6 12 6z"/></svg>';
}

export function openHouseholdSheet() {
  let copied = false;
  openSheet({
    title: 'Huishouden',
    render: () => {
      const h = state.household;
      if (!h) return '';
      const both = h.members.length >= 2;
      return `<p class="lead">${both
        ? 'Jullie zijn met twee (2/2). Alle wijzigingen aan de planning, gerechten en boodschappen worden live gedeeld.'
        : 'Je bent het eerste lid (1/2). Deel deze code met je partner zodat jullie samen kunnen plannen.'}</p>
      <div class="invite">
        <div><span class="kicker">Uitnodigingscode</span><span class="code">${esc(h.inviteCode)}</span></div>
        <button class="icon-btn" data-action="copy" aria-label="Kopieer code">${icon(copied ? 'check' : 'copy')}</button>
      </div>
      <h4 class="section-label">Week begint op</h4>
      <div class="chips">${WEEK_START_OPTIONS.map(([day, name]) =>
        `<button class="chip-btn${day === h.weekStartDay ? ' on' : ''}" data-action="week-start" data-day="${day}">${esc(name.slice(0, 2))}</button>`).join('')}</div>
      <p class="muted small">Geldt voor jullie allebei.</p>
      <hr>
      <p class="muted small">${state.demo ? 'Demo-modus: wijzigingen blijven alleen in deze pagina en verdwijnen bij herladen.' : `Ingelogd als ${esc(state.user?.email ?? '')}`}</p>
      ${state.demo ? '' : `<button class="btn danger-ghost full" data-action="sign-out">${icon('logout')}Afmelden</button>`}`;
    },
    onAction: async (a, el) => {
      if (a === 'copy') {
        try { await navigator.clipboard.writeText(state.household.inviteCode); copied = true; } catch { S.toast(`Code: ${state.household.inviteCode}`); }
        import('../ui.js').then(m => m.refreshSheets());
      } else if (a === 'week-start') S.setWeekStartDay(Number(el.dataset.day));
      else if (a === 'sign-out') { closeSheet(); S.signOut(); }
    },
  });
}
