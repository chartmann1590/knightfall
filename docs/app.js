// Centralized Firebase initialization & Shared logic for Knightfall website
const FIREBASE_CONFIG = window.KNIGHTFALL_FIREBASE_CONFIG || {};
const PROJECT_ID = FIREBASE_CONFIG.projectId || 'knightfall-chess-app';
const API_KEY = FIREBASE_CONFIG.apiKey || '';
const BASE = `https://firestore.googleapis.com/v1/projects/${PROJECT_ID}/databases/(default)/documents`;

// Initialize Firebase SDK if available
if (typeof firebase !== 'undefined' && API_KEY) {
  if (!firebase.apps.length) {
    firebase.initializeApp(FIREBASE_CONFIG);
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
} else if (typeof firebase !== 'undefined') {
  console.warn('Firebase config was not loaded; auth-backed website features are disabled.');
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
  const isTraining = path.includes('training.html');

  let html = `
    <a href="index.html" class="${isHome ? 'active' : ''}">Home</a>
    <a href="play.html" class="${isPlay ? 'active' : ''}">Play</a>
    <a href="training.html" class="${isTraining ? 'active' : ''}">Training</a>
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
        earnedBadges: doc.data().earnedBadges ?? [],
        puzzlesSolved: doc.data().puzzlesSolved ?? 0,
        puzzleStreak: doc.data().puzzleStreak ?? 0,
        bestPuzzleStreak: doc.data().bestPuzzleStreak ?? 0,
        featuredBadges: doc.data().featuredBadges ?? [],
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
          badgeCount: doc.data().badgeCount ?? 0,
          featuredBadges: doc.data().featuredBadges ?? [],
          puzzlesSolved: doc.data().puzzlesSolved ?? 0,
          earnedBadges: doc.data().featuredBadges ?? [],
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
    badgeCount: fieldVal(f.badgeCount) ?? 0,
    puzzlesSolved: fieldVal(f.puzzlesSolved) ?? 0,
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

// Badge manifest — mirrors BadgeRegistry.kt
const BADGE_MANIFEST = {
  first_win:        { name: 'First Blood',        emoji: '⚔️',  desc: 'Win your first online game' },
  ten_wins:         { name: 'Veteran',             emoji: '🎖️', desc: 'Win 10 online games' },
  fifty_wins:       { name: 'Knight Errant',       emoji: '🏇',  desc: 'Win 50 online games' },
  streak_3:         { name: 'On a Roll',           emoji: '🔥',  desc: 'Achieve a 3-game win streak' },
  streak_10:        { name: 'Unstoppable',         emoji: '💫',  desc: 'Achieve a 10-game win streak' },
  first_ai_win:     { name: 'Machine Slayer',      emoji: '🤖',  desc: 'Beat the AI for the first time' },
  ai_wins_10:       { name: 'Engine Killer',       emoji: '⚡',  desc: 'Beat the AI 10 times' },
  ai_wins_50:       { name: "Grandmaster's Bane",  emoji: '🏆',  desc: 'Beat the AI 50 times' },
  elo_1400:         { name: 'Rising Star',         emoji: '⭐',  desc: 'Reach 1400 Elo' },
  elo_1600:         { name: 'Expert',              emoji: '🌟',  desc: 'Reach 1600 Elo' },
  elo_1800:         { name: 'Master Candidate',    emoji: '💎',  desc: 'Reach 1800 Elo' },
  elo_2000:         { name: 'Master',              emoji: '👑',  desc: 'Reach 2000 Elo' },
  first_puzzle:     { name: 'Tactician',           emoji: '🧩',  desc: 'Solve your first puzzle' },
  puzzles_10:       { name: 'Puzzle Hunter',       emoji: '🔍',  desc: 'Solve 10 puzzles' },
  puzzles_50:       { name: 'Puzzle Addict',       emoji: '🎯',  desc: 'Solve 50 puzzles' },
  puzzles_100:      { name: 'Tactical Wizard',     emoji: '🪄',  desc: 'Solve 100 puzzles' },
  puzzle_streak_5:  { name: 'Sharp Eye',           emoji: '👁️', desc: 'Solve 5 puzzles in a row' },
  puzzle_streak_10: { name: 'Laser Focus',         emoji: '🎯',  desc: 'Solve 10 puzzles in a row' },
  hundred_games:    { name: 'Battle Hardened',     emoji: '🛡️', desc: 'Play 100 games total' },
  comeback:         { name: 'The Comeback Kid',    emoji: '💪',  desc: 'Win after suffering 10 losses' },
};

function renderBadgeShelf(badgeIds, containerEl) {
  if (!badgeIds || badgeIds.length === 0) {
    containerEl.innerHTML = '<p style="color:var(--smoke);font-size:0.9rem;">No badges earned yet. Keep playing!</p>';
    return;
  }
  containerEl.innerHTML = `<div class="badge-shelf">${
    badgeIds.map(id => {
      const b = BADGE_MANIFEST[id];
      if (!b) return '';
      return `<span class="badge-chip earned" title="${escapeHtml(b.desc)}">
        <span class="badge-emoji">${b.emoji}</span>
        <span>${escapeHtml(b.name)}</span>
      </span>`;
    }).join('')
  }</div>`;
}

// Client-side badge check — mirrors BadgeChecker.kt
function checkBadges(profile) {
  const earned = new Set(profile.earnedBadges || []);
  const newBadges = [];
  function award(id, cond) { if (cond && !earned.has(id)) newBadges.push(id); }
  const total = (profile.wins || 0) + (profile.losses || 0) + (profile.draws || 0);
  award('first_win',        (profile.wins || 0) >= 1);
  award('ten_wins',         (profile.wins || 0) >= 10);
  award('fifty_wins',       (profile.wins || 0) >= 50);
  award('streak_3',         (profile.bestWinStreak || 0) >= 3);
  award('streak_10',        (profile.bestWinStreak || 0) >= 10);
  award('first_ai_win',     (profile.aiWins || 0) >= 1);
  award('ai_wins_10',       (profile.aiWins || 0) >= 10);
  award('ai_wins_50',       (profile.aiWins || 0) >= 50);
  award('elo_1400',         (profile.elo || 0) >= 1400);
  award('elo_1600',         (profile.elo || 0) >= 1600);
  award('elo_1800',         (profile.elo || 0) >= 1800);
  award('elo_2000',         (profile.elo || 0) >= 2000);
  award('first_puzzle',     (profile.puzzlesSolved || 0) >= 1);
  award('puzzles_10',       (profile.puzzlesSolved || 0) >= 10);
  award('puzzles_50',       (profile.puzzlesSolved || 0) >= 50);
  award('puzzles_100',      (profile.puzzlesSolved || 0) >= 100);
  award('puzzle_streak_5',  (profile.bestPuzzleStreak || 0) >= 5);
  award('puzzle_streak_10', (profile.bestPuzzleStreak || 0) >= 10);
  award('hundred_games',    total >= 100);
  award('comeback',         (profile.wins || 0) >= 1 && (profile.losses || 0) >= 10);
  return newBadges;
}

async function awardBadgesWeb(uid, newBadgeIds) {
  if (!newBadgeIds.length || typeof db === 'undefined') return;
  await db.collection('users').doc(uid).update({
    earnedBadges: firebase.firestore.FieldValue.arrayUnion(...newBadgeIds),
  });
}
