import os, json, sys, urllib.request

PAT = os.environ["GHPAT"]
REPO = "Shmily0826/Appause"
TAG = "v0.4.6"
APK = r"D:\CODE\project\Appause\output\Appause-v0.4.6.apk"

# Read release notes, strip the leading instruction block (lines 1..5)
with open(r"D:\CODE\project\Appause\RELEASE_NOTES.md", encoding="utf-8") as f:
    lines = f.read().split("\n")
# keep from the first "## " section onward
start = next(i for i, l in enumerate(lines) if l.startswith("## "))
body = "\n".join(lines[start:]).strip()

def api(method, url, data=None, headers=None, binary=None):
    h = {"Authorization": f"Bearer {PAT}", "Accept": "application/vnd.github+json"}
    if headers: h.update(headers)
    if data is not None:
        data = data.encode("utf-8")
        h["Content-Type"] = "application/json"
    elif binary is not None:
        h["Content-Type"] = "application/vnd.android.package-archive"
    req = urllib.request.Request(url, data=data if (data is not None or binary is not None) else None, headers=h, method=method)
    with urllib.request.urlopen(req) as r:
        return r.status, r.read().decode("utf-8", "replace")

# 1) create release
payload = json.dumps({"tag_name": TAG, "name": TAG, "body": body, "draft": False, "prerelease": False})
status, resp = api("POST", f"https://api.github.com/repos/{REPO}/releases", data=payload)
print("create release:", status)
rel = json.loads(resp)
rid = rel["id"]
print("release id:", rid)

# 2) upload asset
with open(APK, "rb") as f:
    blob = f.read()
status, resp = api("POST",
    f"https://uploads.github.com/repos/{REPO}/releases/{rid}/assets?name=Appause-v0.4.6.apk",
    binary=blob)
print("upload asset:", status)
print(resp[:200])
