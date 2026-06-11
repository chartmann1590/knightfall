// Consent Mode v2 — must be the first script on every page, before AdSense loads.
window.dataLayer = window.dataLayer || [];
function gtag() { dataLayer.push(arguments); }

(function () {
  var KEY = 'kf_consent';

  // Default everything to denied so AdSense waits for explicit consent.
  gtag('consent', 'default', {
    ad_storage: 'denied',
    ad_user_data: 'denied',
    ad_personalization: 'denied',
    analytics_storage: 'denied',
    wait_for_update: 800
  });

  // If the user already decided, restore that decision immediately so ads
  // don't flicker on return visits.
  var stored;
  try { stored = localStorage.getItem(KEY); } catch (e) {}

  if (stored === 'granted') {
    gtag('consent', 'update', {
      ad_storage: 'granted',
      ad_user_data: 'granted',
      ad_personalization: 'granted',
      analytics_storage: 'granted'
    });
  }

  // ── Banner ─────────────────────────────────────────────────────────────────

  function privacyHref() {
    // Works whether the current page is at /knightfall/ or /knightfall/X.html
    var path = location.pathname;
    var base = path.substring(0, path.lastIndexOf('/') + 1);
    return base + 'privacy.html';
  }

  function injectBanner() {
    if (stored) return; // already decided — don't show

    var el = document.createElement('div');
    el.id = 'consent-banner';
    el.setAttribute('role', 'dialog');
    el.setAttribute('aria-label', 'Cookie consent');
    el.innerHTML =
      '<div class="consent-inner">' +
        '<p>We and our partners use cookies to serve personalised ads and measure' +
        ' site performance. <a href="' + privacyHref() + '">Privacy&nbsp;Policy</a></p>' +
        '<div class="consent-btns">' +
          '<button id="consent-reject">Reject non-essential</button>' +
          '<button id="consent-accept" class="consent-accept-btn">Accept all</button>' +
        '</div>' +
      '</div>';
    document.body.appendChild(el);

    document.getElementById('consent-accept').addEventListener('click', accept);
    document.getElementById('consent-reject').addEventListener('click', reject);
  }

  function accept() {
    try { localStorage.setItem(KEY, 'granted'); } catch (e) {}
    gtag('consent', 'update', {
      ad_storage: 'granted',
      ad_user_data: 'granted',
      ad_personalization: 'granted',
      analytics_storage: 'granted'
    });
    hideBanner();
  }

  function reject() {
    try { localStorage.setItem(KEY, 'denied'); } catch (e) {}
    // consent stays denied (default); mark explicit so we don't re-ask
    hideBanner();
  }

  function hideBanner() {
    var b = document.getElementById('consent-banner');
    if (b) b.remove();
  }

  // Allow footer "Cookie settings" link to re-open the banner
  window.openConsentSettings = function () {
    try { localStorage.removeItem(KEY); } catch (e) {}
    stored = null;
    hideBanner();
    injectBanner();
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', injectBanner);
  } else {
    injectBanner();
  }
})();
