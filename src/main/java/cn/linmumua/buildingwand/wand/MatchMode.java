package cn.linmumua.buildingwand.wand;

/**
 * 方块匹配模式。
 */
public enum MatchMode {
    /** 方块类型完全相同 */
    EXACT,
    /** 落在同一相似组内（如泥土系） */
    SIMILAR,
    /** 只要双方都不是空气即可 */
    ANY;

    public MatchMode next() {
        MatchMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
