package cn.linmumua.buildingwand.config;

import cn.linmumua.buildingwand.item.WandTier;
import cn.linmumua.buildingwand.wand.DirectionMode;
import cn.linmumua.buildingwand.wand.LockMode;
import cn.linmumua.buildingwand.wand.MatchMode;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 运行期配置快照。reload 时整体重建，避免半新半旧。
 */
public final class Config {

    /** 单档之杖的生效数值。 */
    public record TierProps(int durability, int limit) {
    }

    /** 默认选项，新物品按这份初始化。 */
    public record DefaultOptions(LockMode lock, DirectionMode direction, MatchMode match,
                                 boolean replace, boolean random) {
    }

    private final Map<WandTier, TierProps> tiers = new EnumMap<>(WandTier.class);
    private final List<List<String>> similarGroups = new ArrayList<>();
    private List<String> tileEntityList = List.of();

    private int maxRange;
    private boolean applyPhysics;
    private int undoHistory;
    private boolean tileEntityWhitelist;

    private DefaultOptions defaultOptions;

    private boolean previewEnabled;
    private int previewIntervalTicks;
    private int previewMaxBlocks;
    private String previewBuildGlow;
    private String previewDestroyGlow;
    private double previewPeek;
    private boolean previewDebug;
    private String previewBuildMaterial;

    private Config() {
    }

    public static Config load(File dataFolder, ClassLoader loader) {
        Config c = new Config();
        c.reload(dataFolder, loader);
        return c;
    }

    /** 原地重载。所有持有同一个 Config 实例的组件都会看到新值。 */
    public void reload(File dataFolder, ClassLoader loader) {
        YamlDocument doc = YamlDocument.loadOrCreate(
                new File(dataFolder, "config.yml").toPath(),
                YamlDocument.readResource(loader, "config.yml"));

        Config c = this;
        tiers.clear();
        similarGroups.clear();

        for (WandTier tier : WandTier.values()) {
            String base = "wand." + tier.id() + ".";
            c.tiers.put(tier, new TierProps(
                    doc.getInt(base + "durability", tier.defaultDurability()),
                    doc.getInt(base + "limit", tier.defaultLimit())));
        }

        c.maxRange = doc.getInt("misc.max-range", 100);
        c.applyPhysics = doc.getBoolean("misc.apply-physics", false);
        c.undoHistory = Math.max(1, doc.getInt("misc.undo-history", 3));

        c.defaultOptions = new DefaultOptions(
                parseLock(doc.getString("default-options.lock", "NOLOCK")),
                parseDirection(doc.getString("default-options.direction", "TARGET")),
                parseMatch(doc.getString("default-options.match", "SIMILAR")),
                doc.getBoolean("default-options.replace", true),
                doc.getBoolean("default-options.random", false));

        c.similarGroups.addAll(doc.getStringGroups("similar-blocks"));

        c.tileEntityWhitelist = doc.getBoolean("tile-entity.whitelist", false);
        c.tileEntityList = List.copyOf(doc.getStringList("tile-entity.list"));

        c.previewEnabled = doc.getBoolean("preview.enabled", true);
        c.previewIntervalTicks = Math.max(1, doc.getInt("preview.interval-ticks", 4));
        c.previewMaxBlocks = Math.max(1, doc.getInt("preview.max-blocks", 256));
        c.previewBuildGlow = doc.getString("preview.build-glow", "");
        c.previewDestroyGlow = doc.getString("preview.destroy-glow", "#FF4040");
        c.previewDebug = doc.getBoolean("preview.debug", false);
        // 预览高度：1 = 整格，0.5 = 半砖；上限 2，下限取 0.0625（1 像素），避免 0 或负数让预览消失/翻转
        c.previewPeek = Math.max(0.0625, Math.min(2.0, doc.getDouble("preview.peek", 1.0)));
        c.previewBuildMaterial = doc.getString("preview.build-material", "buildingwand:preview");
    }

    private static LockMode parseLock(String raw) {
        try {
            return LockMode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return LockMode.NOLOCK;
        }
    }

    private static MatchMode parseMatch(String raw) {
        try {
            return MatchMode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return MatchMode.SIMILAR;
        }
    }

    private static DirectionMode parseDirection(String raw) {
        try {
            return DirectionMode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return DirectionMode.TARGET;
        }
    }

    public TierProps tier(WandTier tier) {
        return tiers.get(tier);
    }

    public int maxRange() {
        return maxRange;
    }

    public boolean applyPhysics() {
        return applyPhysics;
    }

    /** 撤销保留的步数（默认 3）。 */
    public int undoHistory() {
        return undoHistory;
    }

    public DefaultOptions defaultOptions() {
        return defaultOptions;
    }

    public List<List<String>> similarGroups() {
        return similarGroups;
    }

    public boolean tileEntityWhitelist() {
        return tileEntityWhitelist;
    }

    public List<String> tileEntityList() {
        return tileEntityList;
    }

    public boolean previewEnabled() {
        return previewEnabled;
    }

    public int previewIntervalTicks() {
        return previewIntervalTicks;
    }

    /** 预览方块的显示高度（格），范围 0.0625~2，宽度始终为 1。 */
    public double previewPeek() {
        return previewPeek;
    }

    public int previewMaxBlocks() {
        return previewMaxBlocks;
    }

    /** 是否输出预览诊断日志（仅对拥有 buildingwand.debug 权限的玩家）。 */
    public boolean previewDebug() {
        return previewDebug;
    }

    /** 建造预览显示的方块；TARGET 表示用被瞄准的真实方块。 */
    public String previewBuildMaterial() {
        return previewBuildMaterial;
    }

    /** 建造预览的发光颜色；空字符串或 NONE 表示不给实体加发光描边。 */
    public String previewBuildGlow() {
        return previewBuildGlow;
    }

    /** 撤销预览的粒子颜色（沿方块棱描框）。 */
    public String previewDestroyGlow() {
        return previewDestroyGlow;
    }
}
