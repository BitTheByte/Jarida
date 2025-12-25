# Repository Guidelines

## Project Structure & Module Organization
- `src/main/java/com/jarida/jadxfrida/` holds the plugin code, split by domain (`ui`, `frida`, `util`, `model`).
- `src/main/resources/META-INF/services/` registers the Jadx plugin entry point.
- `assets/` contains documentation media (for example, `assets/showcase.gif`).
- `pom.xml` defines Maven build settings and dependencies.

## Build, Test, and Development Commands
- `mvn -DskipTests package` builds the plugin jar into `target/jarida-<version>.jar`.
- `mvn clean package` performs a clean build (use `-DskipTests` if you are not running tests).
- There is no standalone runtime command; install the jar into Jadx GUI to run the plugin.

## Coding Style & Naming Conventions
- Java 8 source/target is required (see `pom.xml`).
- Indentation is 4 spaces, braces on the same line, and standard Java camelCase naming.
- Keep new classes in the existing package layout (e.g., UI classes under `ui`, Frida logic under `frida`).

## Testing Guidelines
- No automated tests are currently configured (no `src/test/java` or test dependencies).
- If you add tests, place them under `src/test/java`, add the needed dependencies to `pom.xml`, and run `mvn test`.

## Commit & Pull Request Guidelines
- Commit messages are short and imperative; some use a Conventional Commit prefix like `chore:`.
- For pull requests, include a brief summary, manual test notes, and screenshots for UI changes.

## Runtime Requirements
- Jadx GUI 1.5.x, Python with `frida` and `frida-tools`, `adb` on PATH, and a running `frida-server` are required for end-to-end use.
