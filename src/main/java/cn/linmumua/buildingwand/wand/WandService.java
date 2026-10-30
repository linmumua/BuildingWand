package cn.linmumua.buildingwand.wand;

import cn.linmumua.buildingwand.compat.BlockMatch;
import cn.linmumua.buildingwand.compat.BukkitWorldView;
import cn.linmumua.buildingwand.compat.EntityOccupancy;
import cn.linmumua.buildingwand.config.Config;
import cn.linmumua.buildingwand.item.WandTier;
import cn.linmumua.buildingwand.wand.action.ConstructionAction;
import cn.linmumua.buildingwand.wand.supply.BlockSupply;
import cn.linmumua.buildingwand.wand.undo.UndoHistory;
import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Rotatable;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * 之杖的执行门面：算计划、真正落块/拆块、扣方块、扣耐久、记撤销。
 * 事件监听器和预览任务都只调用这里，保证「看到什么」和「放到什么」是同一套算法。
 */
public final class WandService {

    /** 唯一的动作算法：沿命中面外侧洪泛铺开。 */
    private static final ConstructionAction ACTION = new ConstructionAction();

    private final Config config;
    private final UndoHistory undoHistory;

    public WandService(Config config, UndoHistory undoHistory) {
        this.config = config;
        this.undoHistory = undoHistory;
    }

    public UndoHistory undoHistory() {
        return undoHistory;
    }

    // ------------------------------------------------------------------ 计划

    /** 命中方块时的计划。之杖只有「铺面」这一种动作。 */
    public List<Pos> planOnBlock(Player player, Pos hit, Face face,
                                 WandOptions options, WandTier tier) {
        BukkitWorldView view = new BukkitWorldView(player.getWorld(), player, config, options.replace());
        BlockMatcher matcher = BlockMatch.of(options.match(), config.similarGroups());
        int limit = limitFor(player, tier);
        if (limit <= 0) {
            return List.of();
        }
        return ACTION.compute(view, hit, face, options.lock(), matcher, limit);
    }

    /** 本次最多能操作多少格。 */
    public int limitFor(Player player, WandTier tier) {
        Config.TierProps props = config.tier(tier);
        int limit = props.limit();
        if (limit <= 0) {
            return 0;
        }
        // 创造模式下剩余耐久无限，等价于直接吃该等级的上限
        return Math.min(remainingDurability(player, tier), limit);
    }

    // ------------------------------------------------------------------ 放置

    /**
     * 逐格放置。返回实际放置成功的数量。
     *
     * @param prototype 要复制的方块状态（通常是命中方块）
     */
    public int place(Player player, ItemStack wand, WandTier tier, WandOptions options,
                     List<Pos> positions, BlockData prototype) {
        if (positions.isEmpty() || prototype == null) {
            return 0;
        }
        World world = player.getWorld();
        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        Material material = prototype.getMaterial();
        BlockSupply supply = options.random() ? BlockSupply.random(player) : BlockSupply.inventory(player);

        List<UndoHistory.Change> changes = new ArrayList<>();
        int placed = 0;

        for (Pos pos : positions) {
            if (!creative && !supply.has(material)) {
                break;
            }
            BlockData data = oriented(prototype, options, player);
            BlockData previous = placeOne(player, world, pos, data, wand);
            if (previous == null) {
                continue;
            }
            if (!creative && !supply.takeOne(material)) {
                // 取不到方块就把刚放下的撤掉，避免出现「白送的方块」
                world.getBlockAt(pos.x(), pos.y(), pos.z()).setBlockData(previous, false);
                break;
            }
            changes.add(new UndoHistory.Change(pos, previous, data,
                    creative ? null : new ItemStack(material)));
            placed++;
            if (!creative) {
                consumeDurability(player, wand, tier);
                if (wand.getAmount() == 0) {
                    break;
                }
            }
        }

        if (!changes.isEmpty()) {
            undoHistory.push(player, new UndoHistory.Operation(changes, false));
        }
        return placed;
    }

    /**
     * 放置单格，并让保护插件有机会拦截。
     *
     * @return 落块前的方块状态（撤销时用来还原）；失败返回 null
     */
    private BlockData placeOne(Player player, World world, Pos pos, BlockData data, ItemStack wand) {
        Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
        // 必须在落块【之前】取状态，否则 BlockPlaceEvent 的 replacedState 就是新方块
        org.bukkit.block.BlockState previousState = block.getState();
        BlockData previous = previousState.getBlockData();
        if (previous.getMaterial() == data.getMaterial()) {
            return null;
        }
        if (!clearOfEntities(player, pos)) {
            return null;
        }
        block.setBlockData(data, config.applyPhysics());

        BlockPlaceEvent event = new BlockPlaceEvent(block, previousState, block,
                wand, player, true, EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled() || !event.canBuild()) {
            block.setBlockData(previous, false);
            return null;
        }
        return previous;
    }


