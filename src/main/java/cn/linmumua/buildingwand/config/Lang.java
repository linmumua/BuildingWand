package cn.linmumua.buildingwand.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.io.File;

/**
 * 语言文件。全中文 MiniMessage，支持占位符替换。
 */
public final class Lang {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private YamlDocument doc;
    private String prefix = "";

    private Lang() {
    }

    public static Lang load(File dataFolder, ClassLoader loader) {
        Lang lang = new Lang();
        lang.reload(dataFolder, loader);
        return lang;
    }

    /** 原地重载语言文件。 */
    public void reload(File dataFolder, ClassLoader loader) {
        this.doc = YamlDocument.loadOrCreate(
                new File(dataFolder, "lang/zh_CN.yml").toPath(),
                YamlDocument.readResource(loader, "lang/zh_CN.yml"));
        this.prefix = doc.getString("prefix", "");
    }

    /** 原始文本（不含前缀）。 */
    public String raw(String key) {
        return doc.getString(key, key);
    }

    public String prefixedRaw(String key) {
        return prefix + raw(key);
    }

    /** 带占位符的组件。pairs 是 key, value 交替。 */
    public Component msg(String key, String... pairs) {
        return render(prefixedRaw(key), pairs);
    }

    /** 不带前缀的组件（用于 GUI 标题/物品名）。 */
    public Component plain(String key, String... pairs) {
        return render(raw(key), pairs);
    }

    public Component render(String text, String... pairs) {
        if (pairs.length == 0) {
            return MINI.deserialize(text);
        }
        TagResolver[] resolvers = new TagResolver[pairs.length / 2];
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            resolvers[i / 2] = Placeholder.parsed(pairs[i], pairs[i + 1]);
        }
        return MINI.deserialize(text, TagResolver.resolver(resolvers));
    }
}
