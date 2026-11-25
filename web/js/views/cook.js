// Kookmodus: stap voor stap, met timers, alarm en een scherm dat aan blijft.
import { esc, icon, openSheet, closeSheet, refreshSheets } from '../ui.js';
import * as S from '../store.js';
import { state } from '../store.js';
import { stepTimers, timerLabel, formatTimer, ingredientsForStep, portionFactor, scaledAmount, DEFAULT_SERVINGS } from '../logic/cooking.js';
import { capitalize } from '../logic/units.js';

// ---------- Timers (blijven lopen als je van stap wisselt of het paneel sluit) ----------

const timers = []; // { id, label, end, done, session, step, paused, left }
const STEP_SECONDS = 30;

/** Resterende seconden; een gepauzeerde timer onthoudt wat er over was. */
const remaining = t => (t.paused ? t.left : Math.max(0, (t.end - Date.now()) / 1000));
let tickHandle = null;
let audio = null;
let alarmHandle = null;

function unlockAudio() {
  try {
    audio ||= new (window.AudioContext || window.webkitAudioContext)();
    if (audio.state === 'suspended') audio.resume();
  } catch { audio = null; }
}

function beep() {
  if (!audio) return;
  const t = audio.currentTime;
  for (let i = 0; i < 3; i++) {
    const o = audio.createOscillator();
    const g = audio.createGain();
    o.frequency.value = 880;
    g.gain.setValueAtTime(0.0001, t + i * 0.28);
    g.gain.exponentialRampToValueAtTime(0.35, t + i * 0.28 + 0.02);
    g.gain.exponentialRampToValueAtTime(0.0001, t + i * 0.28 + 0.22);
    o.connect(g).connect(audio.destination);
    o.start(t + i * 0.28);
    o.stop(t + i * 0.28 + 0.24);
  }
}

function ringing() { return timers.some(t => t.done && !t.acknowledged); }

function startAlarm() {
  if (alarmHandle) return;
  const ring = () => { beep(); try { navigator.vibrate?.([300, 150, 300]); } catch { /* niet ondersteund */ } };
  ring();
  alarmHandle = setInterval(ring, 2200);
}
function stopAlarm() {
  clearInterval(alarmHandle);
  alarmHandle = null;
  timers.forEach(t => { if (t.done) t.acknowledged = true; });
}

function tick() {
  const now = Date.now();
  for (const t of timers) {
    if (!t.done && !t.paused && now >= t.end) {
      t.done = true;
      S.toast(`Timer klaar: ${t.label}`);
    }
  }
  if (ringing()) startAlarm(); else if (alarmHandle) { clearInterval(alarmHandle); alarmHandle = null; }
  if (!timers.some(t => !t.done && !t.paused) && !ringing()) { clearInterval(tickHandle); tickHandle = null; }
  refreshSheets();
}

function startTimer(label, seconds, session, step) {
  unlockAudio();
  timers.push({ id: Math.random().toString(36).slice(2), label, end: Date.now() + seconds * 1000, done: false, session, step });
  tickHandle ||= setInterval(tick, 1000);
  refreshSheets();
}

/** +30 s, -30 s, pauzeren en verder laten lopen. */
function adjustTimer(id, action) {
  const t = timers.find(x => x.id === id);
  if (!t || t.done) return;
  if (action === 'timer-pause') { t.left = remaining(t); t.paused = true; }
  else if (action === 'timer-resume') { t.end = Date.now() + t.left * 1000; t.paused = false; tickHandle ||= setInterval(tick, 1000); }
  else {
    const delta = action === 'timer-plus' ? STEP_SECONDS : -STEP_SECONDS;
    if (delta < 0 && remaining(t) <= STEP_SECONDS) return;
    if (t.paused) t.left += delta; else t.end += delta * 1000;
  }
}

