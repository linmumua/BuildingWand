package cn.linmumua.buildingwand.compat;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 把 jar 内置的 CraftEngine 资源包释放到 CraftEngine 的 resources 目录。
 *
 * <p>CE 只扫描 {@code plugins/CraftEngine/resources/<namespace>/}，不会去读别人的 jar，
 * 所以插件必须自己把包放到位。策略是「内容不一致才写」：首次进入时全量释放，
 * 之后每次启动按文件内容比对，只有包真的变了（升级）才重写，避免每次启动都产生
 * 无意义的磁盘写入。</p>
 */
public final class CraftEnginePackInstaller {

    private static final String BUNDLE_ROOT = "craftengine/buildingwand";
    static final String PREVIEW_TEXTURE_ENTRY = "resourcepack/assets/buildingwand/textures/block/preview.png";

    private CraftEnginePackInstaller() {
    }

    /**
     * 释放资源包。
     *
     * @return 是否有文件被写入（true 表示需要重载 CE 才能生效）
     */
    public static boolean install(Plugin plugin, int previewBorderAlpha) {
        Plugin craftEngine = Bukkit.getPluginManager().getPlugin("CraftEngine");
        if (craftEngine == null) {
            plugin.getLogger().info("未找到 CraftEngine，跳过资源包释放。");
            return false;
        }
        Path target = craftEngine.getDataFolder().toPath()
                .resolve("resources")
                .resolve("buildingwand");

        List<String> entries = readIndex(plugin);
        boolean changed = false;
        try {
            for (String entry : entries) {
                Path out = target.resolve(entry);
                // 预览贴图按配置的边框透明度现场生成，不用 jar 里的默认图。
                // 改了 preview.border-opacity 后 /bw reload 就会把新贴图写进 CE 目录。
                byte[] data = PREVIEW_TEXTURE_ENTRY.equals(entry)
                        ? PreviewTexture.png(previewBorderAlpha)
                        : read(plugin, BUNDLE_ROOT + "/" + entry);
                if (data == null) {
                    continue;
                }
                if (Files.isRegularFile(out) && java.util.Arrays.equals(Files.readAllBytes(out), data)) {
                    continue;
                }
                Files.createDirectories(out.getParent());
                Files.write(out, data);
                changed = true;
            }
            // 【必须清理清单之外的文件】
            // 这个安装器是「按清单写入」的，只写不删。一旦包里的文件名变了
            // （例如从 wand_stone.json 改成 stone_wand.json），旧文件会留在目录里，
            // CE 会把两套命名都加载进来，表现为物品重复或模型指向错误。
            // 这正是曾经踩过的坑，所以以清单为准做一次收敛。
            changed |= removeStaleFiles(target, entries);
        } catch (IOException e) {
            plugin.getLogger().warning("释放 CraftEngine 资源包失败: " + e.getMessage());
            return false;
        }

        if (changed) {
            plugin.getLogger().info("已释放 CraftEngine 资源包到 " + target
                    + "，请执行 /ce reload 使其生效。");
        }
        return changed;
    }

    /**
     * 删除目标目录中「不在本次清单里」的文件，避免升级后新旧两套资源同时存在。
     * 只处理本包自己目录下的内容，不碰 CraftEngine 的其它资源包。
     *
     * <p>包级可见是为了能被单元测试直接验证：这个「只增不删」的疏漏曾经让
     * 新旧两套文件名同时留在目录里，CE 会把它们都加载进来。</p>
     *
     * @return 是否真的删掉了东西（真删了才算「有变化」）
     */
    static boolean removeStaleFiles(Path target, List<String> entries) throws IOException {
        if (!Files.isDirectory(target)) {
            return false;
        }
        java.util.Set<String> expected = new java.util.HashSet<>(entries);
        boolean removed = false;
        try (var stream = Files.walk(target)) {
            List<Path> stale = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> !expected.contains(
                            target.relativize(path).toString().replace('\\', '/')))
                    .toList();
            for (Path path : stale) {
                Files.deleteIfExists(path);
                removed = true;
            }
        }
        return removed;
    }

    /**
     * 读取包内文件清单。清单由 tools/generate_ce_pack.py 在生成资源包时一并产出，
     * 放在 _index.txt 里；缺失时退回一份硬编码的最小集合，保证至少能放出物品定义。
     */
    private static List<String> readIndex(Plugin plugin) {
        List<String> entries = new ArrayList<>();
        String raw = readText(plugin, BUNDLE_ROOT + "/_index.txt");
        if (raw == null) {
            // 清单缺失时退回最小可用集合。文件名必须与 tools/generate_ce_pack.py 的产出一致
            // （<tier>_wand，而不是 wand_<tier>），否则兜底路径会放出一堆不存在的条目。
            entries.add("pack.yml");
            entries.add("configuration/categories.yml");
            entries.add("configuration/items/wands.yml");
            entries.add("configuration/items/cores.yml");
            for (String tier : new String[]{"stone", "iron", "diamond"}) {
                entries.add("resourcepack/assets/buildingwand/models/item/" + tier + "_wand.json");
                entries.add("resourcepack/assets/buildingwand/textures/item/" + tier + "_wand.png");
            }
            for (String core : new String[]{"angel", "destruction"}) {
                entries.add("resourcepack/assets/buildingwand/models/item/core_" + core + ".json");
                entries.add("resourcepack/assets/buildingwand/textures/item/core_" + core + ".png");
            }
            return entries;
        }
        for (String line : raw.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                entries.add(trimmed);
            }
        }
        return entries;
    }

    private static byte[] read(Plugin plugin, String resource) {
        try (InputStream in = plugin.getResource(resource)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    private static String readText(Plugin plugin, String resource) {
        byte[] data = read(plugin, resource);
        return data == null ? null : new String(data, StandardCharsets.UTF_8);
    }
}
