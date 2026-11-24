// Gegevens van de web-app in het Firebase-project van de Maaltijdplanner.
// In te vullen via de Firebase console: Project settings → Your apps → Web app (</>) → Config.
// Deze waarden zijn niet geheim: de toegang wordt beschermd door de Firestore-regels en de login.
export const FIREBASE_CONFIG = {
  apiKey: '',
  authDomain: '',
  projectId: '',
  storageBucket: '',
  messagingSenderId: '',
  appId: '',
};

// Dezelfde benoemde database als de Android-app (zie firebase.json en res/values/strings.xml).
export const FIRESTORE_DATABASE_ID = 'ai-studio-android-maaltijd-8ed7dff5-f314-4b67-ad31-6fe21e7dd960';
