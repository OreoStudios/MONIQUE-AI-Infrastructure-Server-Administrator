"""Test protected Spring Boot packaging without private OreoObfuscator dependencies."""
import importlib.util
from pathlib import Path
import struct
import sys
import tempfile
import unittest
from zipfile import ZipFile, ZIP_STORED

source = Path(__file__).resolve().parents[1] / "protect_release.py"
spec = importlib.util.spec_from_file_location("protect_release", source)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def klass(major=65):
    return b"\xca\xfe\xba\xbe" + struct.pack(">HH", 0, major) + b"\x00\x01"


class PackagingTests(unittest.TestCase):
    def setUp(self):
        self.context = tempfile.TemporaryDirectory()
        self.addCleanup(self.context.cleanup)
        self.root = Path(self.context.name)
        classes = self.root / "classes"
        classes.mkdir()
        for name in module.REQUIRED_CLASSES + module.REQUIRED_RESOURCES:
            target = classes / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(klass() if name.endswith(".class") else b"demo resource")
        utility = classes / "fr/oreostudios/monique/pods/AddressValidator.class"
        utility.write_bytes(klass())
        self.input = self.root / "input.jar"
        self.original_names = module.make_input_jar(classes, self.input)
        self.boot = self.root / "spring-boot.jar"
        with ZipFile(self.boot, "w") as out:
            out.writestr("META-INF/MANIFEST.MF",
                         "Main-Class: org.springframework.boot.loader.launch.JarLauncher\n")
            out.writestr("BOOT-INF/lib/driver.jar", b"nested-jar", compress_type=ZIP_STORED)
            with ZipFile(self.input) as original:
                for name in original.namelist():
                    out.writestr(module.APP_PREFIX + name, original.read(name))
        self.obf = self.root / "obfuscated.jar"
        with ZipFile(self.obf, "w") as out:
            with ZipFile(self.input) as original:
                for name in original.namelist():
                    renamed = name.replace("AddressValidator.class", "Q.class")
                    out.writestr(renamed, original.read(name))
        self.output = self.root / "protected.jar"

    def test_packages_classes_without_touching_nested_jars(self):
        module.repackage(self.boot, self.obf, self.output, self.original_names)
        with ZipFile(self.output) as out:
            self.assertEqual(out.read("BOOT-INF/lib/driver.jar"), b"nested-jar")
            self.assertEqual(out.getinfo("BOOT-INF/lib/driver.jar").compress_type, ZIP_STORED)
            self.assertIn("BOOT-INF/classes/fr/oreostudios/monique/pods/Q.class",
                          out.namelist())
            self.assertNotIn("BOOT-INF/classes/fr/oreostudios/monique/pods/AddressValidator.class",
                             out.namelist())
            self.assertIn("BOOT-INF/classes/schema.sql", out.namelist())

    def test_refuse_fake_unchanged_obfuscation(self):
        with self.assertRaises(module.ProtectionError):
            module.repackage(self.boot, self.input, self.output, self.original_names)

    def test_refuse_java_22_classfiles(self):
        with ZipFile(self.obf, "a") as out:
            out.writestr("bad.class", klass(66))
        with self.assertRaises(module.ProtectionError):
            module.repackage(self.boot, self.obf, self.output, self.original_names)

    def test_refuse_missing_spring_entrypoint(self):
        wrong = self.root / "wrong.jar"
        with ZipFile(wrong, "w") as out:
            out.writestr("unrelated.class", klass())
        with self.assertRaises(module.ProtectionError):
            module.repackage(self.boot, wrong, self.output, self.original_names)

    def test_reject_zip_slip(self):
        for name in ("../outside.class", "/absolute.class", "a\\b.class"):
            with self.subTest(name=name), self.assertRaises(module.ProtectionError):
                module.check_name(name)


if __name__ == "__main__":
    unittest.main()
