package cn.linmumua.buildingwand.compat;

import cn.linmumua.buildingwand.item.WandTier;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 代码与资源包之间的 id 契约。
 *
 * <p>为什么值得专门写测试：物品 id 在三个地方各写了一遍 —— 资源包的 YAML 键、
 * {@link WandTier#customItemId()}、{@link CraftEngineHook} 的反查逻辑。任何一处
 * 拼错，离线阶段完全看不出来，只在进游戏输入 /bw give 时才炸成「拿不到物品」。
 * 这类跨文件字符串契约正好适合用测试锁死。</p>
 */
class CePackContractTest {

    private static final String PACK_ROOT = "craftengine/buildingwand/";
    private static final String ITEMS = PACK_ROOT + "configuration/items/";
    private static final String MODELS = PACK_ROOT
            + "resourcepack/assets/buildingwand/models/item/";
    private static final String TEXTURES = PACK_ROOT
            + "resourcepack/assets/buildingwand/textures/item/";

    @Test
    void 三档之杖的物品id必须与代码一致() {
        Map<String, Object> items = items("wands.yml");
        for (WandTier tier : WandTier.values()) {
            assertTrue(items.containsKey(tier.customItemId()),
                    "wands.yml 缺少 " + tier.customItemId());
        }
    }

    @Test
    void 三档之杖的模型与贴图必须存在于资源包里() {
        Map<String, Object> items = items("wands.yml");
        for (WandTier tier : WandTier.values()) {
            Object entry = items.get(tier.customItemId());
            assertTrue(entry instanceof Map, tier.customItemId() + " 的定义结构异常");
            Map<?, ?> model = (Map<?, ?>) ((Map<?, ?>) entry).get("model");
            assertNotNull(model, tier.customItemId() + " 缺少 model 段");

            String path = String.valueOf(model.get("path"));
            String expected = "buildingwand:item/" + tier.id() + "_wand";
            assertTrue(expected.equals(path),
                    tier.customItemId() + " 的模型路径应为 " + expected + "，实际 " + path);

            // 模型文件与贴图都必须真实存在，否则 CE 会在加载时静默跳过该物品
            assertNotNull(resource(MODELS + tier.id() + "_wand.json"),
                    "缺少模型文件 " + tier.id() + "_wand.json");
            assertNotNull(resource(TEXTURES + tier.id() + "_wand.png"),
                    "缺少贴图文件 " + tier.id() + "_wand.png");
        }
    }


    @Test
    void 资源包清单必须覆盖所有实际文件() {
        String index = new String(bytes(PACK_ROOT + "_index.txt"), StandardCharsets.UTF_8);
        assertTrue(index.contains("configuration/items/wands.yml"), "清单缺少 wands.yml");
        assertTrue(index.contains("pack.yml"), "清单缺少 pack.yml");
        for (WandTier tier : WandTier.values()) {
            assertTrue(index.contains("models/item/" + tier.id() + "_wand.json"),
                    "清单缺少模型 " + tier.id() + "_wand.json");
            assertTrue(index.contains("textures/item/" + tier.id() + "_wand.png"),
                    "清单缺少贴图 " + tier.id() + "_wand.png");
        }
    }

    // ---------------------------------------------------------------- 工具

    @SuppressWarnings("unchecked")
    private static Map<String, Object> items(String file) {
        Object loaded = new Yaml().load(new String(bytes(ITEMS + file), StandardCharsets.UTF_8));
        Map<String, Object> root = (Map<String, Object>) loaded;
        assertNotNull(root, file + " 解析结果为空");
        Object items = root.get("items");
        assertTrue(items instanceof Map, file + " 缺少 items 根键");
        return (Map<String, Object>) items;
    }

    private static byte[] bytes(String path) {
        try (InputStream in = resource(path)) {
            assertNotNull(in, "缺少资源 " + path);
            return in.readAllBytes();
        } catch (Exception e) {
            throw new AssertionError("读取资源失败: " + path, e);
        }
    }

    private static InputStream resource(String path) {
        return CePackContractTest.class.getClassLoader().getResourceAsStream(path);
    }
}
