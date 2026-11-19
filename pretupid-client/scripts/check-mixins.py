import re
import subprocess
import sys
from pathlib import Path

CLIENT = Path(__file__).resolve().parent.parent
LOOM = Path.home() / ".gradle" / "caches" / "fabric-loom"


def classpath(version):
    base = LOOM / version
    split = [base / "minecraft-client-only.jar", base / "minecraft-common.jar"]
    if all(path.exists() for path in split):
        return split
    for name in ("minecraft-merged.jar", "minecraft-client.jar"):
        if (base / name).exists():
            return [base / name]
    deobf = list((LOOM / "minecraftMaven" / "net" / "minecraft").glob(f"minecraft-merged-deobf/{version}/*.jar"))
    return deobf


def javap(version, cls, cache):
    key = (version, cls)
    if key not in cache:
        jars = classpath(version)
        result = subprocess.run(["javap", "-p", "-s", "-cp", ";".join(str(j) for j in jars), cls], capture_output=True, text=True)
        cache[key] = result.stdout if result.returncode == 0 else None
    return cache[key]


def members(listing):
    names = {}
    current = None
    for line in listing.splitlines():
        line = line.strip()
        if line.startswith("descriptor:") and current:
            names.setdefault(current, set()).add(line.split(":", 1)[1].strip())
            current = None
            continue
        match = re.search(r"([\w$<>]+)\((.*)\)(?: throws [\w.,$ ]+)?;$", line)
        if match:
            current = match.group(1)
            if current == listing.splitlines()[0].split()[-1].split(".")[-1]:
                current = "<init>"
            continue
        match = re.search(r"([\w$]+);$", line)
        if match:
            current = match.group(1)
    return names


def supertypes(listing):
    header = next((line for line in listing.splitlines() if " class " in line or " interface " in line), "")
    header = re.sub(r"<[^<>]*(?:<[^<>]*>[^<>]*)*>", "", header)
    found = []
    for keyword in ("extends", "implements"):
        match = re.search(keyword + r" ([\w.$, ]+?)(?: implements| \{|$)", header)
        if match:
            found += [name.strip() for name in match.group(1).split(",") if name.strip()]
    return found


def all_members(version, cls, cache, depth=0):
    listing = javap(version, cls, cache)
    if listing is None:
        return None
    available = members(listing)
    if depth < 8:
        for parent in supertypes(listing):
            inherited = all_members(version, parent, cache, depth + 1)
            for name, descriptors in (inherited or {}).items():
                available.setdefault(name, set()).update(descriptors)
    return available


def arity(descriptor):
    inner = descriptor[1:descriptor.index(")")]
    return len(re.findall(r"\[*(?:L[^;]+;|[ZBCSIJFD])", inner))


def resolve_import(source, simple):
    for match in re.finditer(r"^import ([\w.]+)\.(\w+);", source, re.M):
        if match.group(2) == simple.split(".")[0]:
            outer = match.group(1) + "." + match.group(2)
            rest = simple.split(".")[1:]
            return outer + "".join("$" + part for part in rest)
    return None


