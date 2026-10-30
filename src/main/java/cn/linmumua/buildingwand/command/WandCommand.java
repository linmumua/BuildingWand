package cn.linmumua.buildingwand.command;

import cn.linmumua.buildingwand.compat.CraftEngineHook;
import cn.linmumua.buildingwand.config.Config;
import cn.linmumua.buildingwand.config.Lang;
import cn.linmumua.buildingwand.item.WandKeys;
import cn.linmumua.buildingwand.item.WandTier;
import cn.linmumua.buildingwand.wand.WandOptions;
import cn.linmumua.buildingwand.wand.WandService;
import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /buildingwand 指令。别名 /bw /wand。
 */
public final class WandCommand implements CommandExecutor, TabCompleter {

    private final Lang lang;
    private final WandKeys keys;
    private final WandService service;
    private final Config config;
    private final Runnable reloadAction;

    public WandCommand(Lang lang, WandKeys keys, WandService service, Config config, Runnable reloadAction) {
        this.lang = lang;
        this.keys = keys;
        this.service = service;
        this.config = config;
        this.reloadAction = reloadAction;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(lang.msg("help"));
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give" -> {
                return give(sender, args);
            }
            case "undo" -> {
                return undo(sender);
            }
            case "reload" -> {
                if (!sender.hasPermission("buildingwand.reload")) {
                    sender.sendMessage(lang.msg("no-permission"));
                    return true;
                }
                reloadAction.run();
                sender.sendMessage(lang.msg("reload-done"));
                return true;
            }
            default -> {
                sender.sendMessage(lang.msg("help"));
                return true;
            }
        }
    }

    private boolean give(CommandSender sender, String[] args) {
        if (!sender.hasPermission("buildingwand.give")) {
            sender.sendMessage(lang.msg("no-permission"));
            return true;
        }
        // 两种写法都接受：/bw give <等级> 与 /bw give <玩家> <等级>
        Player target;
        String tierRaw;
        if (args.length >= 3) {
            target = Bukkit.getPlayerExact(args[1]);
            tierRaw = args[2];
        } else {
            target = sender instanceof Player p ? p : null;
            tierRaw = args[1];
        }
        if (target == null) {
            sender.sendMessage(lang.msg("player-not-found", "player", args.length >= 3 ? args[1] : "-"));
            return true;
        }
        WandTier tier = WandTier.byId(tierRaw);
        if (tier == null) {
            sender.sendMessage(lang.msg("unknown-tier", "tier", tierRaw));
            return true;
        }

        ItemStack wand = CraftEngineHook.createWand(tier);
        if (wand == null) {
            sender.sendMessage(lang.msg("ce-not-ready"));
            return true;
        }
        WandOptions.defaults(config).save(wand, keys);
        // 【把耐久上限写进物品组件】
        // 之杖底材是 stick，木棍本身没有耐久，不写 max_damage 的话客户端不会显示耐久条，
        // 玩家也完全看不出自己还剩多少耐久。工作台合出来的之杖会在首次使用时补上同样的组件。
        int maxDamage = config.tier(tier).durability();
        if (maxDamage > 0) {
            wand.setData(DataComponentTypes.MAX_DAMAGE, maxDamage);
        }
        var leftover = target.getInventory().addItem(wand);
        for (ItemStack drop : leftover.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), drop);
        }
        sender.sendMessage(lang.msg("give-other", "player", target.getName(), "tier", tier.displayName()));
        target.sendMessage(lang.msg("give-received", "tier", tier.displayName()));
        return true;
    }


    private boolean undo(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(lang.msg("player-only"));
            return true;
        }
        int count = service.undoHistory().undo(player);
        player.sendMessage(count > 0
                ? lang.msg("undo-success", "count", String.valueOf(count))
                : lang.msg("undo-none"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("give", "undo", "reload"), args[0]);
        }
        if (args.length == 2) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                names.add(p.getName());
            }
            return filter(names, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            List<String> tiers = new ArrayList<>();
            for (WandTier tier : WandTier.values()) {
                tiers.add(tier.id());
            }
            return filter(tiers, args[2]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(option);
            }
        }
        return out;
    }
}
