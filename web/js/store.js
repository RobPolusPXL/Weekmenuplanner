// Toestand van de app en alle acties (zoals de ViewModel in de Android-app).
import { createRepo } from './data/repo.js';
import { today, weekStart, weekDates, addDays, WEEK_START_OPTIONS } from './logic/dates.js';
import { aggregateGroceryList, pickDishForDay, generateWholeWeek, kindOf } from './logic/planner.js';
import { splitGroceryList, isLow, isTracked, pantryKey, daysByWeek, activeWeeks, weekNeeds, reconcile, hasChanges, parsePantryInput } from './logic/pantry.js';
import { parseAmount, capitalize } from './logic/units.js';

let db = null;
let repo = null;
const listeners = new Set();
let unsubs = [];
let reconcileTimer = null;

export const state = {
  ready: false,
  user: null,
  household: null,
  householdLoaded: false,
  dishes: [],
  days: {},
  weeks: {},
  looseItems: [],
  pantry: [],
  pantryLoaded: false,
  reservations: {},
  selectedWeekStart: weekStart(today(), 1),
  currentWeekStart: weekStart(today(), 1),
  online: navigator.onLine,
  toast: null,
  error: null,
  demo: false,
};

export const onChange = fn => { listeners.add(fn); return () => listeners.delete(fn); };
function emit() { listeners.forEach(fn => fn(state)); }
function patch(p) { Object.assign(state, p); emit(); }

let toastTimer = null;
export function toast(message) {
  clearTimeout(toastTimer);
  patch({ toast: message });
  toastTimer = setTimeout(() => patch({ toast: null }), 4000);
}
const fail = (e, fallback) => {
  console.warn(e);
  toast(e?.code === 'permission-denied'
    ? 'Geen toegang. Zijn de Firestore-regels gepubliceerd en ben je lid van dit huishouden?'
    : (e?.message || fallback));
};

// ---------- Afgeleide gegevens ----------

export const weekStartDay = () => state.household?.weekStartDay ?? 1;
export const dishTypes = () => state.household?.types ?? [];
export const selectedWeekDates = () => weekDates(state.selectedWeekStart);
export const isPastWeek = () => state.selectedWeekStart < state.currentWeekStart;
export const isCurrentWeek = () => state.selectedWeekStart === state.currentWeekStart;

export function selectedWeekPlans() {
  const out = {};
  for (const id of selectedWeekDates()) if (state.days[id]) out[id] = state.days[id];
  return out;
}

export function groceryList() {
  const items = aggregateGroceryList(Object.values(selectedWeekPlans()), state.weeks[state.selectedWeekStart]?.checked ?? {});
  return splitGroceryList(items, state.reservations[state.selectedWeekStart] ?? {}, state.pantry);
}

export const cookingDaysInWeek = () => Object.values(selectedWeekPlans()).filter(d => kindOf(d) === 'koken').length;
export const lowStock = () => state.pantry.filter(isLow);

export function reservedByKey() {
  const totals = {};
  for (const [w, items] of Object.entries(state.reservations)) {
    if (w < state.currentWeekStart) continue;
    for (const [k, v] of Object.entries(items)) totals[k] = (totals[k] ?? 0) + v;
  }
  return totals;
}

export function uncheckedGroceryCount() {
  const { toBuy } = groceryList();
  return toBuy.filter(i => !i.checked).length + state.looseItems.filter(i => !i.checked).length;
}

// ---------- Opstarten ----------

export async function start(database, { demo = false } = {}) {
  db = database;
  repo = createRepo(db);
  state.demo = demo;
  window.addEventListener('online', () => { patch({ online: true }); scheduleReconcile(); });
  window.addEventListener('offline', () => patch({ online: false }));
  db.onUser(user => {
    stopHousehold();
    unsubs.forEach(u => u()); unsubs = [];
    patch({ user, ready: true, household: null, householdLoaded: false });
    if (user) {
      unsubs.push(repo.watchHousehold(user.uid, household => {
        const prevId = state.household?.id;
        const prevDay = state.household?.weekStartDay;
        patch({ household, householdLoaded: true, error: null });
        if (household && household.weekStartDay !== prevDay) applyWeekStartDay(household.weekStartDay);
        if (household?.id !== prevId) {
          stopHousehold();
          if (household) startHousehold(household.id);
        }
      }, e => { patch({ householdLoaded: true, error: e.message }); }));
    }
  });
}

