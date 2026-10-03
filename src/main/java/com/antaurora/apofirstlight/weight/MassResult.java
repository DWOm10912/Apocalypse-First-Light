package com.antaurora.apofirstlight.weight;

import java.util.*;

/** Entire stack/source mass, never a per-unit value. No mutable ItemStack references. */
public record MassResult(long grams, Map<String, Long> breakdown, Set<String> issues) {
    public MassResult { breakdown = Map.copyOf(breakdown); issues = Set.copyOf(issues); }
    public String quality() { return issues.isEmpty() ? "EXACT" : "ESTIMATED"; }
    public static long add(long a, long b) { return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b; }
    public static long multiply(long a, long b) { return b > 0 && a > Long.MAX_VALUE / b ? Long.MAX_VALUE : a * b; }
    public static final class Builder {
        private long total;
        private final Map<String, Long> parts = new LinkedHashMap<>();
        private final Set<String> issues = new TreeSet<>();
        public void issue(String reason) { issues.add(reason); }
        public void add(String label, long grams) {
            total = MassResult.add(total, grams);
            parts.merge(label, grams, MassResult::add);
            if (total == Long.MAX_VALUE) issue("arithmetic_saturated");
        }
        public void add(String label, MassResult result) { add(label, result.grams()); issues.addAll(result.issues()); }
        public MassResult build() { return new MassResult(total, parts, issues); }
    }
}
