package cn.linmumua.buildingwand.config;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SnakeYAML 小工具：读取、与默认值深度合并补全、写回。
 * 运行期只使用 org.yaml:snakeyaml，不使用 Bukkit 的 YamlConfiguration。
 */
public final class YamlDocument {

    private static final Yaml YAML = new Yaml(options());

    private final Map<String, Object> root;

    private YamlDocument(Map<String, Object> root) {
        this.root = root;
    }

    private static DumperOptions options() {
        DumperOptions o = new DumperOptions();
        o.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        o.setIndent(2);
        o.setAllowUnicode(true);
        return o;
    }

    /** 从插件 jar 内的资源读取默认值。 */
    public static Map<String, Object> readResource(ClassLoader loader, String name) {
        try (InputStream in = loader.getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException("缺少内置资源: " + name);
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return asMap(YAML.load(reader));
            }
        } catch (IOException e) {
            throw new IllegalStateException("读取内置资源失败: " + name, e);
        }
    }

    /**
     * 加载文件；缺失时用默认值创建，存在时把默认值里有、文件里没有的键补进去。
     *
     * @return 补全后的文档
     */
    public static YamlDocument loadOrCreate(Path file, Map<String, Object> defaults) {
        Map<String, Object> merged;
        boolean changed = false;
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                Map<String, Object> loaded = asMap(YAML.load(reader));
                changed = mergeMissing(loaded, defaults);
                merged = loaded;
            } catch (IOException e) {
                throw new IllegalStateException("读取配置失败: " + file, e);
            }
        } else {
            merged = deepCopy(defaults);
            changed = true;
        }

        YamlDocument doc = new YamlDocument(merged);
        if (changed) {
            doc.save(file);
        }
        return doc;
    }

    public void save(Path file) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, YAML.dump(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("写入配置失败: " + file, e);
        }
    }

    /** 按点分路径取值。 */
    public Object get(String path) {
        Object cur = root;
        for (String part : path.split("\\.")) {
            if (!(cur instanceof Map<?, ?> map)) {
                return null;
            }
            cur = map.get(part);
        }
        return cur;
    }

    public String getString(String path, String def) {
        Object v = get(path);
        return v == null ? def : String.valueOf(v);
    }

    public int getInt(String path, int def) {
        Object v = get(path);
        return v instanceof Number n ? n.intValue() : def;
    }

    public double getDouble(String path, double def) {
        Object v = get(path);
        return v instanceof Number n ? n.doubleValue() : def;
    }

    public boolean getBoolean(String path, boolean def) {
        Object v = get(path);
        return v instanceof Boolean b ? b : def;
    }

    /** 读取扁平的字符串列表。 */
    public List<String> getStringList(String path) {
        Object v = get(path);
        List<String> out = new ArrayList<>();
        if (v instanceof List<?> list) {
            for (Object o : list) {
                out.add(String.valueOf(o));
            }
        }
        return out;
    }

    /**
     * 读取「分组」列表：每个元素本身是一组字符串。
     * 用于 similar-blocks —— 一行的多个 id 属于同一组。
     */
    public List<List<String>> getStringGroups(String path) {
        Object v = get(path);
        List<List<String>> out = new ArrayList<>();
        if (!(v instanceof List<?> list)) {
            return out;
        }
        for (Object row : list) {
            List<String> group = new ArrayList<>();
            if (row instanceof List<?> inner) {
                for (Object cell : inner) {
                    group.add(String.valueOf(cell));
                }
            } else {
                group.add(String.valueOf(row));
            }
            if (!group.isEmpty()) {
                out.add(group);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ 内部

    /**
     * 把 SnakeYAML 解析出来的 map 归一化：<b>所有键强制转成 String</b>。
     *
     * <p>为什么必须转换而不是直接强转：SnakeYAML 按 YAML 1.1 语义解析，
     * {@code on} / {@code off} / {@code yes} / {@code no} 这类裸键会被解析成
     * Boolean 而不是 String。这时 {@code (Map<String, Object>) map} 是个假强转
     * （泛型被擦除，编译期不报错），一旦遍历到这些键就抛
     * ClassCastException: Boolean cannot be cast to String —— 插件会在 onEnable
     * 直接崩掉，而堆栈只指向这里的 for 循环，很难看出是 YAML 语义导致的。
     * 归一化之后，键叫什么都不会崩，玩家在配置里写 on: 也只是变成一个普通键。</p>
     */
    private static Map<String, Object> asMap(Object loaded) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (!(loaded instanceof Map<?, ?> map)) {
            return out;
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            out.put(String.valueOf(entry.getKey()), normalizeValue(entry.getValue()));
        }
        return out;
    }

    /** 递归归一化嵌套结构，保证任意深度的键都是 String。 */
    private static Object normalizeValue(Object value) {
        if (value instanceof Map<?, ?>) {
            return asMap(value);
        }
        if (value instanceof List<?> list) {
            List<Object> normalized = new ArrayList<>(list.size());
            for (Object element : list) {
                normalized.add(normalizeValue(element));
            }
            return normalized;
        }
        return value;
    }

    /** 把 defaults 中缺失的键补进 target；返回是否发生改动。 */
    private static boolean mergeMissing(Map<String, Object> target, Map<String, Object> defaults) {
        boolean changed = false;
        for (Map.Entry<String, Object> e : defaults.entrySet()) {
            String key = e.getKey();
            Object def = e.getValue();
            if (!target.containsKey(key)) {
                target.put(key, deepCopyValue(def));
                changed = true;
                continue;
            }
            Object cur = target.get(key);
            if (cur instanceof Map<?, ?> curMap && def instanceof Map<?, ?> defMap) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cm = (Map<String, Object>) curMap;
                @SuppressWarnings("unchecked")
                Map<String, Object> dm = (Map<String, Object>) defMap;
                changed |= mergeMissing(cm, dm);
            }
        }
        return changed;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopy(Map<String, Object> src) {
        return (Map<String, Object>) deepCopyValue(src);
    }

    @SuppressWarnings("unchecked")
    private static Object deepCopyValue(Object v) {
        if (v instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                out.put(String.valueOf(e.getKey()), deepCopyValue(e.getValue()));
            }
            return out;
        }
        if (v instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object o : list) {
                out.add(deepCopyValue(o));
            }
            return out;
        }
        return v;
    }
}
