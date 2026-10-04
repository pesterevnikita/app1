# Password removal and visibility follow-up

**TL;DR:** Add an eye control to every password input. Allow authenticated password removal only while unlocked. After removal, Password-only and OR locks cannot start until a new password is set. Timer-only remains an explicit choice. This plan defines intended checks; use [the roadmap](../../roadmap.md) and [phone acceptance](../../testing/phone-acceptance.md) for results.

The user approved eye controls, password removal, and clear OR/timer explanations. The user reported that the earlier password/change flow worked. This report is separate from measured acceptance.

## Behavior

- Start each field masked. Give it an independent show/hide button. Mask it again when cleared or recreated. Never reveal the stored password.
- Allow **Remove password** only while configuration is unlocked. Verify the latest current password in the same transaction as removal. Use the shared persisted retry delay. Preserve blockers, usage, protections, and remembered lock choices.
- First setup and setup after removal ask only for a new password and matching confirmation. Do not ask for an old password when none exists.
- Without a saved password, reject new Password-only and Password OR timer locks. Let the user explicitly choose Timer-only. It has no early password override. Explain this before removal, in lock setup, and in the guide. Never silently convert OR or change an active session.
- Add no maintenance password, reset, or release endpoint.

## Implementation and verification

1. Add pure removal rules and a guarded store action. Show failing tests before implementation. Test correct, wrong, and stale passwords; shared cooldown; refusal in every locked mode; retained data; fresh setup; and new-lock prerequisites.
2. Add a shared password field with local vector eye icons. Add the removal dialog, password status, and clear no-password choices/help.
3. Review the changes. Run policy/app unit tests, Android lint, and debug/release builds.
4. Upgrade without removing phone data. Test service recovery, the removal explanation, and eye behavior with disposable input. Do not submit real credential changes through automation. The user enters and removes their private password.
5. Update tracked acceptance/development documents. Publish through existing Git authentication.

Original work split: subagents handled backend/tests and read-only review. The root agent handled UI/docs, build coordination, and phone interaction. This describes that implementation, not a standing delegation requirement.

The user later kept removal unavailable during Restricted Mode. Do not implement unlock-and-remove as a new password action. The user must release the session normally before removing the password.
