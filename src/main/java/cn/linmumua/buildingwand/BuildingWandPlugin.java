package cn.linmumua.buildingwand;

import cn.linmumua.buildingwand.command.DynamicCommand;
import cn.linmumua.buildingwand.compat.CraftEnginePackInstaller;
import cn.linmumua.buildingwand.command.WandCommand;
import cn.linmumua.buildingwand.config.Config;
import cn.linmumua.buildingwand.config.Lang;
import cn.linmumua.buildingwand.gui.OptionsGui;
import cn.linmumua.buildingwand.item.WandKeys;
import cn.linmumua.buildingwand.listener.WandListener;
import cn.linmumua.buildingwand.recipe.RecipeRegistrar;
import cn.linmumua.buildingwand.task.PreviewTask;
import cn.linmumua.buildingwand.wand.WandService;
import cn.linmumua.buildingwand.wand.undo.UndoHistory;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * 建筑之杖主入口。
 * Maintainer: linmumua
 *
 * <p>继承 Bukkit 的 {@link JavaPlugin} 而不是 TabooLib 的 BukkitPlugin：本插件用
 * paper-plugin.yml 直接把自己的类声明为 main，TabooLib 只作为库被注入。
 * 这条路线与本服其它 TabooLib 插件（MUZ）一致，也是唯一能绕开「TabooLib 包装层
 * 找不到插件实例、onEnable 永不回调」的路子——曾实测过继承包装层，日志只有
 * Enabling 而没有任何自己的输出。</p>
 */
public final class BuildingWandPlugin extends JavaPlugin {


    private Config config;
    private Lang lang;
    private WandKeys keys;
    private WandService service;
    private PreviewTask preview;
    private RecipeRegistrar recipes;

    @Override
    public void onEnable() {
        keys = new WandKeys(this);
        config = Config.load(getDataFolder(), getClassLoader());
        lang = Lang.load(getDataFolder(), getClassLoader());

        // 先把 CraftEngine 资源包释放到位，否则 /bw give 拿不到物品定义
        CraftEnginePackInstaller.install(this);

        service = new WandService(config, new UndoHistory(config.undoHistory()));

        preview = new PreviewTask(this, config, keys, service);
        preview.start();

        OptionsGui gui = new OptionsGui(config, lang, keys);
        Bukkit.getPluginManager().registerEvents(gui, this);
        Bukkit.getPluginManager().registerEvents(
                new WandListener(config, lang, keys, service, preview, gui), this);

        recipes = new RecipeRegistrar(this, config);
        Bukkit.getPluginManager().registerEvents(recipes, this);
        recipes.registerRecipes();

        registerCommands();

        getLogger().info("建筑之杖已加载。");
    }

    @Override
    public void onDisable() {
        if (preview != null) {
            preview.stop();
        }
        if (recipes != null) {
            recipes.unregisterAll();
        }
    }

    /**
     * 重载配置与语言；配方跟着重建（CE 可能在 reload 之后才就绪）。
     *
     * <p>同时重新释放一次 CraftEngine 资源包：{@code /bw reload} 是玩家遇到
     * 「物品/贴图没生效」时最先想到的命令，如果它不碰资源包，删掉目录或换了版本后
     * 就得重启整个服务端才能恢复。安装器本身是「内容不一致才写」，重复调用无副作用。</p>
     */
    public void reload() {
        config.reload(getDataFolder(), getClassLoader());
        lang.reload(getDataFolder(), getClassLoader());
        CraftEnginePackInstaller.install(this);
        recipes.registerRecipes();
    }

    private void registerCommands() {
        WandCommand executor = new WandCommand(lang, keys, service, config, this::reload);
        DynamicCommand command = new DynamicCommand(
                "buildingwand",
                "建筑之杖指令",
                "/buildingwand <give|core|undo|reload>",
                List.of("bw", "wand"),
                executor,
                executor);
        CommandMap map = Bukkit.getServer().getCommandMap();
        map.register("buildingwand", command);
    }
}
