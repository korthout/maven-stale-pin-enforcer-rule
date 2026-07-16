# stale-pin-enforcer

[![CI](https://github.com/korthout/maven-stale-pin-enforcer-rule/actions/workflows/ci.yml/badge.svg)](https://github.com/korthout/maven-stale-pin-enforcer-rule/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.korthout/stale-pin-enforcer)](https://central.sonatype.com/artifact/io.github.korthout/stale-pin-enforcer)

A custom [Maven Enforcer](https://maven.apache.org/enforcer/maven-enforcer-plugin/) rule that
fails the build when a `dependencyManagement` entry (a *pin*) is no longer resolved by any
dependency in the build — directly or transitively, in any module of the reactor.

## The problem

`dependencyManagement` entries are often added to fix a *transitive* version conflict.
A real-world example:

- `jnr-ffi` bumps its transitive `org.ow2.asm:asm` to `9.10.1`, while `json-smart`'s
  dependency chain still pulls `asm` at `9.7.1`.
- The conflict breaks `enforce-dependency-convergence`, so you resolve it by hand with a
  `dependencyManagement` pin: `org.ow2.asm:asm:9.10.1`.

Months later `json-smart` is removed (or stops depending on `asm`). The conflict the pin was
written for is gone — but the pin stays behind, silently. Nothing resolves to it anymore; it's
dead configuration that misleads the next reader and may suddenly (and surprisingly) apply again
if anything ever reintroduces `asm`.

The Enforcer's built-in rules don't catch this:

- [`analyze-dep-mgt`](https://maven.apache.org/plugins/maven-dependency-plugin/analyze-dep-mgt-mojo.html)
  only catches version *mismatches* against `dependencyManagement`.
- [`dependencyConvergence`](https://maven.apache.org/enforcer/enforcer-rules/dependencyConvergence.html)
  only catches convergence *conflicts*.

Neither notices a managed entry that nothing resolves to anymore. This rule closes that gap.

## Usage

Requires Java 17+ and Maven 3.9+.

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-enforcer-plugin</artifactId>
  <version>3.6.3</version>
  <dependencies>
    <dependency>
      <groupId>io.github.korthout</groupId>
      <artifactId>stale-pin-enforcer</artifactId>
      <version>0.1.0</version>
    </dependency>
  </dependencies>
  <executions>
    <execution>
      <id>enforce</id>
      <goals>
        <goal>enforce</goal>
      </goals>
      <configuration>
        <rules>
          <stalePin/>
        </rules>
      </configuration>
    </execution>
  </executions>
</plugin>
```

A stale pin fails the build with a message like:

```
Found 1 stale dependencyManagement entry in com.example:app:1.0.0:
  - org.ow2.asm:asm (pinned to 9.10.1)
No dependency in this build resolves to these coordinates (directly or transitively),
so the pins have no effect. Remove them from dependencyManagement.
```

## How it works

For the module the rule runs in, every `dependencyManagement` entry declared in that module's
own POM is matched by `groupId:artifactId` against the dependency graphs of **all** projects in
the reactor. A pin counts as used when any module resolves those coordinates, directly or
transitively — so a pin declared in a `pom`-packaged parent is justified by usage in any child
module.

Details worth knowing:

- **Inherited pins** are not re-checked in child modules; they are checked once, in the module
  that declares them.
- **Imported BOMs** (`scope=import`) are not flagged: the entries a BOM contributes are not pins
  you wrote, and the import itself is not expected to be a dependency.
- The reactor-wide usage set is computed once per build and cached, so the rule stays fast in
  large multi-module builds.

## Building

```bash
mvn verify
```

runs the unit tests, formatting check (`mvn spotless:apply` fixes violations), and the
integration tests under `src/it`, which build fixture projects against the freshly built rule.

See [CONTRIBUTING.md](CONTRIBUTING.md) for more.

## License

[Apache-2.0](LICENSE)
