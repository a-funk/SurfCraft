---
name: obstacle-protocol
description: Turn walls, blockers, repeated failures, hard constraints, and apparently impossible goals into evidence-backed causal investigations and durable incremental solutions. Use this skill whenever progress is stuck, an initial fix fails, a dependency or platform limitation threatens the outcome, a workaround would merely hide the problem, or the user says to keep going despite obstacles—even if they do not explicitly name the skill.
---

# Obstacle Protocol

Treat an obstacle as a signal to deepen the work, not as permission to abandon
the goal or improvise an unverified workaround.

The operating loop is:

```text
Problem → Evidence → Cause → Strategies → Decision → Delivery → Verification → Remaining
```

Persistence does not broaden authorization. Stay inside the user's scope,
protect data and systems, and request new authority only when it is genuinely
required.

## 1. Define the outcome and the obstacle

Restate the intended outcome in falsifiable terms. Then document:

- the exact failure or constraint;
- reproduction conditions and affected scope;
- what was expected and what occurred;
- evidence already available;
- what is fact, estimate, inference, or unknown.

Preserve useful logs, versions, commands, measurements, and failed attempts.
Do not reduce the problem to the first visible error message.

## 2. Observe from multiple perspectives

Examine only the perspectives that can materially change the solution:

- system and architecture;
- user and product outcome;
- developer and operator experience;
- security, privacy, and trust boundaries;
- performance, reliability, and resource limits;
- short-term delivery and long-term maintenance;
- cost, reversibility, and migration risk.

Look for interactions between constraints. A fix that works locally but breaks
privacy, operability, or the product goal is not a complete solution.

Do not silently omit an expected perspective. For consequential work, record
which perspectives were examined and briefly explain why any apparently
relevant one is immaterial. When a strategy introduces remote fallback,
offload, third-party processing, or a new data path, explicitly document the
privacy and trust-boundary change.

## 3. Establish causality

Answer specifically: **Why is this occurring?**

Separate:

- **symptom:** what is visible;
- **mechanism:** the process producing the symptom;
- **root cause:** the controllable condition that must change;
- **contributors:** conditions that amplify or expose it.

Create causal hypotheses when needed. For each hypothesis, state:

1. evidence supporting it;
2. evidence that would refute it;
3. the smallest safe test that discriminates it from alternatives.

Run those tests or inspect primary evidence before calling a cause confirmed.
Do not present correlation, intuition, or a plausible story as proof.

## 4. Research cause-targeted strategies

Search local source, logs, current primary documentation, upstream code,
issues, papers, or controlled experiments as appropriate. Research the
verified mechanism rather than searching only the symptom text.

Identify multiple viable strategies when the decision is consequential:

- direct root-cause correction;
- architectural correction;
- bounded and reversible workaround;
- staged migration or replacement;
- experimental path that closes a key unknown.

Record why rejected strategies fail the outcome or constraints.

## 5. Choose for the strongest long-term outcome

Compare strategies using the dimensions that matter:

- ease of use;
- reusability across future cases;
- robustness and failure behavior;
- maintainability and observability;
- security and privacy;
- performance and resource efficiency;
- reversibility and migration cost;
- time to useful evidence and time to full solution.

Prefer the simplest strategy that removes or controls the cause without
weakening the goal. Label tactical workarounds honestly, define their ceiling,
and name the upgrade path.

## 6. Plan research and delivery in verifiable increments

Break the chosen strategy into steps that each produce useful evidence or a
working capability. Every material step needs:

- a concrete outcome;
- a verification gate;
- dependencies and safety boundaries;
- reproducible provenance for material inputs, such as exact versions,
  revisions, configuration, and hashes when available;
- the next decision enabled by its result.

Order the plan so early steps retire the highest-risk unknowns. Preserve
intermediate artifacts so another session can continue without restarting.

## 7. Deliver piece by piece

Execute the next safe step instead of stopping at the plan. Verify before
building on it. When a step fails:

1. add the new observation to the evidence;
2. update the causal model;
3. revise the strategy or next experiment;
4. continue toward the outcome.

Send concise progress updates during long work. Clearly label measured results,
estimates, hypotheses, and decisions.

## 8. Close or hand off precisely

Call the obstacle solved only when the original outcome is verified. Report:

```markdown
## Problem
## Evidence
## Cause
## Strategies considered
## Decision
## Delivered
## Verification
## Remaining risks or next step
```

Adapt the headings to the task rather than forcing unnecessary ceremony.

If progress genuinely requires unavailable hardware, credentials, approval,
another person, or an external state change, complete every safe preparatory
step first. Then provide the causal chain, completed work, exact unblock
request, and exact continuation command. This is a precise handoff, not an
unexplained dead end.

## Failure modes to avoid

- Repeating the same failed action without learning.
- Stopping after identifying a limitation.
- Treating a workaround as a root-cause solution.
- Researching broad possibilities before observing the actual problem.
- Choosing only for fastest implementation when it creates recurring toil.
- Inventing measurements, certainty, or success.
- Expanding scope or taking destructive action in the name of persistence.
- Producing a report when a safe next implementation step is available.