function timersHtml(session) {
  if (!timers.length) return '';
  return `<div class="timers">${timers.map(t => {
    const left = remaining(t);
    const id = `data-id="${t.id}"`;
    const controls = t.done ? '' : `<div class="timer-ctrl">
        <button class="timer-step" data-action="timer-minus" ${id} aria-label="30 seconden minder" ${left <= STEP_SECONDS ? 'disabled' : ''}>−30"</button>
        <button class="icon-btn small" data-action="${t.paused ? 'timer-resume' : 'timer-pause'}" ${id} aria-label="${t.paused ? 'Verder' : 'Pauze'}">${icon(t.paused ? 'play' : 'pause')}</button>
        <button class="timer-step" data-action="timer-plus" ${id} aria-label="30 seconden meer">+30"</button>
      </div>`;
    return `<div class="timer${t.done ? ' done' : ''}${t.paused ? ' paused' : ''}">
      ${icon(t.done ? 'bell' : 'timer')}${t.session === session
        ? `<button class="timer-label timer-goto" data-action="timer-goto" data-step="${t.step}">${esc(t.label)}</button>`
        : `<span class="timer-label">${esc(t.label)}</span>`}
      <span class="timer-left">${t.done ? 'Klaar' : esc(formatTimer(left))}</span>
      <button class="icon-btn small" data-action="timer-stop" data-id="${t.id}" aria-label="Timer weg">${icon('close')}</button>
      ${controls}
    </div>`;
  }).join('')}${ringing() ? `<button class="btn danger full" data-action="alarm-off">${icon('bell')}Alarm stoppen</button>` : ''}</div>`;
}

// ---------- Scherm aan houden ----------

let wakeLock = null;
async function keepAwake() {
  try { if ('wakeLock' in navigator && !wakeLock) wakeLock = await navigator.wakeLock.request('screen'); } catch { wakeLock = null; }
}
function releaseAwake() {
  try { wakeLock?.release(); } catch { /* al vrijgegeven */ }
  wakeLock = null;
}
document.addEventListener('visibilitychange', () => {
  if (document.visibilityState === 'visible' && cookOpen) { wakeLock = null; keepAwake(); }
});
let cookOpen = false;

// ---------- Paneel ----------

