package cn.linmumua.buildingwand.item;

/**
 * 三档之杖的固有身份：CE 物品 id、默认数值、合成材料。
 * 实际生效的数值由 config.yml 覆盖（见 Config#tier）。
 */
public enum WandTier {
    STONE("stone", "石之杖", 131, 9, "minecraft:cobblestone"),
    IRON("iron", "铁之杖", 250, 27, "minecraft:iron_ingot"),
    DIAMOND("diamond", "钻石之杖", 1561, 128, "minecraft:diamond");

    private final String id;
    private final String displayName;
    private final int durability;
    private final int limit;
    private final String material;

    WandTier(String id, String displayName, int durability, int limit, String material) {
        this.id = id;
        this.displayName = displayName;
        this.durability = durability;
        this.limit = limit;
        this.material = material;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public int defaultDurability() {
        return durability;
    }

    public int defaultLimit() {
        return limit;
    }

    /** 合成材料 id；null 表示无配方。 */
    public String material() {
        return material;
    }

    public boolean unbreakable() {
        return durability < 0;
    }

    public String customItemId() {
        return "buildingwand:" + id + "_wand";
    }

    public static WandTier byId(String id) {
        for (WandTier tier : values()) {
            if (tier.id.equalsIgnoreCase(id)) {
                return tier;
            }
        }
        return null;
    }
}