def check(version, roots):
    cache = {}
    problems = []
    for root in roots:
        for path in sorted(root.rglob("*Mixin.java")):
            source = path.read_text(encoding="utf-8")
            target_match = re.search(r"@Mixin\((?:value\s*=\s*)?\{?\s*([\w.]+)\.class", source)
            if not target_match:
                continue
            target = resolve_import(source, target_match.group(1))
            if target is None:
                problems.append(f"{path.name}: cannot resolve {target_match.group(1)}")
                continue
            available = all_members(version, target, cache)
            if available is None:
                problems.append(f"{path.name}: class {target} missing")
                continue
            for method in re.finditer(r'method\s*=\s*(\{[^}]*\}|"[^"]*")', source):
                for name in re.findall(r'"([^"]+)"', method.group(1)):
                    bare = name.split("(")[0]
                    descriptor = name[len(bare):]
                    if bare not in available:
                        problems.append(f"{path.name}: {target}#{bare} missing")
                    elif descriptor and descriptor not in available[bare]:
                        problems.append(f"{path.name}: {target}#{bare}{descriptor} has {sorted(available[bare])}")
            for shadow in re.finditer(r"@Shadow(?:\s*@\w+)*\s+(?:private|protected|public)?\s*(?:abstract\s+|static\s+|final\s+)*[\w.<>?, \[\]]+?\s+(\w+)\s*([;(])([^)]*\))?", source):
                name = shadow.group(1)
                if name not in available:
                    problems.append(f"{path.name}: shadow {target}#{name} missing")
                elif shadow.group(2) == "(":
                    params = shadow.group(3)[:-1].strip()
                    count = 0 if not params else len([part for part in re.sub(r"<[^>]*>", "", params).split(",")])
                    if not any(d.startswith("(") and arity(d) == count for d in available[name]):
                        problems.append(f"{path.name}: shadow {target}#{name} with {count} parameters has {sorted(available[name])}")
            for inject in re.finditer(r'@Inject\((.*?)\)\s*(?:private|public|protected)?\s*(?:static\s+)?(?:<[^(]*?>\s*)?[\w.<>?]+\s+\w+\s*\((.*?)\)\s*\{', source, re.S):
                names = re.findall(r'"([^"]+)"', re.search(r'method\s*=\s*(\{[^}]*\}|"[^"]*")', inject.group(1)).group(1)) if 'method' in inject.group(1) else []
                params = [part.strip() for part in re.sub(r"<[^<>]*(?:<[^<>]*>[^<>]*)*>", "", inject.group(2)).split(",") if part.strip()]
                declared = 0
                for part in params:
                    if re.search(r"\bCallbackInfo(Returnable)?\b", part):
                        break
                    if "@Local" in part:
                        continue
                    declared += 1
                if declared == 0:
                    continue
                for name in names:
                    bare = name.split("(")[0]
                    if "(" in name or bare not in available:
                        continue
                    if not any(d.startswith("(") and arity(d) == declared for d in available[bare]):
                        problems.append(f"{path.name}: @Inject {target}#{bare} handler takes {declared} arguments, target has {sorted(available[bare])}")
            for invoke in re.finditer(r'target\s*=\s*"L([\w/$]+);(\w+)(\([^"]*)"', source):
                owner = invoke.group(1).replace("/", ".")
                owner_members = all_members(version, owner, cache)
                if owner_members is None:
                    problems.append(f"{path.name}: invoke owner {owner} missing")
                    continue
                if invoke.group(2) not in owner_members:
                    problems.append(f"{path.name}: invoke {owner}#{invoke.group(2)} missing")
                elif invoke.group(3) not in owner_members[invoke.group(2)]:
                    problems.append(f"{path.name}: invoke {owner}#{invoke.group(2)}{invoke.group(3)} has {sorted(owner_members[invoke.group(2)])}")
            for field in re.finditer(r'target\s*=\s*"L([\w/$]+);(\w+):([^"]+)"', source):
                owner = field.group(1).replace("/", ".")
                owner_members = all_members(version, owner, cache)
                if owner_members is None or field.group(2) not in owner_members:
                    problems.append(f"{path.name}: field {owner}#{field.group(2)} missing")
    return problems


def main():
    targets = sys.argv[1:] or sorted(p.name for p in (CLIENT / "targets").iterdir() if p.is_dir())
    failed = False
    for version in targets:
        properties = dict(
            line.split("=", 1) for line in (CLIENT / "targets" / version / "target.properties").read_text().splitlines() if "=" in line
        )
        roots = [CLIENT / "src" / "main" / "java" / "net" / "pretupid" / "client" / "mixin", CLIENT / "bridges" / properties["bridge"] / "java"]
        problems = check(properties["minecraft_version"], roots)
        print(f"{version}: {'ok' if not problems else str(len(problems)) + ' problems'}")
        for problem in problems:
            print("  " + problem)
        failed |= bool(problems)
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
