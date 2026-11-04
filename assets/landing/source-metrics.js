(function () {
  // Local previews and automated inspections must never increment production.
  if (location.origin !== 'https://shmily0826.github.io' || navigator.webdriver) return;
  var source = new URLSearchParams(location.search).get('src');
  if (source !== 'reddit' && source !== 'v2ex') source = 'unknown';
  var endpoint = 'https://appause-pro-worker.rng2018520.workers.dev/api/source-metrics';
  function count(event) {
    try {
      fetch(endpoint, {method: 'POST', mode: 'cors', credentials: 'omit', referrerPolicy: 'no-referrer', keepalive: true,
        headers: {'Content-Type': 'text/plain'}, body: JSON.stringify({source: source, event: event})}).catch(function () {});
    } catch (_) { /* Counting must never block navigation or downloads. */ }
  }
  // Preserve attribution when visitors switch the landing page language.
  document.querySelectorAll('a[href="index.html"], a[href="zh.html"]').forEach(function (link) {
    var url = new URL(link.href); url.searchParams.set('src', source); link.href = url.href;
  });
  count('visit');
  document.addEventListener('click', function (event) {
    var link = event.target.closest('a[href]');
    if (!link) return;
    var url = new URL(link.href);
    if (url.hostname === 'github.com' && url.pathname.endsWith('.apk')) count('github_click');
    else if (url.hostname === 'shmily0826.lanzoup.com') count('lanzou_click');
  });
})();
