// Demo-modus: dezelfde interface als db-firebase.js, maar alles in het geheugen van de browser.
// Open de app met ?demo om ze zonder Firebase en zonder login uit te proberen.
import { seedDemo } from './demo-seed.js';

export function createMemoryDb() {
  const docs = new Map(); // pad → data
  const listeners = new Set(); // { match(path), fire() }
  let user = null;
  const userListeners = new Set();
  let idCounter = 0;

  const parentOf = path => path.split('/').slice(0, -1).join('/');
  const clone = v => (v == null ? v : structuredClone(v));

  function resolve(value, previous) {
    if (value?.__ts) return new Date();
    if (value?.__arrayUnion) {
      const base = Array.isArray(previous) ? [...previous] : [];
      for (const v of value.__arrayUnion) if (!base.includes(v)) base.push(v);
      return base;
    }
    if (Array.isArray(value)) return value.map(v => resolve(v));
    if (value && typeof value === 'object' && !(value instanceof Date)) {
      return Object.fromEntries(Object.entries(value).map(([k, v]) => [k, resolve(v)]));
    }
    return value;
  }

  let scheduled = false;
  function notify() {
    if (scheduled) return;
    scheduled = true;
    queueMicrotask(() => { scheduled = false; listeners.forEach(l => l.fire()); });
  }

  function write(path, data) { docs.set(path, resolve(data)); }
  function patch(path, data) {
    if (!docs.has(path)) throw new Error(`Document bestaat niet: ${path}`);
    const prev = docs.get(path);
    const next = { ...prev };
    for (const [k, v] of Object.entries(data)) {
      if (v?.__delete) delete next[k]; else next[k] = resolve(v, prev[k]);
    }
    docs.set(path, next);
  }

  const listCollection = path => [...docs.entries()]
    .filter(([p]) => parentOf(p) === path)
    .map(([p, data]) => ({ id: p.split('/').pop(), data: clone(data) }));

  const api = {
    kind: 'memory',
    ts: () => ({ __ts: true }),
    arrayUnion: (...values) => ({ __arrayUnion: values }),
    deleteField: () => ({ __delete: true }),
    newId: () => `demo${Date.now().toString(36)}${(idCounter++).toString(36)}`,
    uid: () => user?.uid ?? null,

    onUser(cb) {
      userListeners.add(cb);
      queueMicrotask(() => cb(user));
      return () => userListeners.delete(cb);
    },
    async signIn() {
      user = { uid: 'demo-rob', name: 'Rob (demo)', email: 'demo@voorbeeld.be' };
      userListeners.forEach(cb => cb(user));
    },
    async signOut() {
      user = null;
      userListeners.forEach(cb => cb(null));
    },

    watchHouseholdOf(uid, cb) {
      const l = { fire: () => cb(listCollection('households').filter(d => d.data.members?.includes(uid))) };
      listeners.add(l); queueMicrotask(l.fire);
      return () => listeners.delete(l);
    },
    watchCollection(path, cb) {
      const l = { fire: () => cb(listCollection(path)) };
      listeners.add(l); queueMicrotask(l.fire);
      return () => listeners.delete(l);
    },

    async set(path, data) { write(path, data); notify(); },
    async update(path, data) { patch(path, data); notify(); },
    async del(path) { docs.delete(path); notify(); },
    async getDocs(path, field, value) {
      return listCollection(path).filter(d => d.data[field] === value).map(d => ({ id: d.id, path: `${path}/${d.id}` }));
    },
    async batch(ops) {
      for (const op of ops) {
        if (op.type === 'set') write(op.path, op.data);
        else if (op.type === 'update') patch(op.path, op.data);
        else docs.delete(op.path);
      }
      notify();
    },
    async runTransaction(fn) {
      const result = await fn({
        async get(path) {
          return { exists: docs.has(path), id: path.split('/').pop(), data: clone(docs.get(path)) ?? null };
        },
        set: (path, data) => write(path, data),
        update: (path, data) => patch(path, data),
        delete: path => docs.delete(path),
      });
      notify();
      return result;
    },
  };

  seedDemo(write);
  user = { uid: 'demo-rob', name: 'Rob (demo)', email: 'demo@voorbeeld.be' };
  return api;
}
