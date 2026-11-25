// Accentkleur van de app, per toestel bewaard. Standaard terracotta.
export const ACCENTS = [
  { id: 'terracotta', label: 'Terracotta', color: '#B44E29' },
  { id: 'blauw', label: 'Diepblauw', color: '#2B4C7E' },
  { id: 'aubergine', label: 'Aubergine', color: '#6A3D63' },
  { id: 'groen', label: 'Groen', color: '#3D6B47' },
  { id: 'oker', label: 'Oker', color: '#8A5A0B' },
];
const KEY = 'maaltijdplanner.accent';

export function currentAccent() {
  let id = null;
  try { id = localStorage.getItem(KEY); } catch { /* geen opslag */ }
  return ACCENTS.some(a => a.id === id) ? id : 'terracotta';
}

export function applyAccent(id = currentAccent()) {
  if (id === 'terracotta') delete document.documentElement.dataset.accent;
  else document.documentElement.dataset.accent = id;
}

export function setAccent(id) {
  try { localStorage.setItem(KEY, id); } catch { /* geen opslag: geldt tot herladen */ }
  applyAccent(id);
}
