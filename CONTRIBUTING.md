# Contributing

Thanks for considering a contribution to maven-stale-pin-enforcer-rule!

## Building

This is a Maven project. Build and run the unit tests with:

```bash
mvn verify
```

## Running the invoker tests

Integration tests live under `src/it` and are executed with the
`maven-invoker-plugin`. They run automatically as part of `mvn verify`.

## Releasing

Releases are cut by pushing a version tag; there are no release commits.

```bash
git tag v0.1.0
git push origin v0.1.0
```

The [release workflow](.github/workflows/release.yml) then sets the POM version
from the tag (`v0.1.0` releases `0.1.0`), builds and tests, and publishes the
signed artifacts to Maven Central. The POM on `main` always stays at a
`-SNAPSHOT` version; after a release, bump it to the next `-SNAPSHOT` so
snapshot builds sort after the release.

## Pull requests

- Keep PRs focused on a single change.
- Add or update tests for any behavior change.
- Make sure `mvn verify` passes before opening a PR.
- Reference the related issue in the PR description.
