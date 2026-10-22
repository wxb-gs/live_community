#!/usr/bin/env python3
"""Resolve service paths from the Maven reactor; verify repository boundaries without middleware."""
import argparse
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
NS = {"m": "http://maven.apache.org/POM/4.0.0"}


def modules(root=ROOT):
    root = Path(root).resolve()
    found = {}
    active = set()

    def visit(directory):
        directory = directory.resolve()
        if not directory.is_relative_to(root):
            raise ValueError(f"Module outside repository: {directory}")
        if directory in active:
            raise ValueError(f"Maven aggregation cycle: {directory}")
        pom = directory / "pom.xml"
        tree = ET.parse(pom).getroot()
        artifact = tree.findtext("m:artifactId", namespaces=NS)
        if artifact in found:
            raise ValueError(f"Duplicate artifactId: {artifact}")
        found[artifact] = (directory, tree)
        active.add(directory)
        for item in tree.findall("m:modules/m:module", NS):
            visit(directory / item.text)
        active.remove(directory)

    visit(root)
    return found


def is_service(tree):
    return any(p.findtext("m:artifactId", namespaces=NS) == "spring-boot-maven-plugin"
               for p in tree.findall("m:build/m:plugins/m:plugin", NS))


def module_path(name, root=ROOT):
    entries = modules(root)
    if name not in entries:
        raise ValueError(f"Unknown Maven module: {name}")
    return entries[name][0]


def service_path(name, root=ROOT):
    entries = modules(root)
    if name not in entries or not is_service(entries[name][1]):
        raise ValueError(f"Not a deployable service: {name}")
    return entries[name][0]


def check(root=ROOT):
    root = Path(root).resolve()
    entries = modules(root)
    runtime = {name for name, (_, tree) in entries.items() if is_service(tree)}
    errors = []
    for name, (directory, tree) in entries.items():
        parent = tree.find("m:parent", NS)
        if parent is not None:
            relative = parent.findtext("m:relativePath", default="../pom.xml", namespaces=NS)
            parent_file = (directory / relative).resolve()
            if not parent_file.is_relative_to(root) or not parent_file.is_file():
                errors.append(f"{name}: invalid local parent path")
            else:
                expected = parent.findtext("m:artifactId", namespaces=NS)
                actual = ET.parse(parent_file).getroot().findtext("m:artifactId", namespaces=NS)
                if actual != expected:
                    errors.append(f"{name}: parent artifact mismatch")
        for dep in tree.findall("m:dependencies/m:dependency", NS):
            dep_name = dep.findtext("m:artifactId", namespaces=NS)
            group = dep.findtext("m:groupId", namespaces=NS)
            scope = dep.findtext("m:scope", default="compile", namespaces=NS)
            if group == "com.example" and dep_name not in entries:
                errors.append(f"{name}: unknown internal dependency {dep_name}")
            if group == "com.example" and dep_name in runtime:
                errors.append(f"{name}: depends on deployable service {dep_name}")
            if group == "com.example" and dep_name in entries:
                dependency_dir = entries[dep_name][0].relative_to(root)
                if directory.relative_to(root).parts[:1] == ("platform",) and dependency_dir.parts[:1] == ("services",):
                    errors.append(f"{name}: platform depends on business domain {dep_name}")
                if name == "gateway" and dep_name not in {"security-core", "common-core"}:
                    errors.append(f"gateway: business/RPC library {dep_name} does not belong at the HTTP edge")
            if name.endswith("-api") and scope != "test" and group != "com.example":
                errors.append(f"{name}: contracts must not depend on frameworks")
            if name == "gateway" and (group == "org.apache.dubbo" or dep_name in {"service-security", "rpc-security"}):
                errors.append("gateway: RPC or Servlet security dependency")
        if name in runtime and not (directory / "src/main/resources/application.yml").is_file():
            errors.append(f"{name}: missing application.yml")
    if errors:
        raise ValueError("\n".join(errors))
    return entries, runtime


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--service-path", metavar="ARTIFACT_ID")
    group.add_argument("--list-services", action="store_true")
    group.add_argument("--check", action="store_true")
    args = parser.parse_args()
    if args.service_path:
        print(service_path(args.service_path).relative_to(ROOT).as_posix())
    elif args.list_services:
        entries, runtime = check()
        for name in sorted(runtime):
            print(name, entries[name][0].relative_to(ROOT).as_posix())
    else:
        entries, runtime = check()
        print(f"Layout OK: {len(runtime)} deployable services, {len(entries)} Maven projects including aggregators")


if __name__ == "__main__":
    main()
