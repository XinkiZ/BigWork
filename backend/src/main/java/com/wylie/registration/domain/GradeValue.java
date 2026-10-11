package com.wylie.registration.domain;

import java.util.Set;

/** 成绩等级：A/B/C/D/F/I */
public final class GradeValue {

    public static final Set<String> ALL = Set.of("A", "B", "C", "D", "F", "I");
    /** 视为通过（满足先修要求）的等级 */
    public static final Set<String> PASSED = Set.of("A", "B", "C", "D");

    private GradeValue() {
    }

    public static boolean isValid(String value) {
        return value == null || ALL.contains(value);
    }
}