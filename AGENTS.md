# AGENTS.md

maven-stale-pin-enforcer-rule is a custom Maven Enforcer rule that flags `dependencyManagement` entries (pins) no longer referenced by any dependency in the build. It helps keep a project's `pom.xml` free of stale version pins left behind after dependencies are removed or refactored.

## Learnings

Capture learnings often: whenever a decision is non-obvious, a mistake is corrected, a convention is picked among options, or tribal knowledge surfaces. Give each learning its own heading below, sized to what it needs — a short sentence when that's enough, a full paragraph or an example when it isn't. This file loads into every session's context, so keep entries as short as they can be without losing the point.

### Commits are for git archeology

Commit titles max 72 chars, prefer <50. Explain all considerations in the body. Separate behavioral changes from structural/refactoring changes into distinct commits.
