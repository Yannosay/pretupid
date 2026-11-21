#!/usr/bin/env python3
import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from pathlib import Path

API = "https://api.modrinth.com/v2"
USER_AGENT = "pretupid-release-tool/1.0"
REPO_ROOT = Path(__file__).resolve().parents[2]
MAX_ATTEMPTS = 5
ENVIRONMENT = "client_only"


class PublishError(Exception):
    pass


def request(method, path, token=None, body=None, content_type=None):
    headers = {"User-Agent": USER_AGENT, "Accept": "application/json"}
    if token:
        headers["Authorization"] = token
    if content_type:
        headers["Content-Type"] = content_type
    url = API + path
    for attempt in range(1, MAX_ATTEMPTS + 1):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, data=body, headers=headers, method=method), timeout=180) as response:
                payload = response.read()
                return json.loads(payload) if payload else None
        except urllib.error.HTTPError as error:
            detail = error.read().decode("utf-8", "replace")[:500]
            if error.code != 429 and error.code < 500 or attempt == MAX_ATTEMPTS:
                raise PublishError(f"{method} {path} failed with HTTP {error.code}: {detail}") from error
            delay = int(error.headers.get("X-Ratelimit-Reset") or 2 ** attempt)
        except urllib.error.URLError as error:
            if attempt == MAX_ATTEMPTS:
                raise PublishError(f"{method} {path} failed: {error.reason}") from error
            delay = 2 ** attempt
        time.sleep(min(delay, 60))
    raise PublishError(f"{method} {path} failed")


def multipart(data, field, file_name, content):
    boundary = uuid.uuid4().hex
    head = (
        f'--{boundary}\r\nContent-Disposition: form-data; name="data"\r\nContent-Type: application/json\r\n\r\n'
        f"{json.dumps(data)}\r\n"
        f'--{boundary}\r\nContent-Disposition: form-data; name="{field}"; filename="{file_name}"\r\n'
        "Content-Type: application/java-archive\r\n\r\n"
    ).encode("utf-8")
    return head + content + f"\r\n--{boundary}--\r\n".encode("ascii"), f"multipart/form-data; boundary={boundary}"


def minecraft_key(version):
    return tuple(int(part) for part in version.split("."))


def run(command):
    result = subprocess.run(command, cwd=REPO_ROOT, capture_output=True, text=True, encoding="utf-8")
    if result.returncode != 0:
        raise PublishError(f"{command[0]} {command[1]} failed: {result.stderr.strip()[:500]}")
    return result.stdout


def changelog(tag):
    text = run(["git", "show", f"refs/tags/{tag}:.github/release-notes/client-{tag}.md"]).replace("\r\n", "\n")
    return re.sub(r"(?ms)^## Downloads\n.*?(?=^## |\Z)", "", text).strip()


def load_builds(directory, version):
    manifest_path = directory / "client-manifest.json"
    if not manifest_path.is_file():
        raise PublishError(f"{manifest_path} is missing")
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if manifest.get("version") != version:
        raise PublishError(f"Manifest is for {manifest.get('version')}, expected {version}")
    builds = sorted(manifest["builds"], key=lambda build: minecraft_key(build["minecraft"]))
    if not builds:
        raise PublishError("Manifest lists no builds")
    for build in builds:
        path = directory / Path(build["file"]).name
        if not path.is_file():
            raise PublishError(f"{path.name} is missing")
        content = path.read_bytes()
        if len(content) != build["size"] or hashlib.sha512(content).hexdigest() != build["sha512"]:
            raise PublishError(f"{path.name} does not match the manifest")
    return builds


def publish(args):
    match = re.fullmatch(r"v(\d+\.\d+\.\d+)", args.tag)
    if not match:
        raise PublishError(f"{args.tag} is not a v<version> tag")
    version = match.group(1)
    token = os.environ.get("MODRINTH_TOKEN", "").strip()
    project = (args.project or os.environ.get("MODRINTH_PROJECT_ID", "")).strip()
    if not args.dry_run and (not token or not project):
        raise PublishError("Set MODRINTH_TOKEN and MODRINTH_PROJECT_ID, or use --dry-run")
    notes = changelog(args.tag)
    with tempfile.TemporaryDirectory(prefix="pretupid-modrinth-") as temporary:
        directory = Path(args.dir) if args.dir else Path(temporary)
        if not args.dir:
            run(["gh", "release", "download", args.tag, "-p", "*.jar", "-p", "client-manifest.json", "-D", str(directory)])
        builds = load_builds(directory, version)
        known = {entry["version"] for entry in request("GET", "/tag/game_version")}
        unknown = [build["minecraft"] for build in builds if build["minecraft"] not in known]
        if unknown:
            raise PublishError("Modrinth does not know these Minecraft versions: " + ", ".join(unknown))
        fabric_api = request("GET", "/project/fabric-api")["id"]
        existing = set()
        if project:
            quoted = urllib.parse.quote(project, safe="")
            project_info = request("GET", f"/project/{quoted}", token or None)
            project = project_info["id"]
            existing = {entry["version_number"] for entry in request("GET", f"/project/{quoted}/version", token or None)}
        newest = builds[-1]["minecraft"]
        groups = {}
        for build in builds:
            groups.setdefault(build["file"], {**build, "versions": []})["versions"].append(build["minecraft"])
        created = skipped = 0
        wrong_environment = []
        for build in groups.values():
            versions = build["versions"]
            minecraft = versions[0] if len(versions) == 1 else f"{versions[0]}-{versions[-1]}"
            number = f"{version}+mc{minecraft}"
            if number in existing:
                skipped += 1
                print(f"skip    {number} (already on Modrinth)")
                continue
            if args.dry_run:
                created += 1
                print(f"ready   {number}  {build['file']}  {build['size'] / 1048576:.1f} MB")
                continue
            data = {
                "project_id": project,
                "name": f"Pretupid Client {version} for {minecraft}",
                "version_number": number,
                "changelog": notes,
                "version_type": "release",
                "loaders": ["fabric"],
                "game_versions": versions,
                "environment": ENVIRONMENT,
                "featured": newest in versions,
                "status": "listed",
                "dependencies": [{"project_id": fabric_api, "dependency_type": "required"}],
                "file_parts": ["jar"],
                "primary_file": "jar",
            }
            body, content_type = multipart(data, "jar", Path(build["file"]).name, (directory / Path(build["file"]).name).read_bytes())
            uploaded = request("POST", "/version", token, body, content_type)
            created += 1
            print(f"created {number}")
            if not uploaded or uploaded.get("environment") != ENVIRONMENT:
                wrong_environment.append(number)
    action = "would upload" if args.dry_run else "uploaded"
    print(f"{action} {created}, skipped {skipped}, of {len(groups)} files for {len(builds)} Minecraft versions")
    if wrong_environment:
        raise PublishError("Modrinth did not confirm client-only for: " + ", ".join(wrong_environment))


def main():
    parser = argparse.ArgumentParser(description="Upload every Minecraft build of a Pretupid Client release to Modrinth.")
    parser.add_argument("tag", help="release tag, for example v0.1.8")
    parser.add_argument("--project", help="Modrinth project id or slug (default: MODRINTH_PROJECT_ID)")
    parser.add_argument("--dir", help="folder with the jars and client-manifest.json (default: download the GitHub release)")
    parser.add_argument("--dry-run", action="store_true", help="check everything without uploading")
    try:
        publish(parser.parse_args())
    except PublishError as error:
        print(f"error: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
