# Public source privacy and security review

## Current public-source status — November 27, 2026

GitHub's repository API now reports `private=false`, `visibility=public` for `Aqyn97/Belsen-AI`. Remote `main`, checked through both the API and Git, matches the clean starting checkout `62d2ad81a385064148b756a9b99d187159b35612`. No visibility or permission setting was changed by this review. The prior November 22 clients returned 404; that historical observation no longer describes current access.

**Public access is already possible, but showcase source links still require a separate owner privacy decision.** Keep `BELSEN_SOURCE_PUBLIC` unset. The owner previously accepted the low-risk historical workstation-path disclosure and prohibited history rewriting; this review preserves both decisions. No application launch link or verified public application URL exists.

The fresh local pass inspected all **270 tracked paths**, **18 reachable commits** and **451 distinct committed blobs**, using actual commit roots rather than tree-only review refs. Recognized token/private-key/credential-URL/dump/artifact checks found no confirmed secret. Home-path matches remain confined to the previously documented historical versions of `docs/AI_COACH_DESIGN.md`; current references remain portable. Contributor email domains are GitHub noreply addresses. Both unchanged screenshots retain fictional fixture data and no text/EXIF chunks. New working files receive the same scoped publication guard before release.

The now-accessible GitHub API listed one branch (`main`), zero Actions runs, zero releases and zero pull requests. The repository reports zero open issues. No new public artifact exposure was identified through those endpoints. This is not a review of every closed issue, comment, server-retained object or future upload. No repository-side security setting was changed.

**New public-source risk assessment:** no new confirmed credential or private dataset finding. Public readers can already inspect the accepted historical username/path, ordinary contributor identity, deliberately generic private-network layouts, operational role names and secret-file paths. These are architecture templates, not deployed addresses or secret values. The owner must separately approve promoting these public source links; a successful HTTP response is not privacy approval. No history rewrite is proposed or performed.

## Prior audit — November 22, 2026

The following records the original baseline review, when the owner instructed that the repository remain private. Current verified visibility is public as described above; no current PRIVATE or 404 claim should be inferred from that earlier decision. The configured repository remains `https://github.com/Aqyn97/Belsen-AI`.

## Scope and method

The pre-release audit reviewed 243 baseline tracked paths, all 17 commits reachable through commit refs and their 418 distinct committed blobs, the preparation worktree paths (33 in the final release scope), commit messages and contributor metadata. Retained local review snapshots and 77 additional unreachable blobs were also inspected. Tree-only review snapshots were kept separate from committed history when counting the 418 blobs. The report uses local Git object reads, credential-pattern and assignment-candidate checks, file/signature checks, author metadata review, infrastructure inspection and visual inspection of both public screenshots. The final scope/counts are recorded in FREE_HOSTING_VERIFICATION.md. No matched credential values or complete personal home path are reproduced here.

Checks cover recognizable provider tokens, embedded private keys, password-bearing URLs, credential assignments, cloud identifiers, personal contacts/home paths, non-example public IP addresses, PostgreSQL/SQLite dump signatures, archive/build outputs and sensitive filenames. Synthetic fixture values and generated-key code were reviewed as candidates rather than assumed to be real secrets. This is a bounded local review, not a certification from a specialist secret-scanning service.

## Findings

### Low historical disclosure — accepted by owner; current references corrected

`docs/AI_COACH_DESIGN.md:280` and `:356` contained an absolute local Docker socket path at the audit baseline. It disclosed a workstation username and local filesystem layout, not a password, token or remotely accessible socket endpoint. The owner accepted this documented low-risk historical disclosure on November 22, 2026 and approved a forward-only correction. Both current references now use `$HOME/.docker/run/docker.sock`.

`git blame` confirms that line 280 originated in B1 and line 356 in B2. The complete list of reachable commits containing these paths is:

