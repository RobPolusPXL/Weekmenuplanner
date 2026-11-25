// Firestore + Google-login via de Firebase web-SDK (CDN, geen buildstap).
// Gebruikt dezelfde benoemde database als de Android-app, zodat beide apps dezelfde data delen.
import { FIREBASE_CONFIG, FIRESTORE_DATABASE_ID } from '../firebase-config.js';

const SDK = 'https://www.gstatic.com/firebasejs/11.10.0/';

export const isConfigured = () => Boolean(FIREBASE_CONFIG?.apiKey && FIREBASE_CONFIG?.projectId);

const AUTH_ERRORS = {
  'auth/unauthorized-domain': 'Dit adres staat niet bij de toegestane domeinen in Firebase (Authentication → Settings → Authorized domains).',
  'auth/network-request-failed': 'Geen verbinding met Firebase. Controleer je internet en probeer opnieuw.',
  'auth/internal-error': 'Inloggen mislukte door een fout bij Google. Probeer het opnieuw.',
  'auth/operation-not-allowed': 'Inloggen met Google staat niet aan in Firebase (Authentication → Sign-in method).',
};

export async function createFirebaseDb() {
  const [appMod, authMod, fs] = await Promise.all([
    import(SDK + 'firebase-app.js'),
    import(SDK + 'firebase-auth.js'),
    import(SDK + 'firebase-firestore.js'),
  ]);
  const app = appMod.initializeApp(FIREBASE_CONFIG);
  const auth = authMod.getAuth(app);
  let db;
  try {
    db = fs.initializeFirestore(app, {
      localCache: fs.persistentLocalCache({ tabManager: fs.persistentMultipleTabManager() }),
    }, FIRESTORE_DATABASE_ID);
  } catch {
    db = fs.getFirestore(app, FIRESTORE_DATABASE_ID);
  }

  const docRef = path => fs.doc(db, path);
  const colRef = path => fs.collection(db, path);
  const read = snap => snap.data({ serverTimestamps: 'estimate' });
  const SENTINEL_TS = { __ts: true };

  // Onze eigen markeringen omzetten naar Firestore-waarden.
  function encode(value) {
    if (value === SENTINEL_TS || value?.__ts) return fs.serverTimestamp();
    if (value && value.__arrayUnion) return fs.arrayUnion(...value.__arrayUnion);
    if (value && value.__delete) return fs.deleteField();
    if (Array.isArray(value)) return value.map(encode);
    if (value && typeof value === 'object' && !(value instanceof Date)) {
      return Object.fromEntries(Object.entries(value).map(([k, v]) => [k, encode(v)]));
    }
    return value;
  }

  let currentUser = null;

  return {
    kind: 'firebase',
    ts: () => SENTINEL_TS,
    arrayUnion: (...values) => ({ __arrayUnion: values }),
    deleteField: () => ({ __delete: true }),
    newId: path => fs.doc(colRef(path)).id,
    uid: () => currentUser?.uid ?? null,

    onUser(cb) {
      return authMod.onAuthStateChanged(auth, user => {
        currentUser = user;
        cb(user ? { uid: user.uid, name: user.displayName || user.email, email: user.email } : null);
      });
    },
    async signIn() {
      const provider = new authMod.GoogleAuthProvider();
      try {
        await authMod.signInWithPopup(auth, provider);
      } catch (err) {
        if (['auth/popup-blocked', 'auth/operation-not-supported-in-this-environment'].includes(err.code)) {
          await authMod.signInWithRedirect(auth, provider);
        } else if (!['auth/popup-closed-by-user', 'auth/cancelled-popup-request'].includes(err.code)) {
          throw new Error(AUTH_ERRORS[err.code] || err.message);
        }
      }
    },
    signOut: () => authMod.signOut(auth),

    watchHouseholdOf(uid, cb, onError) {
      const q = fs.query(colRef('households'), fs.where('members', 'array-contains', uid));
      return fs.onSnapshot(q, snap => cb(snap.docs.map(d => ({ id: d.id, data: read(d) }))), onError);
    },
    watchCollection(path, cb, onError) {
      return fs.onSnapshot(colRef(path), snap => cb(snap.docs.map(d => ({ id: d.id, data: read(d) }))), onError);
    },

    set: (path, data) => fs.setDoc(docRef(path), encode(data)),
    update: (path, data) => fs.updateDoc(docRef(path), encode(data)),
    del: path => fs.deleteDoc(docRef(path)),
    async getDocs(path, field, value) {
      const snap = await fs.getDocs(fs.query(colRef(path), fs.where(field, '==', value)));
      return snap.docs.map(d => ({ id: d.id, path: `${path}/${d.id}` }));
    },
    async batch(ops) {
      const b = fs.writeBatch(db);
      for (const op of ops) {
        if (op.type === 'set') b.set(docRef(op.path), encode(op.data));
        else if (op.type === 'update') b.update(docRef(op.path), encode(op.data));
        else if (op.type === 'delete') b.delete(docRef(op.path));
      }
      await b.commit();
    },
    runTransaction(fn) {
      return fs.runTransaction(db, async tx => {
        const api = {
          async get(path) {
            const s = await tx.get(docRef(path));
            return { exists: s.exists(), id: s.id, data: s.exists() ? read(s) : null };
          },
          set: (path, data) => tx.set(docRef(path), encode(data)),
          update: (path, data) => tx.update(docRef(path), encode(data)),
          delete: path => tx.delete(docRef(path)),
        };
        return fn(api);
      });
    },
  };
}
