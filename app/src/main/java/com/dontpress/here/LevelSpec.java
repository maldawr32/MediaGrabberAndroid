package com.dontpress.here;

public final class LevelSpec {
    public enum Type {
        TAP_RED,
        DONT_TOUCH,
        TAP_COUNT,
        LONG_PRESS,
        DOUBLE_TAP,
        TAP_OUTSIDE,
        CHASE,
        SWIPE,
        DRAG_TO_ZONE,
        TAP_WHEN_GREEN,
        SHAKE,
        TILT,
        MULTI_TOUCH,
        KEEP_STILL,
        FIND_TINY,
        MEMORY,
        HOLD_AND_SHAKE,
        REVERSE_ORDER
    }

    public final String name;
    public final String instruction;
    public final String characterLine;
    public final Type type;
    public final int value;
    public final long timeMs;

    public LevelSpec(String name, String instruction, String characterLine,
                     Type type, int value, long timeMs) {
        this.name = name;
        this.instruction = instruction;
        this.characterLine = characterLine;
        this.type = type;
        this.value = value;
        this.timeMs = timeMs;
    }
}
