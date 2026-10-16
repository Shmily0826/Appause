/* ---------- Release facts: the one place to edit when a version ships ----------
   Both index.html and zh.html load this file, so a release changes one object instead of two.
   Every [data-r] / [data-apk] / [data-release] element on the page is filled from it.

   Still edited by hand on release (crawlers and no-JS visitors do not run this file):
   - the literal "v0.5.46" fallbacks and download hrefs in both HTML files
   - softwareVersion / fileSize / downloadUrl in each page's JSON-LD
   Keep RELEASE aligned with the verified APK offered on the primary share page.
   GitHub may lag behind; label its older fallback version explicitly in both pages. */
var RELEASE = {
  version: '0.5.46',
  versionCode: '98',
  date: '2026-10-17',
  size: '12.3 MB',
  sha256: 'ab455551b07503a9c3663e6f88759955251c42dd526888c0c874df53506fb927',
  downloadUrl: 'https://shmily0826.lanzoup.com/b01eunt29a',
  githubReleaseUrl: 'https://github.com/Shmily0826/Appause/releases/tag/v0.5.45'
};

(function () {
  var v = 'v' + RELEASE.version;
  var apk = RELEASE.downloadUrl;
  var rel = RELEASE.githubReleaseUrl;
  var map = {
    version: v,
    code: RELEASE.versionCode,
    date: RELEASE.date,
    size: RELEASE.size,
    sha256: RELEASE.sha256,
    file: 'Appause-' + v + '.apk'
  };
  Object.keys(map).forEach(function (k) {
    document.querySelectorAll('[data-r="' + k + '"]').forEach(function (el) { el.textContent = map[k]; });
  });
  document.querySelectorAll('[data-apk]').forEach(function (el) { el.setAttribute('href', apk); });
  document.querySelectorAll('[data-release]').forEach(function (el) { el.setAttribute('href', rel); });
})();