/** Opent de kookmodus voor een gerecht, optioneel gekoppeld aan een geplande dag (voor het aantal personen). */
export function openCookMode({ dishId = null, dateId = null }) {
  let step = 0; // 0 = overzicht ingrediënten, 1..n = stappen
  let returnTo = null; // stap waar je was voor je via een timer naar een andere stap keek
  let localPortions = null;
  const session = dateId ? `day:${dateId}` : `dish:${dishId}`;

  const current = () => {
    const day = dateId ? state.days[dateId] : null;
    const lib = state.dishes.find(d => d.id === (dishId ?? day?.dishId)) ?? null;
    const snap = day?.dishSnapshot ?? null;
    const servings = lib?.servings ?? snap?.servings ?? DEFAULT_SERVINGS;
    const portions = dateId ? (snap?.portions ?? servings) : (localPortions ?? servings);
    return {
      name: lib?.name ?? snap?.name ?? 'Gerecht',
      ingredients: lib?.ingredients ?? snap?.ingredients ?? [],
      steps: lib?.steps ?? [],
      servings, portions,
      factor: portionFactor(portions, servings),
      hasLib: Boolean(lib),
    };
  };

  cookOpen = true;
  keepAwake();
  openSheet({
    title: () => current().name,
    full: true,
    onClose: () => { cookOpen = false; releaseAwake(); },
    render: () => {
      const c = current();
      const n = c.steps.length;
      if (step > n) step = n;
      const portionsRow = `<div class="portions"><span>Voor</span>
        <button class="icon-btn small" data-action="portions" data-delta="-1" aria-label="Minder personen" ${c.portions <= 1 ? 'disabled' : ''}>${icon('minus')}</button>
        <strong>${c.portions}</strong>
        <button class="icon-btn small" data-action="portions" data-delta="1" aria-label="Meer personen" ${c.portions >= 12 ? 'disabled' : ''}>${icon('plus')}</button>
        <span>${c.portions === 1 ? 'persoon' : 'personen'}</span></div>`;
      const ingList = list => `<ul class="cook-ing">${list.map(i => {
        const amount = scaledAmount(i, c.factor);
        return `<li><span>${esc(capitalize(i.name || i.raw))}</span>${amount ? `<strong>${esc(amount)}</strong>` : ''}</li>`;
      }).join('')}</ul>`;

      let body;
      if (step === 0) {
        body = `<div class="cook-step">
          <div class="cook-kicker">Klaarzetten</div>
          ${portionsRow}
          ${c.ingredients.length ? ingList(c.ingredients) : '<p class="muted">Geen ingrediënten.</p>'}
          ${n ? '' : `<p class="note-box">Dit gerecht heeft nog geen bereiding. ${c.hasLib ? 'Voeg stappen toe via Gerecht bewerken.' : ''}</p>`}
        </div>`;
      } else {
        const text = c.steps[step - 1];
        const used = ingredientsForStep(text, c.ingredients);
        const found = stepTimers(text);
        body = `<div class="cook-step">
          <div class="cook-kicker">Stap ${step} van ${n}</div>
          <p class="cook-text">${esc(text)}</p>
          ${found.length ? `<div class="chips">${found.map(t =>
            `<button class="btn timer-btn" data-action="timer" data-seconds="${t.seconds}" data-label="${esc(timerLabel(text, t.index, c.ingredients, step))}">${icon('timer')}Timer ${esc(t.label)}</button>`).join('')}</div>` : ''}
          ${used.length ? `<div class="cook-used"><h4 class="section-label">Nodig in deze stap</h4><div class="chips">${used.map(i => {
            const amount = scaledAmount(i, c.factor);
            return `<span class="ing-chip">${esc(capitalize(i.name || i.raw))}${amount ? ` · ${esc(amount)}` : ''}</span>`;
          }).join('')}</div></div>` : ''}
        </div>`;
      }

      // Voortgang bovenaan: één streepje per stap (het eerste = klaarzetten), tikbaar.
      const bar = !n ? '' : `<div class="cook-bar" style="grid-template-columns: repeat(${n + 1}, minmax(0, 1fr))">${Array.from({ length: n + 1 }, (_, i) =>
        `<button class="seg${i <= step ? ' on' : ''}" data-action="goto" data-step="${i}" aria-label="${i ? `Naar stap ${i}` : 'Naar klaarzetten'}"${i === step ? ' aria-current="step"' : ''}></button>`).join('')}</div>`;

      const back = returnTo != null && returnTo !== step
        ? `<button class="btn full back-to-step" data-action="return">${icon('left')}${returnTo ? `Terug naar stap ${returnTo}` : 'Terug naar klaarzetten'}</button>` : '';
      return `${bar}${timersHtml(session)}${back}${body}
        <div class="cook-nav">
          <button class="btn" data-action="prev" ${step === 0 ? 'disabled' : ''}>Vorige</button>
          ${step < n ? `<button class="btn primary grow" data-action="next">${step === 0 ? 'Beginnen' : 'Volgende stap'}</button>`
            : `<button class="btn primary grow" data-action="done">${icon('check')}Klaar</button>`}
        </div>`;
    },
    onAction: (a, el) => {
      unlockAudio();
      const c = current();
      if (a === 'next') { step = Math.min(step + 1, c.steps.length); returnTo = null; }
      else if (a === 'prev') { step = Math.max(0, step - 1); returnTo = null; }
      else if (a === 'goto') { step = Number(el.dataset.step); returnTo = null; }
      else if (a === 'timer-goto') {
        const target = Math.min(Number(el.dataset.step), c.steps.length);
        if (target !== step) { if (returnTo == null) returnTo = step; step = target; }
        if (step === returnTo) returnTo = null;
      }
      else if (a === 'return') { step = returnTo ?? step; returnTo = null; }
      else if (a === 'done') { closeSheet(); return; }
      else if (a === 'timer') startTimer(el.dataset.label, Number(el.dataset.seconds), session, step);
      else if (['timer-plus', 'timer-minus', 'timer-pause', 'timer-resume'].includes(a)) adjustTimer(el.dataset.id, a);
      else if (a === 'timer-stop') {
        const i = timers.findIndex(t => t.id === el.dataset.id);
        if (i >= 0) timers.splice(i, 1);
        if (!ringing()) stopAlarm();
      } else if (a === 'alarm-off') { stopAlarm(); for (let i = timers.length - 1; i >= 0; i--) if (timers[i].done) timers.splice(i, 1); }
      else if (a === 'portions') {
        const next = Math.min(12, Math.max(1, c.portions + Number(el.dataset.delta)));
        if (dateId) S.setPortions(dateId, next); else localPortions = next;
      }
      refreshSheets();
    },
  });
}
