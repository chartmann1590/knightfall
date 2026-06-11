// Reads the live leaderboard from Firestore's public REST API.
// Only players who opted in to a public profile exist in this collection —
// privacy is enforced server-side by Firestore security rules.
const PROJECT_ID = 'knightfall-chess-app';
const API_KEY = 'AIzaSyCM6V5eOm7Po54odRYWPjuygV_kFyHeqhw';
const BASE = `https://firestore.googleapis.com/v1/projects/${PROJECT_ID}/databases/(default)/documents`;

function fieldVal(field) {
  if (field == null) return null;
  if ('integerValue' in field) return parseInt(field.integerValue, 10);
  if ('doubleValue' in field) return field.doubleValue;
  if ('stringValue' in field) return field.stringValue;
  if ('booleanValue' in field) return field.booleanValue;
  return null;
}

function docToProfile(doc) {
  const f = doc.fields || {};
  return {
    uid: doc.name.split('/').pop(),
    username: fieldVal(f.username) || 'Player',
    elo: fieldVal(f.elo) ?? 1200,
    wins: fieldVal(f.wins) ?? 0,
    losses: fieldVal(f.losses) ?? 0,
    draws: fieldVal(f.draws) ?? 0,
    bestWinStreak: fieldVal(f.bestWinStreak) ?? 0,
  };
}

async function fetchLeaderboard(limit = 100) {
  const res = await fetch(`${BASE}:runQuery?key=${API_KEY}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      structuredQuery: {
        from: [{ collectionId: 'publicProfiles' }],
        orderBy: [{ field: { fieldPath: 'elo' }, direction: 'DESCENDING' }],
        limit,
      },
    }),
  });
  if (!res.ok) throw new Error(`Firestore query failed: ${res.status}`);
  const rows = await res.json();
  return rows.filter(r => r.document).map(r => docToProfile(r.document));
}

async function fetchProfile(uid) {
  const res = await fetch(`${BASE}/publicProfiles/${encodeURIComponent(uid)}?key=${API_KEY}`);
  if (!res.ok) return null;
  return docToProfile(await res.json());
}

function escapeHtml(s) {
  return s.replace(/[&<>"']/g, c => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
  }[c]));
}

function medal(rank) {
  if (rank === 1) return '🥇';
  if (rank === 2) return '🥈';
  if (rank === 3) return '🥉';
  return rank;
}
