package cn.linmumua.buildingwand.wand;

/**
 * 放置朝向。
 */
public enum DirectionMode {
    /** 朝向跟随被放置的支撑面 */
    TARGET,
    /** 朝向跟随玩家 */
    PLAYER;

    public DirectionMode next() {
        DirectionMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
