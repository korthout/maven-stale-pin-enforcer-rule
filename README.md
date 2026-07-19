# stale-pin-enforcer

[![CI](https://github.com/korthout/maven-stale-pin-enforcer-rule/actions/workflows/ci.yml/badge.svg)](https://github.com/korthout/maven-stale-pin-enforcer-rule/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.korthout/stale-pin-enforcer)](https://central.sonatype.com/artifact/io.github.korthout/stale-pin-enforcer)

Custom [Maven Enforcer](https://maven.apache.org/enforcer/maven-enforcer-plugin/) rules that keep
`dependencyManagement` free of leftover version pins:

- **`stalePin`** fails the build when a pin is no longer resolved by any dependency in the build —
  directly or transitively, in any module of the reactor.
- **`redundantPin`** fails the build when a pin no longer settles a version conflict: every
  dependency already requests exactly the pinned version, so the pin changes nothing.

## The problem

`dependencyManagement` entries are often added to fix a *transitive* version conflict.
A real-world example:

- `jnr-ffi` bumps its transitive `org.ow2.asm:asm` to `9.10.1`, while `json-smart`'s
  dependency chain still pulls `asm` at `9.7.1`.
- The conflict breaks `enforce-dependency-convergence`, so you resolve it by hand with a
  `dependencyManagement` pin: `org.ow2.asm:asm:9.10.1`.

Months later the conflict the pin was written for is gone — but the pin stays behind, silently.
That happens in two ways:

- `json-smart` is removed (or stops depending on `asm`) and nothing else uses `asm` either:
  the pin is **stale**. It's dead configuration that misleads the next reader and may suddenly
  (and surprisingly) apply again if anything ever reintroduces `asm`.
- `json-smart` catches up and now also requests `asm` `9.10.1` (or one of the two dependents
  disappears while the other already requests the pinned version): the pin is **redundant**.
  Resolution would produce exactly the same result without it.

The Enforcer's built-in rules don't catch either case:

- [`analyze-dep-mgt`](https://maven.apache.org/plugins/maven-dependency-plugin/analyze-dep-mgt-mojo.html)
  only catches version *mismatches* against `dependencyManagement`.
- [`dependencyConvergence`](https://maven.apache.org/enforcer/enforcer-rules/dependencyConvergence.html)
  only catches convergence *conflicts*.

Neither notices a managed entry that no longer does anything. These rules close that gap.

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
          <redundantPin/>
        </rules>
      </configuration>
    </execution>
  </executions>
</plugin>
```

The rules are complementary — use them together: `stalePin` reports pins nothing depends on,
`redundantPin` reports pins that are still resolved but no longer make a difference.

A stale pin fails the build with a message like:

```
Found 1 stale dependencyManagement entry in com.example:app:1.0.0:
  - org.ow2.asm:asm (pinned to 9.10.1) at pom.xml:42:7
No dependency in this build resolves to these coordinates (directly or transitively),
so the pins have no effect. Remove them from dependencyManagement.
```

and a redundant pin with:

```
Found 1 redundant dependencyManagement entry in com.example:app:1.0.0:
  - org.ow2.asm:asm (pinned to 9.10.1) at pom.xml:42:7
Every dependency on these coordinates already requests the pinned version,
so there is no version conflict left to settle. Remove them from dependencyManagement.
```

## How it works

For the module a rule runs in, every `dependencyManagement` entry declared in that module's
own POM is checked against the dependency graphs of **all** projects in the reactor.

**`stalePin`** matches each pin's `groupId:artifactId` against every node of the graphs as
Maven builds them. A pin counts as used when any module resolves those coordinates, directly
or transitively — so a pin declared in a `pom`-packaged parent is justified by usage in any
child module.

**`redundantPin`** collects the graphs *without* applying `dependencyManagement` and without
conflict resolution, so it sees every version the dependencies naturally request. A pin is
redundant when exactly one version is requested across the whole reactor and it equals the
pinned version. The following intentionally pass:

- **Conflicting requests** (two or more versions requested): the pin still settles the
  conflict.
- **Overrides** (the single requested version differs from the pinned one): the pin still
  forces a different version, which may be deliberate — e.g. enforcing a newer release to
  avoid a vulnerability.
- **Version management**: a pin that supplies the version of a direct dependency declared
  without one (the classic parent-manages-the-version pattern) is in active use.
- **Unused pins** (nothing requests the coordinates at all): that's `stalePin`'s finding.

Details worth knowing, for both rules:

- **Inherited pins** are not re-checked in child modules; they are checked once, in the module
  that declares them.
- **Imported BOMs** (`scope=import`) are not flagged: the entries a BOM contributes are not pins
  you wrote, and the import itself is not expected to be a dependency.
- The reactor-wide usage data is computed once per build and cached, so the rules stay fast in
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
