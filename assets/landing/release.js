/* ---------- Release facts: the one place to edit when a version ships ----------
   Both index.html and zh.html load this file, so a release changes one object instead of two.
   Every [data-r] / [data-apk] / [data-release] element on the page is filled from it.

   Still edited by hand on release (crawlers and no-JS visitors do not run this file):
   - the literal "v0.5.47" fallbacks and download hrefs in both HTML files
   - softwareVersion / fileSize / downloadUrl in each page's JSON-LD
   Keep both channels aligned with the same verified APK. English prefers the
   GitHub file; Chinese prefers the Lanzou share page with its visible password. */
var RELEASE = {
  version: '0.5.47',
  versionCode: '99',
  date: '2026-10-26',
  size: '12 MB',
  sha256: 'fce5768b64052e6d8d860dbc97b170e9ff573314b3a8100f93272e450e7e3309',
  downloadUrl: 'https://github.com/Shmily0826/Appause/releases/download/v0.5.47/Appause-v0.5.47.apk',
  mirrorUrl: 'https://shmily0826.lanzoup.com/b01eunt29a',
  githubReleaseUrl: 'https://github.com/Shmily0826/Appause/releases/tag/v0.5.47'
};

(function () {
  var v = 'v' + RELEASE.version;
  var apk = document.documentElement.lang === 'zh-CN' ? RELEASE.mirrorUrl : RELEASE.downloadUrl;
  var rel = RELEASE.githubReleaseUrl;
  var map = {
    version: v,
    code: RELEASE.versionCode,
    date: RELEASE.date,
    size: (document.documentElement.lang === 'zh-CN' ? '约 ' : 'about ') + RELEASE.size,
    sha256: RELEASE.sha256,
    file: 'Appause-' + v + '.apk'
  };
  Object.keys(map).forEach(function (k) {
    document.querySelectorAll('[data-r="' + k + '"]').forEach(function (el) { el.textContent = map[k]; });
  });
  document.querySelectorAll('[data-apk]').forEach(function (el) { el.setAttribute('href', apk); });
  document.querySelectorAll('[data-release]').forEach(function (el) { el.setAttribute('href', rel); });
})();
