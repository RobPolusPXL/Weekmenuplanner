// Lezen en schrijven van huishouden, gerechten, dagen, boodschappen en voorraad.
// De documentvormen zijn exact die van MaaltijdRepository in de Android-app en voldoen aan firestore.rules.
import { DEFAULT_DISH_TYPES, dishSnapshot, ingredientMap } from '../logic/planner.js';
import { reconcile as reconcileStock, hasChanges, pantryKey } from '../logic/pantry.js';
import { round3 } from '../logic/units.js';

const INVITE_CHARS = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';

// ---------- Omzetten van documenten (zoals fromDocument in de Android-app) ----------

const str = v => (typeof v === 'string' && v.trim() ? v : null);
const num = v => (typeof v === 'number' && Number.isFinite(v) ? v : null);

function readIngredient(m) {
  const raw = typeof m?.raw === 'string' ? m.raw : '';
  return {
    raw,
    name: typeof m?.name === 'string' ? m.name : raw.trim().toLowerCase(),
    quantity: num(m?.quantity),
    unit: str(m?.unit),
    needsNormalization: m?.needsNormalization === true,
  };
}

function readSnapshot(m) {
  if (!m || typeof m !== 'object') return null;
  return {
    name: typeof m.name === 'string' ? m.name : '',
    type: typeof m.type === 'string' ? m.type : '',
    ingredients: Array.isArray(m.ingredients) ? m.ingredients.filter(x => x && typeof x === 'object').map(readIngredient) : [],
    recipeUrl: str(m.recipeUrl), note: str(m.note), photoUrl: str(m.photoUrl),
    servings: posInt(m.servings), portions: posInt(m.portions),
  };
}

function posInt(v) {
  return typeof v === 'number' && Number.isFinite(v) && v >= 1 ? Math.trunc(v) : null;
}

const strList = v => (Array.isArray(v) ? v.filter(s => typeof s === 'string' && s.trim()) : []);

export function readHousehold({ id, data }) {
  const types = Array.isArray(data.types) ? data.types.filter(t => typeof t === 'string') : [];
  const wsd = num(data.weekStartDay);
  return {
    id,
    members: Array.isArray(data.members) ? data.members.filter(m => typeof m === 'string') : [],
    inviteCode: typeof data.inviteCode === 'string' ? data.inviteCode : id,
    types: types.length ? types : [...DEFAULT_DISH_TYPES],
    weekStartDay: wsd != null && wsd >= 1 && wsd <= 7 ? Math.trunc(wsd) : 1,
    createdBy: data.createdBy ?? '',
  };
}

export function readDish({ id, data }) {
  if (typeof data.name !== 'string') return null;
  const ingredients = Array.isArray(data.ingredients) ? data.ingredients.filter(x => x && typeof x === 'object').map(readIngredient) : [];
  const prep = num(data.prepMinutes);
  return {
    id,
    name: data.name,
    type: typeof data.type === 'string' ? data.type : 'pasta',
    ingredients,
    recipeUrl: str(data.recipeUrl), note: str(data.note), photoUrl: str(data.photoUrl),
    needsNormalization: typeof data.needsNormalization === 'boolean' ? data.needsNormalization : ingredients.some(i => i.needsNormalization),
    pendingPhotoUpload: data.pendingPhotoUpload === true,
    rating: Math.min(5, Math.max(0, Math.trunc(num(data.rating) ?? 0))),
    prepMinutes: prep != null && prep > 0 ? Math.trunc(prep) : null,
    steps: strList(data.steps),
    servings: posInt(data.servings),
    createdBy: data.createdBy ?? '',
  };
}

export function readDay({ id, data }) {
  return {
    dateId: id,
    kind: typeof data.kind === 'string' ? data.kind : 'leeg',
    dishId: str(data.dishId),
    dishSnapshot: readSnapshot(data.dishSnapshot),
    note: str(data.note),
    updatedBy: data.updatedBy ?? '',
  };
}

export function readWeek({ id, data }) {
  const checked = {};
  for (const [k, v] of Object.entries(data.checked || {})) checked[k] = v === true;
  return { weekStart: id, checked };
}

export function readLooseItem({ id, data }) {
  if (typeof data.name !== 'string') return null;
  return {
    id, name: data.name, quantity: num(data.quantity), unit: str(data.unit),
    recurring: data.recurring === true, checked: data.checked === true,
  };
}

export function readPantryItem({ id, data }) {
  const name = str(data.name);
  if (!name) return null;
  return { id, name, unit: str(data.unit), quantity: num(data.quantity), minQuantity: num(data.minQuantity) };
}

function readReservationItems(raw) {
  const out = {};
  if (raw && typeof raw === 'object') {
    for (const [k, v] of Object.entries(raw)) if (typeof v === 'number') out[k] = v;
  }
  return out;
}

