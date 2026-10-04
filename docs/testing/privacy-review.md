# Published-data review

**TL;DR:** Published branch history is cleaned. Organization proxy/local machine details were removed from four old document versions. Seven commits now use public GitHub no-reply emails. Both branches and this checkout are synchronized. No checked token or private-key pattern matched Git history. Other checkouts must use the rewritten branches.

## Scope and results

Review date: 2026-10-04. The audit checked tracked files, all eight reachable published commits at the starting point, branch tips, tracked filenames and remote configuration.

- The only disclosed setup values were in `docs/development.md`: an organization proxy address/configuration and local installation/workspace paths.
- Those values appeared in four published versions of that document.
- Seven commits used corporate author and committer email addresses. Two were also ancestors of `main`. Published history replaces these emails with the public GitHub no-reply address. New commits use that public identity.
- Checked patterns found no GitHub token, cloud key, private key, credential URL or literal secret assignment. This is a pattern audit, not a guarantee that every possible secret format was detected.
- Tracked history contained no private APK, log, screenshot, signing key or `local.properties` file.
- Device-identifier checks found prohibition text, not an actual private device identifier.
- The configured remote URL contained no embedded credential.

Current documentation removes organization setup values and personal machine paths. Machine-specific build configuration belongs in untracked local notes. Public docs use installation placeholders.

## Published history cleanup

The user approved the force-push on 2026-10-04. The verified cleanup was published to `feat/focusgate` and `main` in one atomic push. Exact expected-tip leases prevented overwriting unexpected remote changes.

Document changes affect only `docs/development.md` in the four affected historical commits. All other file blobs and modes stay identical. Corporate author/committer email addresses are replaced with `pesterevnikita@users.noreply.github.com`. Names, dates and messages are preserved. The initial signed commit stays identical. The `main` file contents stay identical, but its two email-bearing commits have new IDs.

The cleanup tips were `11dc2e1` for `feat/focusgate` and `8a6ee85` for `main`. Remote refs were verified after publication. Local branches and tracking refs were synchronized without changing working files. Later documentation commits can advance the feature tip.

After publication, a check scanned ten reachable commits and 49 unique Markdown blobs. It found zero corporate email matches and zero private setup matches. The `main` tree matched its original contents exactly.

Other checkouts must update to the rewritten branches. Save local work first. Fetch the current remote history. Do not merge or push the old commits back into these branches.

Removing a value from the current file does not remove it from older commits. Rewriting branch history also cannot promise removal from another person's clone, fork or GitHub cache. Do not claim all published copies have been erased.

The earlier token pasted in chat was not found in Git. Do not use or reproduce it. The user should revoke it if they have not already done so.