let householdUnsubs = [];
function stopHousehold() {
  householdUnsubs.forEach(u => u());
  householdUnsubs = [];
}
function startHousehold(hid) {
  const err = what => e => fail(e, `Kon ${what} niet laden.`);
  householdUnsubs = [
    repo.watchDishes(hid, dishes => patch({ dishes }), err('gerechten')),
    repo.watchDays(hid, days => { patch({ days }); scheduleReconcile(); }, err('weekplanning')),
    repo.watchWeeks(hid, weeks => patch({ weeks }), err('boodschappenlijst')),
    repo.watchLooseItems(hid, looseItems => patch({ looseItems }), err('losse items')),
    repo.watchPantry(hid, pantry => { patch({ pantry, pantryLoaded: true }); scheduleReconcile(); },
      e => fail(e, 'Voorraad kon niet laden. Zijn de nieuwe Firestore-regels gepubliceerd?')),
    repo.watchReservations(hid, reservations => { patch({ reservations }); scheduleReconcile(); }, () => {}),
  ];
}

const hid = () => state.household?.id;
const run = async (fn, fallback) => {
  if (!hid()) { toast('Er is nog geen huishouden geladen.'); return false; }
  try { await fn(hid()); return true; } catch (e) { fail(e, fallback); return false; }
};

// ---------- Login & huishouden ----------

export async function signIn() {
  try { await db.signIn(); } catch (e) { toast(e.message); }
}
export const signOut = () => db.signOut();

export async function createHousehold() {
  try { await repo.createHousehold(); toast('Huishouden aangemaakt.'); } catch (e) { fail(e, 'Kon huishouden niet aanmaken.'); }
}
export async function joinHousehold(code) {
  try { await repo.joinHousehold(code); toast('Je bent lid van het huishouden.'); } catch (e) {
    fail(e.code === 'not-found' ? new Error('Geen huishouden gevonden met die code.') : e, 'Kon niet deelnemen met deze code.');
  }
}
export function setWeekStartDay(day) {
  if (day === weekStartDay()) return;
  run(async h => {
    await repo.setWeekStartDay(h, day);
    const name = WEEK_START_OPTIONS.find(o => o[0] === day)?.[1] ?? '';
    toast(`De week begint nu op ${name.toLowerCase()}.`);
  }, 'Kon de eerste dag van de week niet opslaan.');
}

// ---------- Weeknavigatie ----------

function applyWeekStartDay(day) {
  const wasCurrent = state.selectedWeekStart === state.currentWeekStart;
  const current = weekStart(today(), day);
  const selected = wasCurrent ? current : weekStart(addDays(state.selectedWeekStart, 3), day);
  patch({ currentWeekStart: current, selectedWeekStart: selected });
  scheduleReconcile();
}
export const previousWeek = () => patch({ selectedWeekStart: addDays(state.selectedWeekStart, -7) });
export const nextWeek = () => patch({ selectedWeekStart: addDays(state.selectedWeekStart, 7) });
export function currentWeek() {
  const c = weekStart(today(), weekStartDay());
  patch({ currentWeekStart: c, selectedWeekStart: c });
}

// ---------- Dagen plannen ----------

export function pickForDay(dateId) {
  if (!state.dishes.length) { toast('Voeg eerst gerechten toe.'); return false; }
  const ws = weekStart(dateId, weekStartDay());
  const plans = {};
  for (const id of weekDates(ws)) if (state.days[id]) plans[id] = state.days[id];
  const res = pickDishForDay(dateId, plans, state.dishes);
  run(async h => {
    await repo.setDayCooking(h, dateId, res.dish);
    toast(res.ruleIgnored ? `'${res.dish.name}' gekozen (geen vrij type meer deze week).` : `'${res.dish.name}' gekozen.`);
  }, 'Kon geen gerecht kiezen.');
  return true;
}

export function generateWeek() {
  if (!state.dishes.length) { toast('Voeg eerst gerechten toe.'); return false; }
  const res = generateWholeWeek(selectedWeekDates(), selectedWeekPlans(), state.dishes);
  const n = Object.keys(res.assignments).length;
  if (!n) { toast('Alle dagen in deze week zijn al ingevuld.'); return true; }
  run(async h => {
    await repo.setGeneratedWeek(h, res.assignments);
    toast(res.anyRuleIgnored ? 'Week gegenereerd (geen vrij type meer op sommige dagen).' : `${n} lege dag(en) ingevuld.`);
  }, 'Kon week niet genereren.');
  return true;
}

