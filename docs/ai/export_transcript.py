#!/usr/bin/env python3
"""Render Claude Code JSONL transcripts (main session + sub-agents) into readable Markdown.

Usage:
    python3 docs/ai/export_transcript.py [--raw docs/ai/raw] [--out docs/ai/transcript]

Input layout (as written by Claude Code under ~/.claude/projects/<project>/):
    raw/session-*.jsonl                       main session transcript
    raw/subagents/agent-<id>.jsonl            one transcript per sub-agent
    raw/subagents/agent-<id>.meta.json        {"description", "toolUseId", ...}

Output: one Markdown file per human prompt (NN-<slug>.md) plus an index (README-index.md). Every user
message, assistant message, tool call and tool result is kept in chronological order; sub-agent
transcripts are inlined (collapsible) right after the `Agent` tool call that spawned them. Long tool
inputs/results are truncated with an explicit note; nothing else is altered.
"""
from __future__ import annotations

import argparse
import glob
import json
import os
import re
from dataclasses import dataclass, field
from typing import Any

MAX_RESULT_CHARS = 4000       # tool result bodies longer than this are truncated
MAX_RESULT_LINES = 80
MAX_INPUT_CHARS = 3000        # tool inputs (file contents, prompts) longer than this are truncated
MAX_TEXT_CHARS = 60000        # assistant/user prose is never truncated below this (safety net only)

SKIPPED_TYPES = {"attachment", "queue-operation", "last-prompt", "atis-latch", "mode", "summary"}


@dataclass
class SubAgent:
    agent_id: str
    description: str
    tool_use_id: str
    rows: list[dict[str, Any]] = field(default_factory=list)


# ----------------------------------------------------------------------------- helpers

def load_jsonl(path: str) -> list[dict[str, Any]]:
    rows = []
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if line:
                rows.append(json.loads(line))
    return rows


def truncate(text: str, max_chars: int, max_lines: int | None = None, what: str = "output") -> str:
    lines = text.splitlines()
    cut_by_lines = max_lines is not None and len(lines) > max_lines
    cut_by_chars = len(text) > max_chars
    if not cut_by_lines and not cut_by_chars:
        return text
    if cut_by_lines:
        kept = "\n".join(lines[:max_lines])
        if len(kept) > max_chars:
            kept = kept[:max_chars]
    else:
        kept = text[:max_chars]
    note = (f"\n\n[... {what} truncated by the export script: showing {len(kept):,} of {len(text):,} characters"
            f" ({len(lines):,} lines in total); the complete text is in the raw JSONL ...]")
    return kept + note


def fence(text: str, lang: str = "") -> str:
    ticks = "```"
    while ticks in text:
        ticks += "`"
    return f"{ticks}{lang}\n{text}\n{ticks}"


def slugify(text: str, limit: int = 40) -> str:
    text = re.sub(r"[^A-Za-z0-9]+", "-", text).strip("-").lower()
    return text[:limit].rstrip("-") or "prompt"


def ts(row: dict[str, Any]) -> str:
    return (row.get("timestamp") or "")[11:19]


NOTIFICATION_PREFIXES = ("Stop hook feedback", "<system-reminder>", "<task-notification>", "[Image:")


def is_notification_text(text: str) -> bool:
    """True for turns the harness injected as 'user' messages (hooks, sub-agent completions, image echoes)."""
    head = text.lstrip()[:400]
    return head.startswith(NOTIFICATION_PREFIXES) or "[SYSTEM NOTIFICATION" in head


def block_text(content: Any) -> str:
    """Plain text of a message content (string or list of blocks)."""
    if isinstance(content, str):
        return content
    parts = []
    for block in content or []:
        if not isinstance(block, dict):
            continue
        if block.get("type") == "text":
            parts.append(block.get("text", ""))
        elif block.get("type") == "image":
            parts.append("[image]")
    return "\n".join(parts)


