package cn.linmumua.buildingwand.wand;

/**
 * 放置限制。语义与原模组一致：NOLOCK 视为「任意轴向都允许」。
 */
public enum LockMode {
    HORIZONTAL,
    VERTICAL,
    NORTHSOUTH,
    EASTWEST,
    NOLOCK;

    /**
     * 该限制是否允许指定轴向的扩展。
     * NOLOCK 时全部放行，否则必须完全相等。
     */
    public boolean allows(LockMode axis) {
        return this == NOLOCK || this == axis;
    }

    public LockMode next() {
        LockMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
