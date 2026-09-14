package com.antaurora.apofirstlight.worldgen.structure;

import java.util.Objects;

/** Mechanical diagnostics only; never logs, repairs resources, or grants game QA. */
public record StructureValidationIssue(Severity severity, Code code, String path, String message) {
    public enum Severity { ERROR, WARNING }
    public enum Code {
        MISSING_NBT, INVALID_NBT_SIZE, INVALID_NBT_DATA, INVALID_FRONT,
        EMPTY_ROTATION_SET, SOCKET_OUT_OF_BOUNDS, SOCKET_NOT_ON_BOUNDARY,
        SOCKET_FACING_INWARD, DUPLICATE_SOCKET_NAME, INVALID_GROUND_ANCHOR,
        UNSUPPORTED_SCHEMA, ASSET_REVISION_MISSING, ROTATION_TRANSFORM_MISMATCH,
        LEGACY_DIMENSION_MISMATCH, BE_REQUIRES_GAME_QA, MULTIBLOCK_REQUIRES_GAME_QA,
        MISSING_REQUIRED_SOCKET_INFORMATION, LEGACY_OFFSET_REQUIRES_CONFIRMATION,
        MISSING_FIELD, INVALID_FIELD, DUPLICATE_FIELD, DUPLICATE_METADATA_ID,
        FORBIDDEN_NBT_CONTENT
    }
    public StructureValidationIssue {
        Objects.requireNonNull(severity); Objects.requireNonNull(code);
        Objects.requireNonNull(path); Objects.requireNonNull(message);
    }
    public static StructureValidationIssue error(Code code, String path, String message) {
        return new StructureValidationIssue(Severity.ERROR, code, path, message);
    }
    public static StructureValidationIssue warning(Code code, String path, String message) {
        return new StructureValidationIssue(Severity.WARNING, code, path, message);
    }
    /** Typed construction/parser rejection, converted to a report at loading boundaries. */
    public static final class Invalid extends IllegalArgumentException {
        private final StructureValidationIssue issue;
        public Invalid(Code code, String path, String message) {
            super(path + ": " + message);
            issue = error(code, path, message);
        }
        public StructureValidationIssue issue() { return issue; }
    }
}
