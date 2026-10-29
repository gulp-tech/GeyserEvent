# -*- coding: utf-8 -*-
"""Сборка GeyserEvent-1.0.0.jar (ECJ + зависимости из GitHub-блобов)."""
import os, glob, shutil, subprocess, zipfile, json, base64, sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
os.chdir(REPO)

deps_dir = "/tmp/build_deps"
os.makedirs(deps_dir, exist_ok=True)

try:
    import jdk4py
except ImportError:
    subprocess.run([sys.executable, "-m", "pip", "install", "--quiet", "--break-system-packages", "jdk4py"], check=True)
    import jdk4py
java_bin = str(jdk4py.JAVA)

blobs = {
    "ecj-3.43.0.jar": "0f24404dd594675a3ca7ae4aea56da38f63bfb92",
    "paper-api-full.jar": "4660821859d124b96758cf0f616f1d8eed294cd4",
    "bungeecord-chat.jar": "c7f8e98819f46bf2ecabbad8fc58f2ada0086a45",
    "guava.jar": "9a88e1674d34fbeff6602950bf86e19bd5baea93",
    "adventure-api.jar": "bae00c057f1723fc9243b13a1ff025cab1e67465",
    "adventure-key.jar": "b58fcfac540d82bec910f335aa4d929bb731507e",
    "annotations.jar": "28d234f76581279b699da4eb56b449cb1eaff066",
}

gh_repo = "trovao557-dotcom/merelymesmpteste"
for name, sha in blobs.items():
    dest = os.path.join(deps_dir, name)
    if not os.path.exists(dest) or os.path.getsize(dest) == 0:
        print(f"Fetching {name}...")
        raw = subprocess.check_output(["gh", "api", f"repos/{gh_repo}/git/blobs/{sha}"])
        data = json.loads(raw)
        with open(dest, "wb") as f:
            f.write(base64.b64decode(data["content"]))

out_classes = "/tmp/geyserevent_classes"
shutil.rmtree(out_classes, ignore_errors=True)
os.makedirs(out_classes, exist_ok=True)

with zipfile.ZipFile("GeyserEvent-1.0.0.jar", "r") as zf:
    for member in zf.namelist():
        if member.startswith("ru/geyserevent/hook/") and member.endswith(".class"):
            zf.extract(member, out_classes)

java_sources = [
    p for p in glob.glob("src/main/java/**/*.java", recursive=True)
    if not p.endswith("hook/BedrockService.java") and not p.endswith("hook/PlayerPointsHook.java")
]
print(f"Compiling {len(java_sources)} Java source files...")

cp = ":".join([os.path.join(deps_dir, k) for k in blobs if k != "ecj-3.43.0.jar"] + [out_classes])
cmd = [
    java_bin, "-jar", os.path.join(deps_dir, "ecj-3.43.0.jar"),
    "-21", "-encoding", "UTF-8", "-proc:none", "-nowarn",
    "-cp", cp,
    "-d", out_classes
] + java_sources

res = subprocess.run(cmd, capture_output=True, text=True)
if res.returncode != 0:
    print("STDOUT:", res.stdout)
    print("STDERR:", res.stderr)
    raise SystemExit(res.returncode)
print("Compilation succeeded with 0 errors!")

jar_path = "GeyserEvent-1.0.0.jar"
with zipfile.ZipFile(jar_path, "w", zipfile.ZIP_DEFLATED) as zf:
    for root, _, files in os.walk(out_classes):
        for file in sorted(files):
            full = os.path.join(root, file)
            zf.write(full, os.path.relpath(full, out_classes))
    res_dir = "src/main/resources"
    for root, _, files in os.walk(res_dir):
        for file in sorted(files):
            full = os.path.join(root, file)
            zf.write(full, os.path.relpath(full, res_dir))

print("Built JAR size:", os.path.getsize(jar_path), "bytes")
with zipfile.ZipFile(jar_path, "r") as zf:
    names = zf.namelist()
    print("plugin.yml version:", [l for l in zf.read("plugin.yml").decode("utf-8").splitlines() if "version" in l])
    print("pack in jar:", [n for n in names if "mcpack" in n])
    print("hook classes:", [n for n in names if n.startswith("ru/geyserevent/hook/")])
