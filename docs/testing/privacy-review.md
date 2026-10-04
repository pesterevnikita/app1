# Published-data review

**TL;DR:** Current docs use portable setup instructions. The audit found organization proxy/local machine details in four old document versions and corporate email addresses in seven commits. No checked token or private-key pattern matched Git history. A local cleanup is prepared; publishing it requires a branch rewrite. This file contains no private values.

## Scope and results

Review date: 2026-10-04. The audit checked tracked files, all eight reachable published commits at the starting point, branch tips, tracked filenames and remote configuration.

- The only disclosed setup values were in `docs/development.md`: an organization proxy address/configuration and local installation/workspace paths.
- Those values appeared in four published versions of that document.
- Seven feature commits used corporate author and committer email addresses. Prepared history replaces them with the public GitHub no-reply address. New commits use that public identity.
- Checked patterns found no GitHub token, cloud key, private key, credential URL or literal secret assignment. This is a pattern audit, not a guarantee that every possible secret format was detected.
- Tracked history contained no private APK, log, screenshot, signing key or `local.properties` file.
- Device-identifier checks found prohibition text, not an actual private device identifier.
- The configured remote URL contained no embedded credential.

Current documentation removes organization setup values and personal machine paths. Machine-specific build configuration belongs in untracked local notes. Public docs use installation placeholders.

## Prepared history cleanup

An ignored local review repository contains the proposed cleanup. Document changes affect only `docs/development.md` in the four affected historical commits. All other file blobs and modes stay identical. Corporate author/committer email addresses are replaced with `pesterevnikita@users.noreply.github.com`. Names, dates and messages are preserved. The initial signed commit stays identical. The `main` file contents stay identical, but its two email-bearing commits receive new IDs.

Publishing this cleanup changes commit IDs on `feat/focusgate` and `main`. It requires a force-push with exact expected remote tips. Other checkouts must update to the rewritten branches. No force-push has been performed yet.

Removing a value from the current file does not remove it from older commits. Rewriting branch history also cannot promise removal from another person's clone, fork or GitHub cache. Do not claim all published copies have been erased.

The earlier token pasted in chat was not found in Git. Do not use or reproduce it. The user should revoke it if they have not already done so.
