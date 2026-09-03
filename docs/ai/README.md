# AI-assisted development history

The assignment allows the use of AI and agentic development on the condition that the chat history and every
file handed to the agent are submitted in a readable form. This folder is that submission.

## Tool

The project was developed with **Claude Code** (Anthropic's agentic coding CLI) driven by the author. The agent
wrote code, tests and documentation; the author defined the architecture, reviewed every step, decided the open
questions and made the commits.

## Workflow

1. **Architecture first.** Before any code was written, the author and the agent produced
   [`docs/ARCHITECTURE.md`](../ARCHITECTURE.md): data model, layers, package structure, endpoint table,
   configuration, test strategy, a step-by-step implementation plan (section 8) and a list of open questions
   with the decisions taken (section 9). This document is the binding specification for every later step.
2. **Rules for agents.** [`CLAUDE.md`](../../CLAUDE.md) in the repository root holds the hard rules
   (no Spring Data, layer boundaries, soft delete, 90 % coverage, `/api` prefix, RFC 7807 errors) and the
   coding conventions. Claude Code loads it automatically at the start of every session.
3. **Per-step loop.** Each row of the implementation plan was delivered the same way:
   - an *implementer* agent received the step description and implemented code **and** tests;
   - an independent *reviewer* agent checked the result against the specification and the rules;
   - the main agent (the session the author talks to) applied the review fixes;
   - `./mvnw verify` had to pass (compilation, unit and integration tests, ArchUnit rules, JaCoCo 90 % gate);
   - the main agent committed the step after the author's review (one commit per step, see `git log`).
4. **Final polish.** springdoc annotations, README, CI workflow, UML diagrams and this folder.

## Files given to the agents

| File | Purpose |
|------|---------|
| [`CLAUDE.md`](../../CLAUDE.md) | project rules and conventions, loaded automatically by Claude Code |
| [`docs/ARCHITECTURE.md`](../ARCHITECTURE.md) | binding design specification and implementation plan |
| [`docs/zadanie.md`](../zadanie.md) | the assignment (text transcript of the original `zadanie.docx`) |

## Chat history

The exported conversations with the agent are added to this folder by the author at submission time as Markdown
files named `session-*.md` (one file per Claude Code session, in chronological order), exported from Claude Code
without edits. Until they are added, this README and `git log` are the only record.
