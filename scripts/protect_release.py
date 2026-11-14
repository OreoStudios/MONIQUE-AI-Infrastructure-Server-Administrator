#!/usr/bin/env python3
"""Create an UNSIGNED Spring Boot protected release candidate via OreoObfuscator.

Release CI must run full Java 21 + Spring integration tests before signing.
Only application classes/resources are transformed. Third-party libraries stay intact.
"""
from __future__ import annotations

import argparse
import os
from pathlib import Path, PurePosixPath
import shutil
import stat
import struct
import subprocess
import tempfile
from zipfile import ZipFile, ZIP_DEFLATED, BadZipFile


APP_PREFIX = "BOOT-INF/classes/"
REQUIRED_CLASSES = (
    "fr/oreostudios/monique/MoniqueApplication.class",
    "fr/oreostudios/monique/security/SecurityConfig.class",
    "fr/oreostudios/monique/pods/PodService.class",
)
REQUIRED_RESOURCES = ("application.yml", "schema.sql")


class ProtectionError(RuntimeError):
    pass


def check_name(name: str) -> None:
    parts = PurePosixPath(name).parts
    if not name or name.startswith("/") or "\\" in name or ".." in parts:
        raise ProtectionError("Unsafe ZIP member path")


def verify_bytecode(jar: ZipFile) -> None:
    found = 0
    for name in jar.namelist():
        check_name(name)
        if not name.endswith(".class"):
            continue
        contents = jar.read(name)
        if len(contents) < 8 or contents[:4] != b"\xca\xfe\xba\xbe":
            raise ProtectionError("Invalid classfile in protected JAR")
        major = struct.unpack(">H", contents[6:8])[0]
        if major > 65:
            raise ProtectionError("Protected classfile requires newer than Java 21")
        found += 1
    if not found:
        raise ProtectionError("No Java classes in transformed JAR")


def make_input_jar(classes: Path, dest: Path) -> set[str]:
    if not classes.is_dir():
        raise ProtectionError("Missing compiled target/classes (run Maven first)")
    names = set()
    with ZipFile(dest, "w", compression=ZIP_DEFLATED) as jar:
        for file in sorted(classes.rglob("*")):
            if file.is_symlink():
                raise ProtectionError("Symlinks in target/classes are forbidden")
            if file.is_file():
                name = file.relative_to(classes).as_posix()
                check_name(name)
                jar.write(file, name)
                names.add(name)
    if not set(REQUIRED_CLASSES + REQUIRED_RESOURCES).issubset(names):
        raise ProtectionError("Required compiled Spring classes/resources missing")
    return names


def repackage(boot_file: Path, obfuscated_file: Path, output: Path,
              original_names: set[str]) -> None:
    """Preserve every non-application entry in the original Boot executable."""
    with ZipFile(boot_file) as boot, ZipFile(obfuscated_file) as obf:
        original_boot_names = set(boot.namelist())
        if APP_PREFIX + REQUIRED_CLASSES[0] not in original_boot_names:
            raise ProtectionError("Input is not a Spring Boot executable JAR")
        if "META-INF/MANIFEST.MF" not in original_boot_names:
            raise ProtectionError("Boot manifest missing")
        for name in boot.namelist():
            check_name(name)
            if name.upper().startswith("META-INF/") and name.upper().endswith(
                    (".SF", ".RSA", ".DSA")):
                raise ProtectionError("Refusing to rewrite a signed JAR")
        verify_bytecode(obf)
        transformed_names = set(obf.namelist())
        if not set(REQUIRED_CLASSES + REQUIRED_RESOURCES).issubset(transformed_names):
            raise ProtectionError("Spring entry point/resources missing after obfuscation")
        old_classes = {n for n in original_names if n.endswith(".class")}
        new_classes = {n for n in transformed_names if n.endswith(".class")}
        if not old_classes - new_classes:
            raise ProtectionError("No class renamed; refusing unprotected artifact")
        if output.exists():
            raise ProtectionError("Protected destination already exists")

        with ZipFile(output, "w") as merged:
            for info in boot.infolist():
                if info.filename.startswith(APP_PREFIX):
                    continue
                merged.writestr(info, boot.read(info.filename))
            for info in obf.infolist():
                if info.is_dir():
                    continue
                check_name(info.filename)
                merged.writestr(APP_PREFIX + info.filename, obf.read(info.filename),
                                compress_type=info.compress_type)
    with ZipFile(output) as result, ZipFile(boot_file) as boot, ZipFile(obfuscated_file) as obf:
        for name in REQUIRED_CLASSES + REQUIRED_RESOURCES:
            if APP_PREFIX + name not in result.namelist():
                raise ProtectionError("Final JAR missing required Spring files")
        for info in boot.infolist():
            if not info.filename.startswith(APP_PREFIX):
                if result.read(info.filename) != boot.read(info.filename):
                    raise ProtectionError("Boot loader/dependency content modified")
        for info in obf.infolist():
            if not info.is_dir() and result.read(APP_PREFIX + info.filename) != obf.read(info.filename):
                raise ProtectionError("Transformed application content modified")


