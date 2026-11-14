/* ---------- Release facts: the one place to edit when a version ships ----------
   Both index.html and zh.html load this file, so a release changes one object instead of two.
   Every [data-r] / [data-apk] / [data-release] element on the page is filled from it.

   Still edited by hand on release (crawlers and no-JS visitors do not run this file):
   - the literal version labels in both HTML files
   - softwareVersion / fileSize / downloadUrl in each page's JSON-LD
   Download links stay on /releases/latest because APK filenames include the version. */
var RELEASE = {
  version: '0.5.51',
  versionCode: '103',
  size: '12 MB',
  sha256: '943fb1e92a7e4eb0c6cfbe18ee6cd6842e6aee63a9edbde72415803da51dffbf',
  downloadUrl: 'https://github.com/Shmily0826/Appause/releases/latest',
  githubReleaseUrl: 'https://github.com/Shmily0826/Appause/releases/latest'
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
