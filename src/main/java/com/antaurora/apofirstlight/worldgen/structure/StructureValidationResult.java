package com.antaurora.apofirstlight.worldgen.structure;

import java.util.List;

/** Status is derived from immutable issues, so errors cannot be labelled VALID. */
public record StructureValidationResult(List<StructureValidationIssue> issues) {
    public enum Status { VALID, VALID_WITH_WARNINGS, INVALID }
    public StructureValidationResult { issues = List.copyOf(issues); }
    public Status status() {
        if (issues.stream().anyMatch(i -> i.severity() == StructureValidationIssue.Severity.ERROR)) return Status.INVALID;
        return issues.isEmpty() ? Status.VALID : Status.VALID_WITH_WARNINGS;
    }
    public boolean valid() { return status() != Status.INVALID; }
}
