package cn.linmumua.buildingwand.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 配置释放与补全。
 *
 * <p>为什么必须测：插件首次启动要「自动释放 config.yml」，缺失项要「自动补全」。
 * 这两件事一旦失效，表现是「配置文件凭空消失」这种很难从日志看出来的问题——
 * 现场就出现过 lang 写了、config 没写的情况。这里把行为钉死。</p>
 */
class YamlDocumentTest {

    private static final String CONFIG_RESOURCE = "config.yml";

    private Map<String, Object> defaults() {
        return YamlDocument.readResource(getClass().getClassLoader(), CONFIG_RESOURCE);
    }

    @Test
    void 首次加载应写出配置文件(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.yml");
        assertFalse(Files.exists(file), "前置条件：文件不应存在");

        YamlDocument.loadOrCreate(file, defaults());

        assertTrue(Files.isRegularFile(file), "首次加载必须释放 config.yml");
        assertTrue(Files.size(file) > 0, "释放出来的配置不应为空");
    }

    @Test
    void 释放出的配置能被重新解析且关键项齐全(@TempDir Path dir) {
        Path file = dir.resolve("config.yml");
        YamlDocument doc = YamlDocument.loadOrCreate(file, defaults());

        // 三档之杖都在
        for (String tier : List.of("stone", "iron", "diamond")) {
            assertTrue(doc.getInt("wand." + tier + ".limit", -1) > 0,
                    "缺少 wand." + tier + ".limit");
        }
        assertEquals(128, doc.getInt("wand.diamond.limit", -1));
        assertEquals(100, doc.getInt("misc.max-range", -1));
        assertFalse(doc.getStringGroups("similar-blocks").isEmpty(),
                "similar-blocks 应当被解析成至少一个分组");
    }

    @Test
    void 已存在的配置缺失项应被自动补全并保留原有值(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.yml");
        // 只写一个残缺配置：有 magic 自定义值，且缺 misc 整段
        Files.writeString(file, """
                wand:
                  stone:
                    durability: 999999
                """, StandardCharsets.UTF_8);

        YamlDocument doc = YamlDocument.loadOrCreate(file, defaults());

        assertEquals(999999, doc.getInt("wand.stone.durability", -1),
                "玩家改过的值不能被默认值覆盖");
        assertEquals(27, doc.getInt("wand.iron.limit", -1),
                "缺失的键必须从默认值补全");
        assertEquals(100, doc.getInt("misc.max-range", -1),
                "缺失的整段也必须补全");

        // 补全结果要落盘，否则下次启动又会缺
        String after = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(after.contains("max-range"), "补全内容必须写回文件");
        assertTrue(after.contains("999999"), "原有值必须保留在文件里");
    }

    @Test
    void 重复加载不应改写玩家已修改的文件(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.yml");
        YamlDocument.loadOrCreate(file, defaults());
        Files.writeString(file, Files.readString(file, StandardCharsets.UTF_8)
                .replace("max-range: 100", "max-range: 42"), StandardCharsets.UTF_8);
        long before = Files.getLastModifiedTime(file).toMillis();

        YamlDocument doc = YamlDocument.loadOrCreate(file, defaults());

        assertEquals(42, doc.getInt("misc.max-range", -1));
        assertEquals(before, Files.getLastModifiedTime(file).toMillis(),
                "内容完整时不应重写文件");
    }

    @Test
    void 相似方块分组按行解析(@TempDir Path dir) {
        YamlDocument doc = YamlDocument.loadOrCreate(dir.resolve("config.yml"), defaults());
        List<List<String>> groups = doc.getStringGroups("similar-blocks");

        assertFalse(groups.isEmpty(), "必须至少解析出一个分组");
        assertTrue(groups.get(0).contains("minecraft:dirt"), "首个分组应包含泥土");
        assertTrue(groups.get(0).contains("minecraft:grass_block"), "泥土组应包含草方块");
    }

    /**
     * 回归：语言文件里曾出现裸键 {@code on:} / {@code off:}。
     * SnakeYAML 按 YAML 1.1 把这类裸键解析成 Boolean，于是 map 里混进非 String 键，
     * 补全时遍历直接抛 ClassCastException，插件在 onEnable 就崩了。
     * 这里同时锁两件事：任何文件的键都能安全遍历，且语言文件能取到开关文案。
     */
    @Test
    void 语言文件里的裸键不能被YAML旧语义弄崩() {
        Map<String, Object> langDefaults =
                YamlDocument.readResource(getClass().getClassLoader(), "lang/zh_CN.yml");

        assertFalse(langDefaults.isEmpty(), "语言文件不应解析为空");
        for (Map.Entry<String, Object> entry : langDefaults.entrySet()) {
            assertTrue(entry.getKey() instanceof String,
                    "键必须是字符串，实际是 " + entry.getKey().getClass().getName());
        }
        // 开关文案必须能取到——这正是当初崩掉的那两个键
        assertTrue(langDefaults.containsKey("state-on"), "缺少 state-on");
        assertTrue(langDefaults.containsKey("state-off"), "缺少 state-off");
    }

    @Test
    void 配置文件里的歧义键也不会导致崩溃(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.yml");
        // 玩家完全可能写出 on: / yes: 这种键，必须只是变成普通字符串键，而不是崩掉
        Files.writeString(file, """
                on: true
                yes: 1
                wand:
                  stone:
                    durability: 5
                """, StandardCharsets.UTF_8);

        YamlDocument doc = YamlDocument.loadOrCreate(file, defaults());

        assertEquals(5, doc.getInt("wand.stone.durability", -1), "已有值必须保留");
        assertEquals(100, doc.getInt("misc.max-range", -1), "缺失项仍要能补全");
    }
}
