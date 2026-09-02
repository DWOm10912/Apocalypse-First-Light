# Documentation Sync

Documentation is part of the Definition of Done for every implementation task that changes final live behavior.

This includes new or removed systems and features, gameplay or balance changes, architecture changes, command changes, registry/name changes, behavior-changing bug fixes, and features that are postponed after previously being documented as current.

Before finishing any implementation task:

1. Identify which `docs/*.md` files describe the modified system.
2. Update only those directly related documents to match the final live behavior.
3. Update implemented, partial, planned, obsolete, and known-issue status accurately.
4. Update every changed value, formula, command, registry ID, name, and relevant code/resource path.
5. Remove obsolete behavior descriptions or clearly mark them obsolete.
6. Never describe an unimplemented plan as current behavior.
7. Never claim runtime verification when runtime verification was not performed.
8. If no documentation change is needed, explicitly explain why in the final report.

If live behavior changed but its documentation was not synchronized, the task is not fully complete.

Every implementation-task final report must include:

```text
DOCUMENTATION:

DOCS UPDATED = YES/NO

Updated:
- <path>

Reason if NO:
<why this task does not affect documentation>
```

# Blockbench Source Models

- All editable Blockbench source models belong under `src/main/blockbench/`.
- Editable Blockbench sources and runtime Minecraft model JSON are different artifacts.
- Do not place or overwrite editable Blockbench source geometry under `src/main/resources/assets/.../models/`.
- BER / `ENTITYBLOCK_ANIMATED` blocks may intentionally use particle-only or builtin runtime model JSON.
- When a model geometry changes, preserve/update the editable source under `src/main/blockbench/` and update its runtime representation only when the task requires it.
