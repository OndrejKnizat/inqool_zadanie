# AI-assisted development record

The assignment allows AI/agentic development on the condition that the communication history with the AI tool
and every file given to the agent are handed in. This folder is that record.

## Tool and workflow

The whole project was built in one **Claude Code** session (Anthropic's agentic CLI, running in a remote
container on `claude.ai/code`), driven by the author in Slovak. The author never wrote code by hand; every
change was made by the agent and reviewed by the author.

1. **Architecture first.** The author uploaded the assignment and their architectural requirements; the agent
   produced `docs/ARCHITECTURE.md` with a list of open questions and recommendations. The author approved them
   (one change: phone number validation) before any code was written.
2. **Project rules.** `CLAUDE.md` in the repository root captures the hard rules (no Spring Data, layering,
   soft delete, 90 % coverage in `verify`) and coding conventions. Claude Code loads it automatically.
3. **Per-step loop.** Each row of the implementation plan (steps 0–9 in `ARCHITECTURE.md` §8) was delivered
   the same way:
   - an *implementer* sub-agent received the step description and implemented code **and** tests;
   - an independent *reviewer* sub-agent checked the result against the specification and the rules and
     returned a findings list (it never modified files);
   - the main agent evaluated the findings, applied the fixes and ran `./mvnw verify` (compilation, unit and
     integration tests, ArchUnit rules, JaCoCo 90 % gate);
   - the main agent committed the step (one commit per step, see `git log`); the author gave an explicit OK
     for steps 0 and 1 and then let the remaining steps run without waiting.
4. **Final polish.** springdoc annotations, README, CI workflow, UML diagrams, this folder, and two follow-ups
   after the author tested on Windows / JDK 25 (annotation processor configuration) and asked for this export.

## Files given to the agents

| File | Purpose |
|------|---------|
| [`CLAUDE.md`](../../CLAUDE.md) | project rules and conventions, loaded automatically by Claude Code |
| [`docs/ARCHITECTURE.md`](../ARCHITECTURE.md) | binding design specification and implementation plan |
| [`docs/zadanie.md`](../zadanie.md) | the assignment (text transcript of the original `zadanie.docx`) |

Sub-agents were pointed at the relevant sections of these files in their prompts; the prompts themselves are
part of the transcript.

## Chat history

```
docs/ai/
├── README.md                     this file
├── export_transcript.py          converter: raw JSONL -> readable Markdown (Python 3, standard library only)
├── raw/
│   ├── session-ece6cbbd.jsonl    main session transcript exactly as written by Claude Code
│   └── subagents/
│       ├── agent-<id>.jsonl      transcript of one sub-agent (20 implementer/reviewer agents)
│       └── agent-<id>.meta.json  description of the sub-agent and the id of the Agent call that started it
└── transcript/
    ├── README.md                 index: one row per prompt of the author
    └── NN-<prompt>.md            readable transcript of everything that happened after that prompt
```

- **`raw/`** is a verbatim copy of `~/.claude/projects/<project>/` from the agent's container. The main
  transcript starts with the author's first message (the uploaded assignment and the architectural
  requirements) and ends with the request to produce this export, so the very last turn (copying the files,
  running the converter and committing) is only partially contained in it.
- **`transcript/`** is generated from `raw/` by `export_transcript.py`. It contains, in chronological order,
  every message of the author, every message of the agent, every tool call with its input and every tool
  result. Sub-agent transcripts are inlined as collapsible sections directly under the `Agent` call that
  spawned them, so the implementer/reviewer work is visible in context. Harness notifications that Claude Code
  delivers as user turns (hook output, sub-agent completion notices, image echoes) are labelled as such.
- Long tool inputs and outputs (file contents written in one go, build logs, sub-agent prompts) are
  truncated by the converter with an explicit note giving the original size; the full text is always in
  `raw/`. Nothing else is edited, reordered or summarised. Empty "thinking" blocks (the model's reasoning is
  not exported by Claude Code) are omitted.
- The transcripts contain only development-time data: the dev-profile admin credentials, test JWT secrets
  and access tokens issued against the local H2 database during smoke tests. No production secret exists.

To regenerate the Markdown after adding or replacing raw files:

```bash
python3 docs/ai/export_transcript.py
```
