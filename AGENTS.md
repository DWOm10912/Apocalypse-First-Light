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