| Commit | File and line numbers in that commit |
| --- | --- |
| `a69b5d0e649eb909de3a03ffabd6b0eff98e3082` | `docs/AI_COACH_DESIGN.md:280` — first path introduced |
| `7823b6956db70c555442db0ad5b68065e1072edf` | `docs/AI_COACH_DESIGN.md:280,356` — second path introduced |
| `6f0981841274a3bfaac512afe0c7ee29cb3454ac` | `docs/AI_COACH_DESIGN.md:280,356` — retained |
| `ecb26acea663017b226a9b913625e31233004211` | `docs/AI_COACH_DESIGN.md:280,356` — retained |
| `1322f44358d9961b0d1e84380646f05e2cdd399b` | `docs/AI_COACH_DESIGN.md:280,356` — reviewed starting baseline |

No other committed file containing a workstation-home path was found by the scoped baseline scan. No original path value is copied into this report.

**Owner decision and completed remediation:** the documented historical disclosure is accepted. The two current references were replaced with `$HOME/.docker/run/docker.sock`, preserving the troubleshooting evidence without a workstation username. The historical commits listed above remain intact. The owner explicitly prohibited history rewriting and, at that time, instructed that the repository remain private. Current observed public visibility is recorded above. No history rewrite, force-push, visibility change or credential rotation was performed. A filesystem-path disclosure alone is not evidence of a compromised credential.

This completed the approved forward-only correction; historical disclosure remains accepted. It does not authorize promoting source links: `BELSEN_SOURCE_PUBLIC` remains unset and the static showcase's GitHub links stay hidden.

### Reviewed candidates without a confirmed secret finding

- Password/token-like assignments occur in local authentication/browser fixtures, generated test keystores or form autocomplete labels. They are not supplied production credentials or live-provider keys.
- The password-bearing URL samples in `showcase/build.test.mjs` deliberately test rejection. They are fictional and are not emitted by the default static build.
- `PostgresTlsIT` contains a PEM delimiter and runtime key generation; it does not embed a real private key. Test keys/credentials are temporary and excluded from the production JAR/static upload.
- The two PNGs are genuine local Java/PostgreSQL test captures. Visible names, identifiers and quantities are fictional fixture data. Visual and file-metadata inspection found no private user account, password or provider key.
- The 17 commit messages produced no sensitive-pattern finding. Git author/committer contacts use a GitHub `users.noreply.github.com` address; no personal email domain was found in the inspected commit metadata. This still identifies the GitHub contributor normally.
- Infrastructure files disclose private Docker subnet layouts, service/resource settings, secret **paths** and role names. These are deliberate local templates, not real secret values, Oracle identifiers or live public server addresses. No confirmed real public infrastructure IP or database dump was found. The owner should knowingly accept publication of this architecture/configuration information.

No confirmed provider credential, live password, embedded private key, database dump, committed runtime environment, generated JAR/dependency tree/build output or private account dataset was found in the inspected content. This conclusion is limited to the checks and local objects available; it is not an unconditional “safe to publish” statement.

## Remaining limits and publication decision

At the prior audit, the connected repository was inaccessible, so remote-only branches/refs, issues, PR discussions, Actions logs/artifacts, release attachments and server-retained objects were not reviewed. The limited fresh endpoint inventory above supersedes that access blocker, not the limits on a complete server-side audit. Local unreachable/reflog objects are not normally transmitted by a standard branch push; they were inspected as additional local evidence without deleting or rewriting them. Ignored workstation secrets were not read or published. There was no external authenticated secret scan or complete container-OS vulnerability scan.

**Current disposition:** the owner accepted the historical workstation-path disclosure, and current references are portable. No confirmed secret finding was identified in the inspected scope. GitHub now reports public visibility; the agent has not changed it. A separate privacy decision is still required before enabling showcase source links. Repository-side artifacts/settings and future changes require continued review. Any genuine credential discovered later would require revocation/rotation; none was confirmed here.

Do not turn on `BELSEN_SOURCE_PUBLIC` merely because a URL returns 200. It requires both the privacy decision and actual public access. No authentication/session shortcut, shared demonstration password or source-visibility change is needed for the current Cloudflare showcase release.