def format_tool_input(name: str, inp: dict[str, Any]) -> str:
    """Human-readable rendering of a tool call's input."""
    if name == "Bash":
        desc = inp.get("description")
        head = f"_{desc}_\n\n" if desc else ""
        return head + fence(truncate(inp.get("command", ""), MAX_INPUT_CHARS, what="command"), "bash")
    if name in ("Write",):
        return (f"`{inp.get('file_path')}`\n\n"
                + fence(truncate(inp.get("content", ""), MAX_INPUT_CHARS, what="file content")))
    if name == "Edit":
        return (f"`{inp.get('file_path')}`" + (" (replace all)" if inp.get("replace_all") else "") + "\n\n"
                + "old:\n" + fence(truncate(inp.get("old_string", ""), MAX_INPUT_CHARS // 2, what="old text"))
                + "\n\nnew:\n" + fence(truncate(inp.get("new_string", ""), MAX_INPUT_CHARS // 2, what="new text")))
    if name == "Read":
        extra = {k: v for k, v in inp.items() if k != "file_path"}
        return f"`{inp.get('file_path')}`" + (f" {json.dumps(extra)}" if extra else "")
    if name in ("Glob", "Grep"):
        return fence(json.dumps(inp, ensure_ascii=False, indent=2), "json")
    if name == "Agent":
        desc = inp.get("description", "")
        prompt = inp.get("prompt", "")
        meta = {k: v for k, v in inp.items() if k not in ("description", "prompt")}
        return (f"**{desc}** {json.dumps(meta) if meta else ''}\n\nprompt:\n\n"
                + fence(truncate(prompt, MAX_INPUT_CHARS * 2, what="agent prompt"), "text"))
    return fence(truncate(json.dumps(inp, ensure_ascii=False, indent=2), MAX_INPUT_CHARS, what="tool input"),
                 "json")


def format_tool_result(block: dict[str, Any]) -> str:
    content = block.get("content")
    if isinstance(content, list):
        texts = []
        for part in content:
            if isinstance(part, dict) and part.get("type") == "text":
                texts.append(part.get("text", ""))
            elif isinstance(part, dict) and part.get("type") == "image":
                texts.append("[image omitted]")
        text = "\n".join(texts)
    else:
        text = str(content or "")
    text = truncate(text, MAX_RESULT_CHARS, MAX_RESULT_LINES, what="tool result")
    label = "tool result (error)" if block.get("is_error") else "tool result"
    return f"<details><summary>{label}</summary>\n\n{fence(text)}\n\n</details>"


# ----------------------------------------------------------------------------- rendering

class Renderer:
    def __init__(self, subagents: dict[str, SubAgent]):
        self.subagents = subagents          # keyed by toolUseId of the spawning Agent call
        self.tool_names: dict[str, str] = {}

    def render_rows(self, rows: list[dict[str, Any]], depth: int = 0) -> list[str]:
        out: list[str] = []
        h = "#" * min(3 + depth, 6)
        for row in rows:
            rtype = row.get("type")
            if rtype in SKIPPED_TYPES or rtype is None:
                continue
            if rtype == "system":
                errors = row.get("hookErrors") or []
                if errors:
                    out.append(f"> **system hook** {ts(row)}: " + " ".join(e.strip() for e in errors))
                continue
            msg = row.get("message") or {}
            content = msg.get("content")
            if rtype == "user":
                if isinstance(content, str) or (isinstance(content, list) and content
                                                 and content[0].get("type") == "text"):
                    text = block_text(content)
                    kind = "user"
                    if is_notification_text(text):
                        kind = "harness notification (delivered as a user turn)"
                    out.append(f"{h} 🧑 {kind} — {ts(row)}\n\n{truncate(text, MAX_TEXT_CHARS, what='message')}")
                elif isinstance(content, list):
                    for block in content:
                        if block.get("type") == "tool_result":
                            name = self.tool_names.get(block.get("tool_use_id"), "tool")
                            out.append(f"**↳ {name}** {ts(row)}\n\n{format_tool_result(block)}")
                        elif block.get("type") == "text":
                            out.append(f"{h} 🧑 user — {ts(row)}\n\n{block.get('text', '')}")
                continue
            if rtype == "assistant":
                for block in content or []:
                    btype = block.get("type")
                    if btype == "text" and block.get("text", "").strip():
                        out.append(f"{h} 🤖 assistant — {ts(row)}\n\n{block['text']}")
                    elif btype == "thinking" and block.get("thinking", "").strip():
                        out.append(f"<details><summary>assistant thinking</summary>\n\n"
                                   f"{truncate(block['thinking'], MAX_TEXT_CHARS, what='thinking')}\n\n</details>")
                    elif btype == "tool_use":
                        name = block.get("name", "tool")
                        self.tool_names[block.get("id")] = name
                        out.append(f"**🔧 {name}** {ts(row)}\n\n{format_tool_input(name, block.get('input') or {})}")
                        sub = self.subagents.get(block.get("id"))
                        if sub is not None:
                            out.append(self.render_subagent(sub, depth + 1))
                continue
        return out

    def render_subagent(self, sub: SubAgent, depth: int) -> str:
        body = "\n\n".join(self.render_rows(sub.rows, depth))
        return (f"<details><summary>🧵 sub-agent <code>{sub.agent_id}</code>: {sub.description} "
                f"({len(sub.rows)} records)</summary>\n\n{body}\n\n</details>")


# ----------------------------------------------------------------------------- main

def split_by_prompt(rows: list[dict[str, Any]]) -> list[tuple[str, list[dict[str, Any]]]]:
    """Split the main transcript into chunks starting at each human prompt (non-notification user text)."""
    chunks: list[tuple[str, list[dict[str, Any]]]] = []
    current: list[dict[str, Any]] = []
    title = "session start"
    for row in rows:
        if row.get("type") == "user":
            content = (row.get("message") or {}).get("content")
            if isinstance(content, str):
                text = content.strip()
                if not is_notification_text(text):
                    if current:
                        chunks.append((title, current))
                    current = []
                    title = text.splitlines()[0][:80]
        current.append(row)
    if current:
        chunks.append((title, current))
    return chunks


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--raw", default=os.path.join(os.path.dirname(__file__), "raw"))
    parser.add_argument("--out", default=os.path.join(os.path.dirname(__file__), "transcript"))
    args = parser.parse_args()

    sessions = sorted(glob.glob(os.path.join(args.raw, "session-*.jsonl")))
    if not sessions:
        raise SystemExit(f"no session-*.jsonl in {args.raw}")

    subagents: dict[str, SubAgent] = {}
    for meta_path in sorted(glob.glob(os.path.join(args.raw, "subagents", "agent-*.meta.json"))):
        with open(meta_path, encoding="utf-8") as fh:
            meta = json.load(fh)
        agent_id = os.path.basename(meta_path)[len("agent-"):-len(".meta.json")]
        jsonl_path = meta_path[:-len(".meta.json")] + ".jsonl"
        rows = load_jsonl(jsonl_path) if os.path.exists(jsonl_path) else []
        subagents[meta["toolUseId"]] = SubAgent(agent_id, meta.get("description", ""), meta["toolUseId"], rows)

    os.makedirs(args.out, exist_ok=True)
    for old in glob.glob(os.path.join(args.out, "*.md")):
        os.remove(old)

    index_lines = ["# Transcript index", "",
                   "Generated by `export_transcript.py` from the raw JSONL files in `raw/`. One file per human prompt;",
                   "sub-agent transcripts are inlined (collapsible) under the `Agent` call that started them.", "",
                   "| # | Started | Prompt | Records | Sub-agents |", "|---|---------|--------|---------|------------|"]
    renderer = Renderer(subagents)
    for session in sessions:
        rows = load_jsonl(session)
        chunks = [(t, c) for t, c in split_by_prompt(rows)
                  if any(r.get("type") in ("user", "assistant") for r in c)]
        for i, (title, chunk) in enumerate(chunks, start=1):
            started = next((r.get("timestamp", "") for r in chunk if r.get("timestamp")), "")
            spawned = [b.get("id") for r in chunk if r.get("type") == "assistant"
                       for b in (r.get("message") or {}).get("content") or []
                       if b.get("type") == "tool_use" and b.get("name") == "Agent"]
            n_sub = sum(1 for s in spawned if s in subagents)
            fname = f"{i:02d}-{slugify(title)}.md"
            body = "\n\n".join(renderer.render_rows(chunk))
            header = (f"# {i:02d}. {title}\n\n"
                      f"Session `{os.path.basename(session)}`, started {started}. "
                      f"{len(chunk)} records, {n_sub} sub-agent transcript(s) inlined.\n\n"
                      f"Legend: 🧑 user · 🤖 assistant · 🔧 tool call · ↳ tool result · 🧵 sub-agent.\n\n---\n\n")
            with open(os.path.join(args.out, fname), "w", encoding="utf-8") as fh:
                fh.write(header + body + "\n")
            index_lines.append(f"| {i:02d} | {started[:19].replace('T', ' ')} | [{title}]({fname}) | {len(chunk)} | {n_sub} |")
    with open(os.path.join(args.out, "README.md"), "w", encoding="utf-8") as fh:
        fh.write("\n".join(index_lines) + "\n")
    unmatched = [s.agent_id for s in subagents.values() if s.tool_use_id not in renderer.tool_names]
    if unmatched:
        print(f"WARNING: {len(unmatched)} sub-agent transcript(s) not matched to an Agent call: {unmatched}")
    print(f"wrote {len(glob.glob(os.path.join(args.out, '*.md')))} files to {args.out}")


if __name__ == "__main__":
    main()
