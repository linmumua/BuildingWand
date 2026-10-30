package cn.linmumua.buildingwand.gui;

import cn.linmumua.buildingwand.compat.CraftEngineHook;
import cn.linmumua.buildingwand.config.Config;
import cn.linmumua.buildingwand.config.Lang;
import cn.linmumua.buildingwand.item.WandKeys;
import cn.linmumua.buildingwand.item.WandTier;
import cn.linmumua.buildingwand.wand.DirectionMode;
import cn.linmumua.buildingwand.wand.WandOptions;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 之杖选项界面。用普通箱子 GUI，因为服务端无法渲染原模组的自定义界面。
 * 每次点击都立即写回物品，关闭界面不需要额外保存。
 */
public final class OptionsGui implements Listener {

    private static final int SLOT_INFO = 4;
    private static final int SLOT_LOCK = 11;
    private static final int SLOT_DIRECTION = 12;
    private static final int SLOT_REPLACE = 13;
    private static final int SLOT_MATCH = 14;
    private static final int SLOT_RANDOM = 15;
    private static final int SLOT_CLOSE = 22;
    private static final int SIZE = 27;

    private final Config config;
    private final Lang lang;
    private final WandKeys keys;
    private final Map<UUID, Session> sessions = new HashMap<>();

    private record Session(WandTier tier, WandOptions options, Holder holder) {
    }

    public OptionsGui(Config config, Lang lang, WandKeys keys) {
        this.config = config;
        this.lang = lang;
        this.keys = keys;
    }

    public void open(Player player) {
        ItemStack wand = player.getInventory().getItemInMainHand();
        WandTier tier = CraftEngineHook.tierOf(wand);
        if (tier == null) {
            return;
        }
        WandOptions options = WandOptions.read(wand, keys, config);
        Holder holder = new Holder();
        Inventory inventory = Bukkit.createInventory(holder, SIZE,
                lang.plain("options-title"));
        holder.inventory = inventory;
        sessions.put(player.getUniqueId(), new Session(tier, options, holder));
        render(inventory, tier, options);
        player.openInventory(inventory);
    }

    // ---------------------------------------------------------------- 渲染

    private void render(Inventory inventory, WandTier tier, WandOptions options) {
        ItemStack filler = icon(Material.GRAY_STAINED_GLASS_PANE, Component.empty(), List.of());
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, filler);
        }

        Config.TierProps props = config.tier(tier);
        inventory.setItem(SLOT_INFO, icon(Material.BOOK,
                lang.plain("gui-tier", "value", tier.displayName()),
                List.of(lang.plain("gui-info",
                        "limit", String.valueOf(props.limit())))));

        inventory.setItem(SLOT_LOCK, icon(Material.IRON_BARS,
                lang.plain("gui-lock", "value", lang.raw("lock-" + options.lock().name())),
                List.of(lang.plain("gui-lock-lore"))));

        inventory.setItem(SLOT_DIRECTION, icon(Material.COMPASS,
                lang.plain("gui-direction", "value", lang.raw("direction-" + options.direction().name())),
                List.of(lang.plain("gui-direction-lore"))));

        inventory.setItem(SLOT_REPLACE, icon(Material.GRASS_BLOCK,
                lang.plain("gui-replace", "value", lang.raw(options.replace() ? "state-on" : "state-off")),
                List.of(lang.plain("gui-replace-lore"))));

        inventory.setItem(SLOT_MATCH, icon(Material.SPYGLASS,
                lang.plain("gui-match", "value", lang.raw("match-" + options.match().name())),
                List.of(lang.plain("gui-match-lore"))));

        inventory.setItem(SLOT_RANDOM, icon(Material.FIREWORK_STAR,
                lang.plain("gui-random", "value", lang.raw(options.random() ? "state-on" : "state-off")),
                List.of(lang.plain("gui-random-lore"))));

        inventory.setItem(SLOT_CLOSE, icon(Material.BARRIER, lang.plain("gui-close"), List.of()));
    }

    private static ItemStack icon(Material material, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name);
        if (!lore.isEmpty()) {
            meta.lore(new ArrayList<>(lore));
        }
        stack.setItemMeta(meta);
        return stack;
    }

    // ---------------------------------------------------------------- 事件

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        WandOptions options = session.options();
        int slot = event.getRawSlot();

        switch (slot) {
            case SLOT_LOCK -> options.setLock(options.lock().next());
            case SLOT_DIRECTION -> options.setDirection(options.direction() == DirectionMode.TARGET
                    ? DirectionMode.PLAYER : DirectionMode.TARGET);
            case SLOT_REPLACE -> options.setReplace(!options.replace());
            case SLOT_MATCH -> options.setMatch(options.match().next());
            case SLOT_RANDOM -> options.setRandom(!options.random());
            case SLOT_CLOSE -> {
                player.closeInventory();
                return;
            }
            default -> {
                return;
            }
        }
        saveToWand(player, options);
        render(event.getInventory(), session.tier(), options);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder)) {
            return;
        }
        sessions.remove(event.getPlayer().getUniqueId());
    }

    /** 写回手持之杖，避免玩家在界面里切物品导致丢配置。 */
    private void saveToWand(Player player, WandOptions options) {
        ItemStack wand = player.getInventory().getItemInMainHand();
        if (CraftEngineHook.tierOf(wand) != null) {
            options.save(wand, keys);
        }
    }

    /** 用来标识「这是本插件的界面」。 */
    private static final class Holder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
