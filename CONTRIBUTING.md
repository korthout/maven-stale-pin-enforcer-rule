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

## Pull requests

- Keep PRs focused on a single change.
- Add or update tests for any behavior change.
- Make sure `mvn verify` passes before opening a PR.
- Reference the related issue in the PR description.
