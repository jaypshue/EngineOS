# EngineOS Project Rules

## General
- Prefer small, focused changes.
- Do not modify files outside the scope of the approved proposal.
- Always preserve existing public APIs unless the request explicitly requires a change.

## Code style
- Follow existing formatting and naming in the file you are editing.
- Use Java 17+ features where they improve clarity.
- Add or update unit tests for any non-trivial logic change.

## Safety
- Never commit secrets or credentials.
- Do not disable existing tests to make a change pass.
- If a change would affect the non-modifying / investigation boundary, stop and note it.

## Verification
- After implementation, prefer running the relevant Maven tests.
- Surface any failing tests clearly rather than suppressing them.