/* ---------- Release facts: the one place to edit when a version ships ----------
   Both index.html and zh.html load this file, so a release changes one object instead of two.
   Every [data-r] / [data-apk] / [data-release] element on the page is filled from it.

   Still edited by hand on release (crawlers and no-JS visitors do not run this file):
   - the literal "v0.5.50" fallbacks and download hrefs in both HTML files
   - softwareVersion / fileSize / downloadUrl in each page's JSON-LD
   Both pages use the GitHub APK for this release. */
var RELEASE = {
  version: '0.5.50',
  versionCode: '102',
  size: '12 MB',
  sha256: 'b05b5fadcac3b7ed6013ab96b7bbcf01b220c180bc7f2e41ae084002c11f3daa',
  downloadUrl: 'https://github.com/Shmily0826/Appause/releases/download/v0.5.50/Appause-v0.5.50.apk',
  githubReleaseUrl: 'https://github.com/Shmily0826/Appause/releases/tag/v0.5.50'
};

(function () {
  var v = 'v' + RELEASE.version;
  var apk = RELEASE.downloadUrl;
  var rel = RELEASE.githubReleaseUrl;
  var map = {
    version: v,
    code: RELEASE.versionCode,
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
