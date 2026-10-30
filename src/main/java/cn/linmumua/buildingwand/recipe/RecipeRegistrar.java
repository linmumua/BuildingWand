package cn.linmumua.buildingwand.recipe;

import cn.linmumua.buildingwand.compat.CraftEngineHook;
import cn.linmumua.buildingwand.config.Config;
import cn.linmumua.buildingwand.item.WandTier;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * 合成配方。
 *
 * <pre>
 *   X A A      G = 木棍
 *   X G A      A = 该等级的材料
 *   G X X      X = 空
 * </pre>
 */
public final class RecipeRegistrar implements Listener {

    private static final String[] SHAPE = {" AA", " GA", "G  "};

    private final Plugin plugin;
    private final Config config;
    private final List<NamespacedKey> registered = new ArrayList<>();

    public RecipeRegistrar(Plugin plugin, Config config) {
        this.plugin = plugin;
        this.config = config;
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
            recipe.setIngredient('G', Material.STICK);
            if (Bukkit.addRecipe(recipe)) {
                registered.add(key);
            }
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