// ---------- Repository ----------

export function createRepo(db) {
  const requireUid = () => {
    const uid = db.uid();
    if (!uid) throw new Error('Je moet ingelogd zijn met Google.');
    return uid;
  };
  const H = hid => `households/${hid}`;
  const ts = () => db.ts();

  return {
    // --- Abonnementen ---
    watchHousehold(uid, cb, err) {
      return db.watchHouseholdOf(uid, docs => cb(docs.map(readHousehold)[0] ?? null), err);
    },
    watchDishes(hid, cb, err) {
      return db.watchCollection(`${H(hid)}/dishes`, docs =>
        cb(docs.map(readDish).filter(Boolean).sort((a, b) => a.name.toLowerCase().localeCompare(b.name.toLowerCase(), 'nl'))), err);
    },
    watchDays(hid, cb, err) {
      return db.watchCollection(`${H(hid)}/days`, docs => cb(Object.fromEntries(docs.map(readDay).map(d => [d.dateId, d]))), err);
    },
    watchWeeks(hid, cb, err) {
      return db.watchCollection(`${H(hid)}/weeks`, docs => cb(Object.fromEntries(docs.map(readWeek).map(w => [w.weekStart, w]))), err);
    },
    watchLooseItems(hid, cb, err) {
      return db.watchCollection(`${H(hid)}/looseItems`, docs =>
        cb(docs.map(readLooseItem).filter(Boolean).sort((a, b) => (a.checked - b.checked) || a.name.toLowerCase().localeCompare(b.name.toLowerCase(), 'nl'))), err);
    },
    watchPantry(hid, cb, err) {
      return db.watchCollection(`${H(hid)}/pantry`, docs =>
        cb(docs.map(readPantryItem).filter(Boolean).sort((a, b) => a.name.localeCompare(b.name, 'nl'))), err);
    },
    watchReservations(hid, cb, err) {
      return db.watchCollection(`${H(hid)}/stockReservations`, docs =>
        cb(Object.fromEntries(docs.map(d => [d.id, readReservationItems(d.data.items)]))), err);
    },

    // --- Huishouden ---
    async createHousehold() {
      const uid = requireUid();
      let code = '';
      for (let i = 0; i < 6; i++) code += INVITE_CHARS[Math.floor(Math.random() * INVITE_CHARS.length)];
      await db.set(H(code), {
        members: [uid], inviteCode: code, types: [...DEFAULT_DISH_TYPES],
        createdBy: uid, createdAt: ts(), updatedAt: ts(),
      });
      return code;
    },
    async joinHousehold(inviteCode) {
      const uid = requireUid();
      const code = String(inviteCode).trim().toUpperCase();
      if (code.length !== 6) throw new Error('De uitnodigingscode moet exact 6 tekens bevatten.');
      await db.update(H(code), { members: db.arrayUnion(uid), updatedAt: ts() });
      return code;
    },
    async setWeekStartDay(hid, day) {
      requireUid();
      await db.update(H(hid), { weekStartDay: day, updatedAt: ts() });
    },
    async updateTypes(hid, newTypes, renamedPair = null) {
      requireUid();
      const cleaned = [...new Set(newTypes.map(t => t.trim().toLowerCase()).filter(Boolean))];
      if (!cleaned.length) throw new Error('Er moet minstens één gerechttype zijn.');
      const ops = [{ type: 'update', path: H(hid), data: { types: cleaned, updatedAt: ts() } }];
      if (renamedPair) {
        const [oldType, newType] = renamedPair;
        for (const d of await db.getDocs(`${H(hid)}/dishes`, 'type', oldType)) {
          ops.push({ type: 'update', path: d.path, data: { type: newType, updatedAt: ts() } });
        }
      }
      await db.batch(ops);
    },

    // --- Gerechten ---
    async saveDish(hid, existingId, f) {
      const uid = requireUid();
      const id = existingId || db.newId(`${H(hid)}/dishes`);
      const name = f.name.trim();
      const type = f.type.trim().toLowerCase();
      const recipeUrl = f.recipeUrl?.trim() || null;
      const note = f.note?.trim() || null;
      const photoUrl = f.photoUrl?.trim() || null;
      const prep = Number.isInteger(f.prepMinutes) && f.prepMinutes >= 1 && f.prepMinutes <= 1440 ? f.prepMinutes : null;
      const ingredients = f.ingredients.map(ingredientMap);
      const steps = (f.steps ?? []).map(s => s.trim()).filter(Boolean).slice(0, 60).map(s => s.slice(0, 1000));
      const servings = Number.isInteger(f.servings) && f.servings >= 1 && f.servings <= 20 ? f.servings : null;
      const needsNormalization = ingredients.some(i => i.needsNormalization);
      const path = `${H(hid)}/dishes/${id}`;
      if (!existingId) {
        const payload = {
          name, type, ingredients, needsNormalization, pendingPhotoUpload: false,
          createdBy: uid, createdAt: ts(), updatedAt: ts(),
        };
        if (recipeUrl) payload.recipeUrl = recipeUrl;
        if (note) payload.note = note;
        if (photoUrl) payload.photoUrl = photoUrl;
        if (prep != null) payload.prepMinutes = prep;
        if (steps.length) payload.steps = steps;
        if (servings != null) payload.servings = servings;
        await db.set(path, payload);
      } else {
        await db.update(path, {
          name, type, ingredients, recipeUrl, note, photoUrl, prepMinutes: prep, steps, servings,
          needsNormalization, pendingPhotoUpload: false, updatedAt: ts(),
        });
      }
      return { id, name, type, ingredients, recipeUrl, note, photoUrl, prepMinutes: prep, steps, servings, rating: f.rating ?? 0 };
    },
    /** Na het bewerken: ook de dagen die dit gerecht gepland hebben krijgen de nieuwe snapshot. */
    async updateLinkedDaySnapshots(hid, dish, days) {
      const uid = requireUid();
      if (!days.length) return;
      await db.batch(days.map(day => ({
        type: 'update', path: `${H(hid)}/days/${day.dateId}`,
        data: { dishSnapshot: dishSnapshot(dish, day.dishSnapshot?.portions ?? null), updatedBy: uid, updatedAt: ts() },
      })));
    },
    /** Aantal personen voor één geplande dag; de boodschappenlijst rekent mee. */
    async setDayPortions(hid, day, dish, portions) {
      const uid = requireUid();
      const base = dish ?? { ...day.dishSnapshot };
      await db.update(`${H(hid)}/days/${day.dateId}`, {
        dishSnapshot: dishSnapshot(base, portions), updatedBy: uid, updatedAt: ts(),
      });
    },
    async setDishRating(hid, dishId, rating) {
      requireUid();
      await db.update(`${H(hid)}/dishes/${dishId}`, { rating: Math.min(5, Math.max(0, Math.trunc(rating))), updatedAt: ts() });
    },
    async deleteDish(hid, dishId) {
      requireUid();
      await db.del(`${H(hid)}/dishes/${dishId}`);
    },

    // --- Dagen ---
    async setDayCooking(hid, dateId, dish, note = null) {
      const uid = requireUid();
      const payload = { kind: 'koken', dishId: dish.id, dishSnapshot: dishSnapshot(dish), updatedBy: uid, updatedAt: ts() };
      if (note?.trim()) payload.note = note.trim();
      await db.set(`${H(hid)}/days/${dateId}`, payload);
    },
    async setDayMarker(hid, dateId, kind, note) {
      const uid = requireUid();
      const payload = { kind, updatedBy: uid, updatedAt: ts() };
      if (note?.trim()) payload.note = note.trim();
      await db.set(`${H(hid)}/days/${dateId}`, payload);
    },
    async clearDay(hid, dateId) {
      const uid = requireUid();
      await db.set(`${H(hid)}/days/${dateId}`, { kind: 'leeg', updatedBy: uid, updatedAt: ts() });
    },
    async swapDays(hid, idA, planA, idB, planB) {
      const uid = requireUid();
      const payload = plan => {
        const p = { kind: plan?.kind ?? 'leeg', updatedBy: uid, updatedAt: ts() };
        if (plan?.dishId) p.dishId = plan.dishId;
        if (plan?.dishSnapshot) {
          const snap = Object.fromEntries(Object.entries(plan.dishSnapshot).filter(([, v]) => v != null && v !== ''));
          snap.ingredients = plan.dishSnapshot.ingredients.map(ingredientMap);
          p.dishSnapshot = snap;
        }
        if (plan?.note) p.note = plan.note;
        return p;
      };
      await db.batch([
        { type: 'set', path: `${H(hid)}/days/${idA}`, data: payload(planB) },
        { type: 'set', path: `${H(hid)}/days/${idB}`, data: payload(planA) },
      ]);
    },
    async setGeneratedWeek(hid, assignments) {
      const uid = requireUid();
      const ops = Object.entries(assignments).map(([dateId, dish]) => ({
        type: 'set', path: `${H(hid)}/days/${dateId}`,
        data: { kind: 'koken', dishId: dish.id, dishSnapshot: dishSnapshot(dish), updatedBy: uid, updatedAt: ts() },
      }));
      if (ops.length) await db.batch(ops);
    },

    // --- Boodschappen ---
    async setGroceryChecked(hid, weekStartIso, itemKey, checked, existing) {
      const uid = requireUid();
      const map = { ...existing };
      if (checked) map[itemKey] = true; else delete map[itemKey];
      await db.set(`${H(hid)}/weeks/${weekStartIso}`, { checked: map, updatedBy: uid, updatedAt: ts() });
    },
    async addLooseItem(hid, name, quantity, unit, recurring) {
      const uid = requireUid();
      const payload = { name: name.trim(), recurring: Boolean(recurring), checked: false, createdBy: uid, createdAt: ts(), updatedAt: ts() };
      if (quantity != null) payload.quantity = quantity;
      if (unit?.trim()) payload.unit = unit.trim();
      await db.set(`${H(hid)}/looseItems/${db.newId(`${H(hid)}/looseItems`)}`, payload);
    },
    async setLooseChecked(hid, id, checked) {
      requireUid();
      await db.update(`${H(hid)}/looseItems/${id}`, { checked, updatedAt: ts() });
    },
    async deleteLooseItem(hid, id) {
      requireUid();
      await db.del(`${H(hid)}/looseItems/${id}`);
    },
    /** Winkelen afronden: eenmalige afgevinkte items weg, vaste items terug onafgevinkt. */
    async finishShopping(hid, looseItems) {
      requireUid();
      const ops = looseItems.filter(i => i.checked).map(i => (i.recurring
        ? { type: 'update', path: `${H(hid)}/looseItems/${i.id}`, data: { checked: false, updatedAt: ts() } }
        : { type: 'delete', path: `${H(hid)}/looseItems/${i.id}` }));
      if (ops.length) await db.batch(ops);
    },

    // --- Voorraad ---
    async addOrRefillPantry(hid, existing, { name, unit, quantity }, minQuantity) {
      const uid = requireUid();
      if (existing) {
        const upd = { updatedAt: ts() };
        if (quantity != null) upd.quantity = round3((existing.quantity ?? 0) + quantity);
        if (minQuantity != null) upd.minQuantity = minQuantity;
        await db.update(`${H(hid)}/pantry/${existing.id}`, upd);
      } else {
        const payload = { name, createdBy: uid, createdAt: ts(), updatedAt: ts() };
        if (unit) payload.unit = unit;
        if (quantity != null) payload.quantity = round3(quantity);
        if (minQuantity != null) payload.minQuantity = minQuantity;
        await db.set(`${H(hid)}/pantry/${db.newId(`${H(hid)}/pantry`)}`, payload);
      }
    },
    async updatePantry(hid, id, quantity, minQuantity) {
      requireUid();
      await db.update(`${H(hid)}/pantry/${id}`, {
        quantity: quantity == null ? null : round3(quantity), minQuantity, updatedAt: ts(),
      });
    },
    async deletePantry(hid, id) {
      requireUid();
      await db.del(`${H(hid)}/pantry/${id}`);
    },
    /**
     * Verdeelt de voorraad over de geplande maaltijden van de actieve weken, in een transactie op
     * verse data, zodat twee toestellen tegelijk niets dubbel aftrekken.
     */
    async reconcileStock(hid, trackedIds, needsPerWeek, weeks) {
      const uid = requireUid();
      await db.runTransaction(async tx => {
        const pantrySnaps = await Promise.all(trackedIds.map(id => tx.get(`${H(hid)}/pantry/${id}`)));
        const resSnaps = Object.fromEntries(await Promise.all(weeks.map(async w => [w, await tx.get(`${H(hid)}/stockReservations/${w}`)])));

        const idByKey = {};
        const free = {};
        for (const s of pantrySnaps) {
          if (!s.exists) continue;
          const item = readPantryItem({ id: s.id, data: s.data });
          if (!item || item.quantity == null) continue;
          const key = pantryKey(item);
          if (key in idByKey) continue;
          idByKey[key] = item.id;
          free[key] = item.quantity;
        }
        const current = Object.fromEntries(weeks.map(w => [w, resSnaps[w].exists ? readReservationItems(resSnaps[w].data.items) : {}]));
        const result = reconcileStock(free, current, needsPerWeek, weeks);
        if (!hasChanges(free, current, weeks, result)) return false;

        for (const [key, q] of Object.entries(result.freeStock)) {
          if (Math.abs((free[key] ?? 0) - q) > 0.0001) {
            tx.update(`${H(hid)}/pantry/${idByKey[key]}`, { quantity: q, updatedAt: ts() });
          }
        }
        for (const w of weeks) {
          const items = result.reservations[w] ?? {};
          if (!Object.keys(items).length) {
            if (resSnaps[w].exists) tx.delete(`${H(hid)}/stockReservations/${w}`);
          } else {
            tx.set(`${H(hid)}/stockReservations/${w}`, { items, updatedBy: uid, updatedAt: ts() });
          }
        }
        return true;
      });
    },
  };
}