export const assignDish = (dateId, dish) => run(h => repo.setDayCooking(h, dateId, dish), 'Fout bij plannen van gerecht.');
export const markDay = (dateId, kind, note) => run(h => repo.setDayMarker(h, dateId, kind, note), 'Fout bij markeren van dag.');
export const clearDay = dateId => run(h => repo.clearDay(h, dateId), 'Fout bij leegmaken van dag.');
export function swapDays(a, b) {
  if (a === b) return;
  run(h => repo.swapDays(h, a, state.days[a], b, state.days[b]), 'Fout bij wisselen van dagen.');
}
export function copyToCurrentWeek(sourceDay, targetDateId) {
  const snap = sourceDay.dishSnapshot;
  if (!snap) return;
  const lib = state.dishes.find(d => d.id === sourceDay.dishId);
  const dish = lib ?? { id: sourceDay.dishId ?? `copied_${Date.now()}`, ...snap };
  run(async h => { await repo.setDayCooking(h, targetDateId, dish); toast(`'${dish.name}' gekopieerd naar de huidige week.`); }, 'Kopiëren mislukt.');
}

// ---------- Gerechten ----------

/** f = { name, type, ingredients, recipeUrl, note, photoUrl, prepMinutes } */
export async function saveDish(existingId, f, assignToDateId = null) {
  if (!f.name.trim() || !f.type.trim()) { toast('Naam en type zijn verplicht.'); return false; }
  return run(async h => {
    const type = f.type.trim().toLowerCase();
    if (!dishTypes().map(t => t.toLowerCase()).includes(type)) await repo.updateTypes(h, [...dishTypes(), type]);
    const saved = await repo.saveDish(h, existingId, f);
    if (assignToDateId) {
      await repo.setDayCooking(h, assignToDateId, saved);
    } else if (existingId) {
      const linked = Object.values(state.days).filter(d => kindOf(d) === 'koken' && d.dishId === saved.id);
      await repo.updateLinkedDaySnapshots(h, saved, linked);
    }
    toast(`Gerecht '${saved.name}' opgeslagen.`);
  }, 'Fout bij opslaan van gerecht.');
}
export function setPortions(dateId, portions) {
  const day = state.days[dateId];
  if (!day?.dishSnapshot) return;
  const p = Math.min(12, Math.max(1, Math.round(portions)));
  const dish = state.dishes.find(d => d.id === day.dishId) ?? null;
  return run(h => repo.setDayPortions(h, day, dish, p), 'Kon aantal personen niet opslaan.');
}
export const rateDish = (dish, rating) => run(h => repo.setDishRating(h, dish.id, rating), 'Kon waardering niet opslaan.');
export const deleteDish = dish => run(async h => {
  await repo.deleteDish(h, dish.id);
  toast(`'${dish.name}' verwijderd (bestaande planningen blijven behouden).`);
}, 'Kon gerecht niet verwijderen.');

export function addType(newType) {
  const clean = newType.trim().toLowerCase();
  if (!clean) return;
  if (dishTypes().includes(clean)) { toast(`Type '${clean}' bestaat al.`); return; }
  run(async h => { await repo.updateTypes(h, [...dishTypes(), clean]); toast(`Type '${clean}' toegevoegd.`); }, 'Kon type niet toevoegen.');
}
export function renameType(oldType, newType) {
  const clean = newType.trim().toLowerCase();
  if (!clean || clean === oldType.toLowerCase()) return;
  const list = [...new Set(dishTypes().map(t => (t.toLowerCase() === oldType.toLowerCase() ? clean : t)))];
  run(async h => { await repo.updateTypes(h, list, [oldType, clean]); toast(`Type '${oldType}' hernoemd naar '${clean}'.`); }, 'Kon type niet hernoemen.');
}
export function deleteType(type) {
  const used = state.dishes.filter(d => d.type.toLowerCase() === type.toLowerCase()).length;
  if (used) { toast(`Kan '${type}' niet verwijderen: nog in gebruik bij ${used} gerecht(en).`); return; }
  if (dishTypes().length <= 1) { toast('Er moet minstens één gerechttype overblijven.'); return; }
  run(async h => { await repo.updateTypes(h, dishTypes().filter(t => t.toLowerCase() !== type.toLowerCase())); toast(`Type '${type}' verwijderd.`); }, 'Kon type niet verwijderen.');
}

