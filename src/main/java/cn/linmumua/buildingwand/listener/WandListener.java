package cn.linmumua.buildingwand.listener;

import cn.linmumua.buildingwand.compat.CraftEngineHook;
import cn.linmumua.buildingwand.config.Config;
import cn.linmumua.buildingwand.config.Lang;
import cn.linmumua.buildingwand.gui.OptionsGui;
import cn.linmumua.buildingwand.item.WandKeys;
import cn.linmumua.buildingwand.item.WandTier;
import cn.linmumua.buildingwand.task.PreviewTask;
import cn.linmumua.buildingwand.wand.Face;
import cn.linmumua.buildingwand.wand.Pos;
import cn.linmumua.buildingwand.wand.WandOptions;
import cn.linmumua.buildingwand.wand.WandService;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * 之杖交互。
 *
 * <p>键位映射（服务端能看到的只有这些）：
 * <ul>
 *   <li>右键方块 → 沿命中面铺开同质方块</li>
 *   <li>潜入 + 右键空气 → 打开选项界面</li>
 *   <li>潜行 + 左键 → 撤销最近一次操作</li>
 *   <li>潜行 + 滚轮 → 切换放置限制（不潜行时保留原版快捷栏行为）</li>
 * </ul>
 *
 * <p>潜行还兼作预览开关：站着看铺面预览，潜行看撤销预览（见 PreviewTask）。</p>
 */
public final class WandListener implements Listener {

    private final Config config;
    private final Lang lang;
    private final WandKeys keys;
    private final WandService service;
    private final PreviewTask preview;
    private final OptionsGui optionsGui;

    public WandListener(Config config, Lang lang, WandKeys keys, WandService service,
                        PreviewTask preview, OptionsGui optionsGui) {
        this.config = config;
        this.lang = lang;
        this.keys = keys;
        this.service = service;
        this.preview = preview;
        this.optionsGui = optionsGui;
    }

    // ---------------------------------------------------------------- 右键

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack wand = player.getInventory().getItemInMainHand();
        WandTier tier = CraftEngineHook.tierOf(wand);
        if (tier == null) {
            return;
        }

        Action action = event.getAction();
        if (action == Action.LEFT_CLICK_BLOCK || action == Action.LEFT_CLICK_AIR) {
            handleUndo(event, player);
            return;
        }
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) {
            return;
        }

        // 潜行 + 右键空气 = 打开选项界面
        if (action == Action.RIGHT_CLICK_AIR && player.isSneaking()) {
            event.setCancelled(true);
            optionsGui.open(player);
            preview.clear(player);
            return;
        }

        WandOptions options = WandOptions.read(wand, keys, config);

        // 右键空气没有动作（之杖只有「对着方块铺面」这一种行为）
        if (action == Action.RIGHT_CLICK_AIR) {
            return;
        }

        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }
        event.setCancelled(true);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);

        Face face = toFace(event.getBlockFace());
        if (face == null) {
            return;
        }
        Pos hit = new Pos(clicked.getX(), clicked.getY(), clicked.getZ());
        List<Pos> plan = service.planOnBlock(player, hit, face, options, tier);
        if (plan.isEmpty()) {
            return;
        }

        BlockData prototype = clicked.getBlockData();
        int placed = service.place(player, wand, tier, options, plan, prototype);
        // 方块不够时明确告诉玩家少了几格，否则会以为之杖坏了
        if (placed < plan.size() && player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            player.sendMessage(lang.msg("not-enough-blocks",
                    "count", String.valueOf(placed)));
        }
        preview.clear(player);
    }


    // ---------------------------------------------------------------- 撤销

    private void handleUndo(PlayerInteractEvent event, Player player) {
        if (!player.isSneaking()) {
            return;
        }
        event.setCancelled(true);
        int count = service.undoHistory().undo(player);
        if (count > 0) {
            player.sendMessage(lang.msg("undo-success", "count", String.valueOf(count)));
        } else {
            player.sendMessage(lang.msg("undo-none"));
        }
        preview.clear(player);
    }

    // ---------------------------------------------------------------- 键位


    /** 潜行 + 滚轮：切换放置限制。不潜行时完全放行，保留原版快捷栏行为。 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }
        ItemStack wand = player.getInventory().getItemInMainHand();
        if (CraftEngineHook.tierOf(wand) == null) {
            return;
        }
        event.setCancelled(true);
        WandOptions options = WandOptions.read(wand, keys, config);
        options.setLock(options.lock().next());
        options.save(wand, keys);
        player.sendMessage(lang.msg("lock-switched",
                "lock", lang.raw("lock-" + options.lock().name())));
        preview.clear(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        preview.forget(event.getPlayer());
        service.undoHistory().clear(event.getPlayer());
    }

    // ---------------------------------------------------------------- 工具

    private static Face toFace(BlockFace face) {
        return switch (face) {
            case UP -> Face.UP;
            case DOWN -> Face.DOWN;
            case NORTH -> Face.NORTH;
            case SOUTH -> Face.SOUTH;
            case EAST -> Face.EAST;
            case WEST -> Face.WEST;
            default -> null;
        };
    }

    /** 准星前方 2 格的落点，与原模组一致。 */
    private static Pos aimPos(Player player) {
        var eye = player.getEyeLocation();
        var dir = eye.getDirection().multiply(2);
        return new Pos((int) Math.round(eye.getX() + dir.getX()),
                (int) Math.round(eye.getY() + dir.getY()),
                (int) Math.round(eye.getZ() + dir.getZ()));
    }
}
