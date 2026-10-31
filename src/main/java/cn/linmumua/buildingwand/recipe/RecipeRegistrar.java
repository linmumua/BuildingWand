package cn.linmumua.buildingwand.recipe;

import cn.linmumua.buildingwand.compat.CraftEngineHook;
import cn.linmumua.buildingwand.config.Config;
import cn.linmumua.buildingwand.item.WandTier;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import net.momirealms.craftengine.bukkit.api.event.CraftEngineReloadEvent;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * 合成配方。三档之杖共用同一个形状，材料按等级替换，各档独立合成。
 *
 * <pre>
 *   X A A      P = 木棍
 *   X P A      A = 该等级的材料（圆石 / 铁锭 / 钻石）
 *   P X X      X = 空
 * </pre>
 *
 * <p>之杖物品底材是 {@code paper}，不会被当成配方里的木棍，所以不需要额外校验。</p>
 */
public final class RecipeRegistrar implements Listener {

    private static final String[] SHAPE = {" AA", " PA", "P  "};

    private final Plugin plugin;
    private final Config config;
    private final List<NamespacedKey> registered = new ArrayList<>();

    public RecipeRegistrar(Plugin plugin, Config config) {
        this.plugin = plugin;
        this.config = config;
    }

    /**
     * CraftEngine 每次加载/重载完成后重新注册配方。
     *
     * <p><b>为什么必须监听这个事件</b>：插件启用时才把资源包释放到 CE 目录，
     * 此时 CE 早已完成首轮加载、还不认识之杖物品，{@link CraftEngineHook#createWand}
     * 返回 null，三条配方全部注册失败。CE 随后重载、物品就绪，但没有人再注册一次——
     * 表现就是「配方怎么都合不出来」。CE 的 /ce reload 同样会重建物品，也需要跟着刷新。</p>
     *
     * <p>该事件不保证在全局区域线程触发，而增删配方是全服共享状态，
     * 所以统一投递到全局区域调度器上执行。</p>
     */
    @EventHandler
    public void onCraftEngineReload(CraftEngineReloadEvent event) {
        Bukkit.getGlobalRegionScheduler().execute(plugin, this::registerRecipes);
    }

    /** 注册三档之杖配方。CE 未就绪时跳过，reload 后可再调用。 */
    public void registerRecipes() {
        unregisterAll();
        for (WandTier tier : WandTier.values()) {
            String materialId = tier.material();
            if (materialId == null) {
                continue;
            }
            ItemStack result = CraftEngineHook.createWand(tier);
            if (result == null) {
                // 不再静默跳过：之前正是这里一声不吭，才查不出配方为什么没加进去
                plugin.getLogger().info("CraftEngine 尚未加载 " + tier.customItemId()
                        + "，暂不注册其配方（CE 加载完成后会自动补注册）");
                continue;
            }
            Material material = Material.matchMaterial(materialId);
            if (material == null) {
                plugin.getLogger().warning("未知的合成材料: " + materialId + "（等级 " + tier.id() + "）");
                continue;
            }
            NamespacedKey key = new NamespacedKey(plugin, tier.id() + "_wand");
            ShapedRecipe recipe = new ShapedRecipe(key, result);
            recipe.shape(SHAPE);
            recipe.setIngredient('A', material);
            recipe.setIngredient('P', Material.STICK);
            if (Bukkit.addRecipe(recipe)) {
                registered.add(key);
            } else {
                plugin.getLogger().warning("配方注册失败: " + key);
            }
        }
        if (!registered.isEmpty()) {
            plugin.getLogger().info("已注册 " + registered.size() + " 个之杖配方");
        }
    }

    public void unregisterAll() {
        for (NamespacedKey key : registered) {
            Bukkit.removeRecipe(key);
        }
        registered.clear();
    }

    public List<NamespacedKey> registered() {
        return List.copyOf(registered);
    }
}
