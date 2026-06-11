// Centralized Firebase initialization & Shared logic for Knightfall website
const PROJECT_ID = 'knightfall-chess-app';
const API_KEY = 'AIzaSyA3CHXn6vYbYDcfLIJzj0wLDXXkfjvBB0o'; // Auth-enabled API key
const BASE = `https://firestore.googleapis.com/v1/projects/${PROJECT_ID}/databases/(default)/documents`;

// Initialize Firebase SDK if available
if (typeof firebase !== 'undefined') {
  if (!firebase.apps.length) {
    firebase.initializeApp({
      apiKey:            API_KEY,
      authDomain:        'knightfall-chess-app.firebaseapp.com',
      projectId:         'knightfall-chess-app',
      storageBucket:     'knightfall-chess-app.firebasestorage.app',
      messagingSenderId: '439141758944',
      appId:             '1:439141758944:web:7c1fe2a43cf84b80d41d85',
    });
  }
  const auth = firebase.auth();
  const db   = firebase.firestore();
  window.auth = auth;
  window.db   = db;

  // Listen to Auth state and dynamically update navigation bar
  auth.onAuthStateChanged(user => {
    updateNavbar(user);
    if (window.onAuthChanged) {
      window.onAuthChanged(user);
    }
  });
}

// Update the navigation bar based on the current user status
function updateNavbar(user) {
  const navLinks = document.querySelector('.nav-links');
  if (!navLinks) return;

  const path = window.location.pathname;
  const isHome = path.endsWith('index.html') || path.endsWith('/') || (!path.includes('.html') && path.endsWith('knightfall'));
  const isPlay = path.includes('play.html');
  const isLb = path.includes('leaderboard.html');
  const isProfile = path.includes('profile.html');

  let html = `
    <a href="index.html" class="${isHome ? 'active' : ''}">Home</a>
    <a href="play.html" class="${isPlay ? 'active' : ''}">Play</a>
    <a href="leaderboard.html" class="${isLb ? 'active' : ''}">Leaderboard</a>
  `;

  if (user) {
    html += `
      <a href="profile.html" class="${isProfile ? 'active' : ''}">My Profile</a>
      <a href="#" onclick="doSignOut(event)">Sign Out</a>
    `;
  } else {
    html += `
      <a href="profile.html" class="${isProfile ? 'active' : ''}">Sign In</a>
    `;
  }

  html += `
    <a href="https://github.com/chartmann1590/knightfall/releases/latest">Download</a>
  `;

  navLinks.innerHTML = html;
}

window.doSignOut = async function(e) {
  if (e) e.preventDefault();
  if (typeof auth !== 'undefined') {
    try {
      await auth.signOut();
      window.location.href = 'index.html';
    } catch(err) {
      console.error("Sign out error:", err);
    }
  }
};

// Create a private profile document for newly registered players in users/{uid}
async function ensureProfileExists(user, optUsername) {
  if (!user || typeof db === 'undefined') return;
  const docRef = db.collection('users').doc(user.uid);
  try {
    const doc = await docRef.get();
    if (!doc.exists) {
      let username = optUsername || user.displayName || user.email || 'Guest';
      if (username.includes('@')) {
        username = username.split('@')[0];
      }
      username = username.trim().substring(0, 24);
      if (username.length < 2) username = 'Player';

      await docRef.set({
        username: username,
        avatarColor: 'gold',
        elo: 1200,
        wins: 0,
        losses: 0,
        draws: 0,
        bestWinStreak: 0,
        currentWinStreak: 0,
        aiWins: 0,
        isPublic: false,
        createdAt: firebase.firestore.FieldValue.serverTimestamp(),
      });
    }
  } catch (err) {
    console.error("Error creating default user profile:", err);
  }
}

// Attempt to load private profile users/{uid} (requires viewer to be owner)
async function fetchPrivateProfile(uid) {
  if (typeof db === 'undefined') return null;
  try {
    const doc = await db.collection('users').doc(uid).get();
    if (doc.exists) {
      return {
        uid: doc.id,
        username: doc.data().username || 'Player',
        elo: doc.data().elo ?? 1200,
        wins: doc.data().wins ?? 0,
        losses: doc.data().losses ?? 0,
        draws: doc.data().draws ?? 0,
        bestWinStreak: doc.data().bestWinStreak ?? 0,
        isPublic: doc.data().isPublic ?? false,
      };
    }
  } catch (e) {
    // Expected to fail if viewer is not the profile owner
    console.warn("fetchPrivateProfile failed, likely not owner:", e);
  }
  return null;
}

// Fetch user profile trying private, then public collection
async function fetchUserProfile(uid) {
  let p = await fetchPrivateProfile(uid);
  if (p) {
    p.isPrivateView = true;
    return p;
  }

  // Fallback to publicProfiles collection
  if (typeof db !== 'undefined') {
    try {
      const doc = await db.collection('publicProfiles').doc(uid).get();
      if (doc.exists) {
        return {
          uid: doc.id,
          username: doc.data().username || 'Player',
          elo: doc.data().elo ?? 1200,
          wins: doc.data().wins ?? 0,
          losses: doc.data().losses ?? 0,
          draws: doc.data().draws ?? 0,
          bestWinStreak: doc.data().bestWinStreak ?? 0,
          isPrivateView: false,
        };
      }
    } catch(e) {
      console.warn("db publicProfile fetch failed, using REST fallback:", e);
    }
  }

  p = await fetchProfile(uid);
  if (p) {
    p.isPrivateView = false;
  }
  return p;
}

// Fetch up to 20 recent games for the specified player from whiteUid or blackUid queries
async function fetchUserGames(uid) {
  if (typeof db === 'undefined') return [];
  try {
    const q1 = db.collection('games')
      .where('whiteUid', '==', uid)
      .orderBy('finishedAt', 'desc')
      .limit(20)
      .get();

    const q2 = db.collection('games')
      .where('blackUid', '==', uid)
      .orderBy('finishedAt', 'desc')
      .limit(20)
      .get();

    const [snap1, snap2] = await Promise.all([q1, q2]);
    const games = [];

    snap1.forEach(doc => {
      games.push({ id: doc.id, ...doc.data() });
    });
    snap2.forEach(doc => {
      if (!games.find(g => g.id === doc.id)) {
        games.push({ id: doc.id, ...doc.data() });
      }
    });

    games.sort((a, b) => {
      const tA = a.finishedAt ? a.finishedAt.toMillis() : 0;
      const tB = b.finishedAt ? b.finishedAt.toMillis() : 0;
      return tB - tA;
    });

    return games.slice(0, 20);
  } catch(e) {
    console.error("fetchUserGames failed:", e);
    return [];
  }
}

// REST helper functions
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
