# AGENTS.md

maven-stale-pin-enforcer-rule is a custom Maven Enforcer rule that flags `dependencyManagement` entries (pins) no longer referenced by any dependency in the build. It helps keep a project's `pom.xml` free of stale version pins left behind after dependencies are removed or refactored.

## Learnings

Capture learnings often: whenever a decision is non-obvious, a mistake is corrected, a convention is picked among options, or tribal knowledge surfaces. Give each learning its own heading below, sized to what it needs — a short sentence when that's enough, a full paragraph or an example when it isn't. This file loads into every session's context, so keep entries as short as they can be without losing the point.

### Commits are for git archeology

Commit titles max 72 chars, prefer <50. Explain all considerations in the body. Separate behavioral changes from structural/refactoring changes into distinct commits.

### PRs follow `.github/PULL_REQUEST_TEMPLATE.md`

Always fill in the repo's PR template structure (Description / Related issue) rather than an improvised format.

### Use javax.inject, not jakarta.inject

The rule must be annotated with `javax.inject.Named`: `sisu-maven-plugin:main-index` does not index `jakarta.inject.Named` classes, so with jakarta the jar ships without `META-INF/sisu/javax.inject.Named` and the enforcer plugin silently cannot find the rule. The invoker ITs guard this end-to-end.

### Rule sees the reactor via its own dependency collection

`MavenProject.getArtifacts()` of sibling modules is empty when the rule runs at `validate`, so StalePinRule collects each reactor project's graph itself through the injected aether `RepositorySystem` and caches the reactor-wide result in resolver `SessionData` (one collection per build).

### redundantPin sees requested versions via unmanaged, conflict-preserving graphs

RedundantPinRule collects each reactor project's graph without request-level managed dependencies and with the resolver session's dependency graph transformer removed, so no conflict resolution picks a winner and every naturally requested version stays visible. Two guards prevent false positives: pins whose coordinates appear as versionless direct dependencies in any raw (original) model are version management in active use, and pins that differ from the single requested version are kept (possibly a deliberate override, e.g. a CVE fix).

### Pins are filtered by InputLocation

Effective-model `dependencyManagement` includes inherited entries and entries flattened in from imported BOMs. StalePinRule only checks entries whose `InputLocation` source modelId is the current project, so inherited pins are checked once (in the declaring module) and BOM contents are never flagged.

### InputLocation carries line AND column, but the column points past the start tag

Maven core builds project models with location tracking on, and its model reader records line and column together — but it records them after consuming the `<dependency>` start tag, so the column points one past the tag's `>` (e.g. 19 for an element starting at column 7), not at the element. `Pins.position` shifts the column back by `"<dependency>".length()` to land on the opening `<`; the failing-pin ITs assert the exact shifted `at pom.xml:<line>:<column>` end-to-end. Failure messages still degrade gracefully — no location means no position suffix, and a column that cannot lie behind a start tag (≤ 12) is dropped rather than misreported.
### Build floor is Java 17, ceiling is deliberate none

The artifact targets Java 17/21 runtimes via `maven.compiler.release=17` (safe to cross-compile from any newer JDK), so the build must never cap the JDK. The only thing that broke newer JDKs was mockito's bundled byte-buddy: 1.17.x rejects class files beyond Java 25, and even the latest mockito still ships it, so `dependencyManagement` pins byte-buddy(+agent) 1.18.x. When a new JDK GA lands, bump the CI matrix's newest entry and, if tests fail, the byte-buddy pin.
