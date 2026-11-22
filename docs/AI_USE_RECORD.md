# Append to docs/AI_USE_RECORD.md

## 1. In the index list at the top, add one line after "4. Independent code review (separate AI chat)":

```
5. Additional feature: flexible prerequisites (any-of policy)
```

## 2. At the very end of the file, add this entry:

```markdown
---

## Entry 5: Additional feature, flexible prerequisites (either MATH101 or MATH102, or both)
- **Prompts (verbatim, no sensitive data):**
  1. "what code need to change If I want to support for MATH101 and MATH102"
  2. "learner needs EITHER MATH101 or MATH102 or can have both"
  3. "Actually requirement is one course is supported but wanted to give flexibility to add more courses
     supported, add as additional feature - learner needs EITHER MATH101 or MATH102 or can have both"
- **AI response (summary):** Explained that "needs both" already works (the offering takes a set and the default policy
  requires all of it, so only `Main` changes). For "either or both" it proposed a new `AnyOfPrerequisitesPolicy`
  implementing the existing `EligibilityPolicy` interface, passed through the existing three-argument
  `EnrollmentService` constructor, plus a test and doc updates. It ran a small demo in its sandbox first
  (MATH101 only, MATH102 only and both were enrolled; a learner with neither got
  `Ineligible[missing=[MATH101, MATH102]]`).
- **Decisions on suggestions**
  | Item | Decision | Reason |
  |---|---|---|
  | New `AnyOfPrerequisitesPolicy` class used via the existing constructor | Accepted | No change to the service, roster or existing tests |
  | Keep all-of as the default; any-of as an optional extension | Accepted (prompt 3) | The brief asks for the stated prerequisite rule, so single-course behavior stays the required one |
  | Make any-of the default for the whole project | **Rejected** | Would change A2 and several tests for no requirement |
  | Configurable AND/OR rule engine | **Rejected** | The brief says not to build a policy engine |
  | Test extended to cover the "both courses" learner | Edited | Prompt 2 explicitly asked for "or can have both" |
- **Files informed:** `policy/AnyOfPrerequisitesPolicy.java` (new), `EdgeCaseTest.java` (+1 test), `Main.java`,
  `README.md`, `docs/DESIGN.md` (A2 and new section 9), `docs/LIMITATIONS.md`.
- **Verification:** AI sandbox demo as above. On my machine, `mvn clean verify` (22 Nov 2026, 14:49 IST) ->
  `Tests run: 187, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`. Docs updated to 187 tests.
```