    /** 剩余可放置格数（受耐久限制）。 */
    private int remainingDurability(Player player, WandTier tier) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return Integer.MAX_VALUE;
        }
        int max = config.tier(tier).durability();
        if (max < 0) {
            return Integer.MAX_VALUE;
        }
        ItemStack wand = player.getInventory().getItemInMainHand();
        return Math.max(0, max - damageOf(wand));
    }

    /**
     * 扣一点耐久。
     *
     * <p><b>必须走数据组件，不能用 {@link Damageable}</b>：之杖的底材是 {@code stick}，
     * 木棍的 ItemMeta 不是 Damageable，旧写法里 {@code meta instanceof Damageable}
     * 永远为假，于是耐久一点都扣不掉——表面上「实现了耐久」，实际完全没生效。
     * 1.20.5 之后任何物品都能通过 {@code max_damage}/{@code damage} 组件变成耐久物品，
     * 客户端也据此显示耐久条与「耐久不足」提示。</p>
     */
    private void consumeDurability(Player player, ItemStack wand, WandTier tier) {
        if (tier.unbreakable()) {
            return;
        }
        int max = config.tier(tier).durability();
        // 耐久上限以 config.yml 为唯一来源：首次消耗时把组件补上，
        // 这样 /bw give 发的、以及工作台合出来的之杖都能正常显示耐久条。
        if (max > 0 && !wand.hasData(DataComponentTypes.MAX_DAMAGE)) {
            wand.setData(DataComponentTypes.MAX_DAMAGE, max);
        }
        int next = damageOf(wand) + 1;
        if (max > 0 && next >= max) {
            // 耐久耗尽：之杖直接消失
            wand.setAmount(0);
            return;
        }
        wand.setData(DataComponentTypes.DAMAGE, next);
    }

    /** 读取 damage 组件；没有该组件时视为 0。 */
    private static int damageOf(ItemStack wand) {
        return wand.getDataOrDefault(DataComponentTypes.DAMAGE, 0);
    }

    /** PLAYER 朝向时把方向性方块的朝向掰向玩家。 */
    private BlockData oriented(BlockData prototype, WandOptions options, Player player) {
        if (options.direction() != DirectionMode.PLAYER) {
            return prototype.clone();
        }
        BlockData data = prototype.clone();
        BlockFace facing = cardinal(player.getLocation());
        try {
            if (data instanceof Rotatable rotatable) {
                rotatable.setRotation(facing);
            }
            // Directional#getFaces() 给出的是它支持的方向集合；含 NORTH 说明支持水平朝向
            if (data instanceof Directional directional && directional.getFaces().contains(BlockFace.NORTH)) {
                // 方块「面朝玩家」= 玩家朝向的反面
                directional.setFacing(facing.getOppositeFace());
            }
        } catch (IllegalArgumentException ignored) {
            // 某些方块不接受该朝向，保持原样
        }
        return data;
    }

    private static BlockFace cardinal(Location loc) {
        float yaw = loc.getYaw();
        int idx = Math.round(yaw / 90f) & 3;
        return switch (idx) {
            case 0 -> BlockFace.SOUTH;
            case 1 -> BlockFace.WEST;
            case 2 -> BlockFace.NORTH;
            default -> BlockFace.EAST;
        };
    }

    /**
     * 落块前的实体遮挡检查：该格是否被活着的实体占着。
     *
     * <p>判据统一走 {@link EntityOccupancy}，不再在这里单独写一遍。原来预览侧完全不看实体，
     * 于是 CraftEngine 家具的隐形碰撞箱（shulker 实体，属于 LivingEntity）所在的格子会被
     * 照常画进预览，玩家以为能放、右键却一格都不落。两边调同一个函数之后，
     * 「预览不显示的格子」和「放不下去的格子」才是同一批。</p>
     *
     * <p>单格语义与旧实现等价：以前用整格 AABB 与实体包围盒 {@code overlaps} 判定，
     * 现在把实体包围盒换算成方块再看是否包含这一格，结果一致。</p>
     */
    private static boolean clearOfEntities(Player player, Pos pos) {
        // 自己 spawn 的预览实体不在这里排除：放置发生在右键那一刻，此时预览正在被清掉，
        // 而且预览实体是 BlockDisplay（非 LivingEntity），本来就不会参与判据
        return EntityOccupancy.blockedPositions(player.getWorld(), List.of(pos), List.of()).isEmpty();
    }
}
