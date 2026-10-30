package cn.linmumua.buildingwand.compat;

import cn.linmumua.buildingwand.item.WandTier;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;

/**
 * CraftEngine 物品桥。CraftEngine 是硬前置，物品本体由 CE 资源包定义，
 * 这里只负责「按 id 取物品」和「反查这是哪一档之杖」。
 *
 * <p>注意：CE 的加载可能晚于本插件，所以全部做成调用时查询，不在 onEnable 缓存。</p>
 */
public final class CraftEngineHook {

    private CraftEngineHook() {
    }

    /** 按等级构建之杖物品。CE 未就绪时返回 null。 */
    public static ItemStack createWand(WandTier tier) {
        try {
            var definition = CraftEngineItems.byId(Key.of(tier.customItemId()));
            return definition == null ? null : definition.buildBukkitItem();
        } catch (Throwable t) {
            return null;
        }
    }

    /** 该物品是哪一档之杖；不是之杖返回 null。 */
    public static WandTier tierOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        try {
            if (!CraftEngineItems.isCustomItem(stack)) {
                return null;
            }
            Key id = CraftEngineItems.getCustomItemId(stack);
            if (id == null) {
                return null;
            }
            String raw = id.toString();
            if (!raw.startsWith("buildingwand:")) {
                return null;
            }
            String tierId = raw.substring("buildingwand:".length());
            if (tierId.endsWith("_wand")) {
                tierId = tierId.substring(0, tierId.length() - "_wand".length());
            }
            return WandTier.byId(tierId);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 按 CE 方块 id 取它的渲染状态，转成 Bukkit 的 {@link BlockData}，供 BlockDisplay 使用。
     *
     * <p>CE 自定义方块的底层是「某个原版方块 + 状态」，客户端靠资源包把该状态映射到自定义模型。
     * 因此把这里的 BlockData 交给 BlockDisplay，客户端就会渲染出我们要的外观。</p>
     *
     * <p><b>必须取 {@code customBlockState()}，不能取 {@code visualBlockState()}</b>：
     * 后者返回的是底层的「原版替身」状态（CE 里由 {@code BukkitVanillaBlockStateWrapper}
     * 承载），把它交给 BlockDisplay，客户端就会直接画出那颗原版方块——实测分别表现成
     * 音符盒和绿色树叶。前者才是 CE 定义、并且被资源包映射到自定义模型的那个状态。</p>
     *
     * @return 取不到时返回 null，调用方需自行回退
     */
    public static BlockData blockData(String blockId) {
        if (blockId == null || blockId.isBlank()) {
            return null;
        }
        try {
            var definition = CraftEngineBlocks.byId(Key.of(blockId));
            if (definition == null) {
                return null;
            }
            var state = definition.defaultState();
            if (state == null) {
                return null;
            }
            Object platform = state.customBlockState().platformState();
            return platform instanceof BlockData data ? data : null;
        } catch (Throwable t) {
            // CE 未就绪、方块未注册、或 API 版本差异，都按「取不到」处理，交给调用方回退
            return null;
        }
    }
}
