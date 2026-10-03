# Password removal and visibility follow-up

User-authorized scope: add eye controls to password inputs; allow removing a saved password by verifying it; explain the effect on Password OR timer. Existing password/change behavior was reported working by the user.

## Behavior

- Each input starts hidden and has its own show/hide button. Clearing or recreating a field returns it to hidden. Never reveal the stored password.
- Remove password is available only while configuration is unlocked. It verifies the current credential inside the same transaction as removal and shares the persisted retry throttle. Preserve blockers, usage, protections and remembered lock choices.
- First setup and setup after removal ask only for a new password and matching confirmation.
- No saved password means no new Password-only/OR lock. Timer-only stays available through explicit selection, with no early password release. Explain this in the removal dialog, lock setup, and guide. Never convert OR silently or mutate an active lock.
- No maintenance password, reset or release endpoint.

## Implementation and verification

1. Backend/tests: add pure removal rules and guarded store action; demonstrate failing removal tests before implementation, then verify correct/wrong/stale password, shared cooldown, all locked modes, data retention, fresh setup and new-lock prerequisites.
2. UI: shared password field with local vector eyes; removal dialog and status; clear no-password release choices and help text.
3. Review and run policy/app unit tests, Android lint, debug/release assembly.
4. Upgrade the connected phone without data removal. Verify service recovery, removal explanation and eye behavior using a disposable input without submitting real credential changes. Actual password entry/removal remains with the user.
5. Update durable acceptance/development docs and publish through existing Git authentication.

The backend/test task and read-only code review are delegated; the root agent owns UI/docs, build coordination and phone interaction.
