# Contributing

This file is the team's version-control policy. It covers how we branch,
commit, merge and release.

## 1. Trunk-based development

- `main` is the trunk. It must always build and pass CI.
- Nobody pushes to `main` directly. Every change goes through a pull request.
- Work happens on **short-lived branches** cut from the latest `main`, merged
  within a few days. Small pull requests are preferred over large ones.

### Branch names

| Prefix | Use for | Example |
| --- | --- | --- |
| `feat/` | a new feature | `feat/booking-aggregate` |
| `fix/` | a bug fix | `fix/weka-path` |
| `ci/` | pipeline changes | `ci/docker-delivery` |
| `docs/` | documentation only | `docs/context-map` |
| `chore/` | tooling, cleanup | `chore/remove-node-modules` |

## 2. Rebasing policy

We keep a **linear history**: no merge commits on `main`.

- Before opening a pull request, and whenever `main` moves, **rebase** your
  branch onto `main`. Do not merge `main` into your branch.

  ```bash
  git fetch origin
  git rebase origin/main
  # fix any conflicts, then: git add <files> && git rebase --continue
  git push --force-with-lease
  ```

- Only force-push **your own** branch, and always with `--force-with-lease`.
  Never force-push `main`.
- Pull requests are merged with **Rebase and merge**, or **Squash and merge**
  for branches with many small commits. **Create a merge commit** is disabled.
- GitHub enforces this: `main` has "Require linear history" turned on.

## 3. Commit messages

We use [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/):

```
<type>(<optional scope>): <short summary in the imperative>
```

Types: `feat`, `fix`, `docs`, `test`, `refactor`, `ci`, `build`, `chore`.

Examples:

```
feat(booking): add ServiceCenter aggregate with slot calendar
fix(ml): locate weka.jar from the parent folder
ci: run pipelines on release tags
```

A breaking change adds `!` after the type (`feat!: ...`) or a
`BREAKING CHANGE:` line in the body.

## 4. Pull requests

A pull request can be merged when:

1. all CircleCI checks are green;
2. one other team member has approved it;
3. the branch is rebased on the current `main`;
4. `CHANGELOG.md` has a line under `## [Unreleased]` for user-visible changes.

## 5. Versioning

We use [Semantic Versioning](https://semver.org/) `MAJOR.MINOR.PATCH`:

| Change | Bump | Example |
| --- | --- | --- |
| Breaking change to an API, message format or data file | MAJOR | 0.4.2 to 1.0.0 |
| New feature, backwards compatible | MINOR | 0.4.2 to 0.5.0 |
| Bug fix only | PATCH | 0.4.2 to 0.4.3 |

While the version is `0.x.y` the project is in initial development and a
MINOR bump may contain breaking changes.

There is **one version for the whole repository**, stored in `VERSION`.
`build.gradle` and every `package.json` must carry the same number;
`scripts/check-version.sh` checks this in CI.

### Releasing

1. On a `chore/release-x.y.z` branch:
   - set the new number in `VERSION`, `Multi-Agent-System/build.gradle` and each `package.json`;
   - in `CHANGELOG.md`, rename `## [Unreleased]` to `## [x.y.z] - YYYY-MM-DD` and add a new empty `## [Unreleased]` above it;
   - run `./scripts/check-version.sh`.
2. Open a pull request and merge it when CI is green.
3. Tag the merged commit on `main` and push the tag:

   ```bash
   git checkout main && git pull --rebase
   git tag -a vx.y.z -m "Release x.y.z"
   git push origin vx.y.z
   ```

4. CircleCI runs the full pipeline on the tag and checks that the tag matches `VERSION`.

## 6. Running the checks locally

Run exactly what CI runs on a pull request before you push:

```bash
make install   # once
make check     # version check + lint + tests on all three platforms
make help      # all targets
```

To run the whole system in Docker: `make up` (stop with `make down`).
