package cn.linmumua.buildingwand.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

/**
 * 运行期注册的指令。
 *
 * <p>为什么不用 plugin.yml：TabooLib 的 description DSL 不提供 commands 段落，
 * 而 {@code PluginCommand} 的构造器是包内可见的，所以这里直接继承 {@link Command}，
 * 由插件在 onEnable 时注册进 CommandMap。</p>
 */
public final class DynamicCommand extends Command {

    private final CommandExecutor executor;
    private final TabCompleter completer;

    public DynamicCommand(String name, String description, String usage,
                          List<String> aliases, CommandExecutor executor, TabCompleter completer) {
        super(name, description, usage, aliases);
        this.executor = executor;
        this.completer = completer;
        setPermission("buildingwand.use");
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        return executor.onCommand(sender, this, commandLabel, args);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        List<String> result = completer.onTabComplete(sender, this, alias, args);
        return result == null ? List.of() : result;
    }
}