// ---------- Boodschappen ----------

export const toggleGrocery = (itemKey, checked) => run(
  h => repo.setGroceryChecked(h, state.selectedWeekStart, itemKey, checked, state.weeks[state.selectedWeekStart]?.checked ?? {}),
  'Kon afvinkstatus niet bijwerken.');
export function addLooseItem(name, quantityText, unit, recurring) {
  if (!name.trim()) return;
  run(h => repo.addLooseItem(h, name, parseAmount(quantityText), unit, recurring), 'Kon item niet toevoegen.');
}
export const toggleLoose = (id, checked) => run(h => repo.setLooseChecked(h, id, checked), 'Kon item niet afvinken.');
export const deleteLoose = id => run(h => repo.deleteLooseItem(h, id), 'Kon item niet verwijderen.');
export function finishShopping() {
  const checked = state.looseItems.filter(i => i.checked);
  if (!checked.length) { toast('Geen afgevinkte losse items om af te ronden.'); return; }
  run(async h => {
    await repo.finishShopping(h, state.looseItems);
    const removed = checked.filter(i => !i.recurring).length;
    toast(`Winkelen afgerond (${removed} eenmalig verwijderd, ${checked.length - removed} vast teruggezet).`);
  }, 'Kon winkelen niet afronden.');
}

// ---------- Voorraad ----------

export function addPantry(text, minText) {
  const parsed = parsePantryInput(text);
  if (!parsed) { toast('Geef een product op, bv. "1 kg rijst" of "zout".'); return; }
  const min = minText.trim() ? parseAmount(minText) : null;
  const existing = state.pantry.find(p => p.name === parsed.name && (p.unit ?? null) === (parsed.unit ?? null));
  run(async h => {
    await repo.addOrRefillPantry(h, existing, parsed, min);
    toast(existing ? `${capitalize(parsed.name)} aangevuld.` : `${capitalize(parsed.name)} toegevoegd aan voorraad.`);
  }, 'Kon voorraad niet bijwerken.');
}
/** Aanvullen (mag negatief) of exact instellen, plus minimumvoorraad. */
export function updatePantry(item, addText, exactText, minText) {
  const exact = exactText.trim() ? parseAmount(exactText) : null;
  const delta = Number(addText.trim().replace(',', '.'));
  let q = item.quantity;
  if (exact != null) q = exact;
  else if (addText.trim() && Number.isFinite(delta)) q = Math.max(0, (item.quantity ?? 0) + delta);
  const min = minText.trim() ? parseAmount(minText) : null;
  run(h => repo.updatePantry(h, item.id, q, min), 'Kon voorraad niet bijwerken.');
}
export const deletePantry = item => run(h => repo.deletePantry(h, item.id), 'Kon voorraaditem niet verwijderen.');
export function lowStockToList(item) {
  const label = capitalize(item.name);
  if (state.looseItems.some(i => i.name.toLowerCase() === item.name.toLowerCase() && !i.checked)) {
    toast(`${label} staat al op de boodschappenlijst.`); return;
  }
  run(async h => { await repo.addLooseItem(h, label, null, null, false); toast(`${label} op de boodschappenlijst gezet.`); }, 'Kon item niet toevoegen.');
}

/** Herverdeelt de voorraad over geplande maaltijden; schrijft enkel als er iets verandert. */
function scheduleReconcile() {
  clearTimeout(reconcileTimer);
  reconcileTimer = setTimeout(async () => {
    const h = hid();
    if (!h || !state.pantryLoaded || !state.online) return;
    const tracked = state.pantry.filter(isTracked);
    const free = {};
    for (const p of tracked) if (!(pantryKey(p) in free)) free[pantryKey(p)] = p.quantity;
    const byWeek = daysByWeek(state.days, weekStartDay());
    const weeks = activeWeeks(state.currentWeekStart, Object.keys(byWeek), Object.keys(state.reservations));
    if (!weeks.length) return;
    const needs = Object.fromEntries(weeks.map(w => [w, weekNeeds(byWeek[w] ?? [])]));
    const result = reconcile(free, state.reservations, needs, weeks);
    if (!hasChanges(free, state.reservations, weeks, result)) return;
    try { await repo.reconcileStock(h, tracked.map(p => p.id), needs, weeks); } catch (e) { console.warn('Voorraad herverdelen mislukt', e); }
  }, 800);
}
