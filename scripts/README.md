# Implemented protected-release candidate pipeline (development)

MONIQUE now includes a real candidate pipeline that uses OreoObfuscator's headless CLI, which supports the command:

~~~sh
oreo protect INPUT.jar OUTPUT.jar --config PROFILE.yml --seed 1729
~~~

The command is called by scripts/protect_release.py. It transforms **only** compiled MONIQUE classes/resources from Maven target/classes, then reconstructs the executable Spring Boot JAR, replacing BOOT-INF/classes and preserving the original Boot loader and nested dependencies byte-for-byte. The script refuses to ship a fake "protected" artifact without a renamed Java class, a malformed Spring entry point, lost app resources, or Java 22+ classfiles.

Prerequisites: Maven/Java 21, Python 3.10+, authorized installed OreoObfuscator CLI (its own build currently uses JDK 25), and the obfuscator's own valid license entitlement.

~~~sh
export OREO_CLI=/path/to/oreo-cli/build/install/oreo-cli/bin/oreo
export OREO_LICENSE=YOUR_OREO_TOOL_KEY
bash scripts/build_protected.sh
~~~

The conservative packaging/oreo-monique-safe.yml profile preserves Spring-managed classes, REST/DTO data types, entry points and security classes; only internal, non-Spring classes such as AddressValidator can currently be renamed. It deliberately disables experimental virtualization, control flow, string/resource/number transforms until regression tests validate them.

Output: backend/target/monique-backend-protected.jar. This is **UNSIGNED**, not a production release. The obfuscation mapping is stored in backend/target/private-mappings and must never enter a distributable installer. All target artifacts are Git-ignored.

Unit tests that do not need the OreoObfuscator private tool:

~~~sh
python3 -m unittest discover -s scripts/tests -v
~~~

The GitHub Actions normal CI now runs the Java unit suite and Python CLI/packaging tests; it does **not** attempt obfuscation without a private authorized OreoObfuscator installation or license. A protected production release must also pass Java 21 Spring startup with PostgreSQL, security/REST/chat smoke tests, signed artifact verification and installation lifecycle tests. Those end-to-end checks are not yet satisfied.

See docs/RELEASE-PROTECTION.md and the current CLI at cli/monique.py.