def protect(classes_dir: Path, boot_jar: Path, output: Path, profile: Path,
            oreo_cli: str, private_mapping_dir: Path, seed: int) -> None:
    if not boot_jar.is_file() or not profile.is_file():
        raise ProtectionError("Missing Maven executable JAR or Oreo profile")
    if boot_jar.resolve() == output.resolve():
        raise ProtectionError("Never overwrite the original executable")
    cli = shutil.which(oreo_cli) if oreo_cli else None
    if not cli:
        raise ProtectionError("Specify OREO_CLI path to the installed OreoObfuscator launcher")
    output.parent.mkdir(parents=True, exist_ok=True)
    private_mapping_dir.mkdir(mode=0o700, parents=True, exist_ok=True)
    if os.name == "posix":
        private_mapping_dir.chmod(0o700)
    with tempfile.TemporaryDirectory(prefix="monique-obfuscate-") as tmp:
        work = Path(tmp)
        thin, transformed, candidate = (work / f for f in ("thin.jar", "protected.jar", "boot.jar"))
        originals = make_input_jar(classes_dir, thin)
        proc = subprocess.run(
            [cli, "protect", str(thin), str(transformed),
             "--config", str(profile.resolve()), "--seed", str(seed)],
            cwd=work, capture_output=True, check=False,
        )
        if proc.returncode:
            raise ProtectionError("OreoObfuscator error (exit "
                                  + str(proc.returncode) + "); check license and profile")
        if not transformed.is_file():
            raise ProtectionError("OreoObfuscator produced no transformed JAR")
        repackage(boot_jar, transformed, candidate, originals)
        shutil.copy2(candidate, output)
        for path in (work / "mapping.json", transformed.parent / "mapping.json"):
            if path.is_file():
                private = private_mapping_dir / (output.stem + "-mapping.json")
                shutil.copyfile(path, private)
                if os.name == "posix":
                    private.chmod(stat.S_IRUSR | stat.S_IWUSR)
                break
    print("Protected release candidate (unsigned):", output)
    print("Post-obfuscation Java 21/Spring/security tests required before release.")


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description="Build MONIQUE OreoObfuscator release candidate")
    parser.add_argument("--classes-dir", type=Path, default=root / "backend/target/classes")
    parser.add_argument("--boot-jar", type=Path,
                        default=root / "backend/target/monique-backend-0.1.0-SNAPSHOT.jar")
    parser.add_argument("--output", type=Path,
                        default=root / "backend/target/monique-backend-protected.jar")
    parser.add_argument("--config", type=Path, default=root / "packaging/oreo-monique-safe.yml")
    parser.add_argument("--oreo-cli", default=os.getenv("OREO_CLI", ""))
    parser.add_argument("--mapping-dir", type=Path,
                        default=root / "backend/target/private-mappings")
    parser.add_argument("--seed", type=int, default=1729)
    args = parser.parse_args()
    try:
        protect(args.classes_dir, args.boot_jar, args.output,
                args.config, args.oreo_cli, args.mapping_dir, args.seed)
        return 0
    except (OSError, BadZipFile, ProtectionError) as exc:
        print("Protected release failed:", exc)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
