# Issue tracker: CNB

Issues and specs for this repo live in this repo's CNB Issues: `cnb.cool/BirthCat-Dev/krema-f-ext`.
Operate them with the `cnb` CLI, following the conventions in the `cnb-api` skill.

## Conventions

- **Repo slug**: `BirthCat-Dev/krema-f-ext`, inferred from `git remote -v`. `create-issue` / `list-issues` require `--repo` explicitly; the issue number is auto-detected from the environment and overridden with `--number <n>`.
- **Create an issue**: `cnb issues create-issue --repo BirthCat-Dev/krema-f-ext --title "..." --body-file <path>`. Use `--body-file` for anything multi-line (no shell quoting risk); `--body` for one-liners. Optional `--labels` (repeatable, max 10), `--assignees` (repeatable, max 8), `--priority` (`-2P`…`P3`).
- **Read an issue**: `cnb issues get`. Add `--verbose` for the full raw payload.
- **List issues**: `cnb issues list-issues --repo BirthCat-Dev/krema-f-ext --state open --labels a,b --labels-operator contains_all`. Supports `--keyword`, `--assignees` (`-` means unassigned), `--priority`; `--page-size` max 100.
- **Comment**: `cnb issues comment --body '...'` — single quotes for multi-line bodies.
- **Labels**: `cnb issues add-labels --labels bug --labels feature`, `cnb issues list-labels`, `cnb issues put-issue-labels` (replaces the whole set). Labels must exist at repo level; create missing ones with `cnb repo-labels post-label`.
- **Close / reopen**: `cnb issues close` / `cnb issues open`.
- **Assign**: `cnb issues add-assignees --assignees <username>`; the current handle comes from `cnb users get-user-info`.
- **Attachments**: images and files in bodies and comments must be fetched via `cnb issues get-imgs --img-path <tail>` / `cnb issues get-files --file-path <tail>`; the CLI carries the auth, hand-written tokens get a 400. The tail must include the leading numeric id segment.

## When a skill says "publish to the issue tracker"

Create a CNB issue as in **Create an issue** above.

## When a skill says "fetch the relevant ticket"

`cnb issues get`, passing `--number <n>` when the ticket isn't the environment's current one.

## Pull requests as a triage surface

**PRs as a request surface: no.** _(Set to `yes` if this repo treats external PRs as feature requests.)_

When set to `yes`, PRs run through the same states as issues using the `cnb pulls` equivalents: `cnb pulls get`, `cnb pulls list-files`, `cnb pulls comment`, `cnb pulls add-labels`, `cnb pulls check-status`.

CNB gives issues and PRs separate number spaces, so `#12` is ambiguous without context — always say "issue #12" or "PR #12".

## Wayfinding operations

Used by `/wayfinder`. The **map** is a single issue with **child** issues as tickets.

- **Map**: one issue labelled `wayfinder:map`, holding the Notes / Decisions-so-far / Fog body.
- **Child ticket**: an issue whose body starts with `Part of #<map>` — CNB has no sub-issues. Label it `wayfinder:<type>` (`research`/`prototype`/`grilling`/`task`).
- **Blocking**: a `Blocked by: #<n>, #<n>` line at the top of the child body — CNB has no native issue dependencies. A ticket is unblocked when every issue it lists is closed.
- **Frontier query**: list open children of the map, drop any with an open blocker or an assignee; lowest number wins.
- **Claim**: `cnb issues add-assignees --assignees <username>`, the session's first write.
- **Resolve**: `cnb issues comment --body '...'` with the answer, then `cnb issues close`, then append a context pointer (gist + link) to the map's Decisions-so-far.
