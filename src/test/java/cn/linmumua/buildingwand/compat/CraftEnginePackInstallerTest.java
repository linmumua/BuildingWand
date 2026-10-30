package cn.linmumua.buildingwand.compat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 资源包升级时的目录收敛。
 *
 * <p>回归背景：安装器是「按清单写入」的，只写不删。当包里的文件名发生变化
 * （早期叫 wand_stone.json，后来改成模组原名 stone_wand.json），旧文件会留在
 * 目标目录里，CraftEngine 会把两套命名都加载进来。实服上就出现过这种新旧混杂，
 * 所以这里必须锁住「清单之外的文件会被清掉」。</p>
 */
class CraftEnginePackInstallerTest {

    private static void write(Path root, String relative) throws Exception {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "x", StandardCharsets.UTF_8);
    }

    @Test
    void 清单之外的旧文件必须被清除(@TempDir Path dir) throws Exception {
        write(dir, "pack.yml");
        write(dir, "configuration/items/wands.yml");
        // 上一版遗留的旧命名
        write(dir, "resourcepack/assets/buildingwand/models/item/wand_stone.json");
        write(dir, "resourcepack/assets/buildingwand/textures/item/wand_stone.png");

        boolean removed = CraftEnginePackInstaller.removeStaleFiles(dir, List.of(
                "pack.yml",
                "configuration/items/wands.yml",
                "resourcepack/assets/buildingwand/models/item/stone_wand.json"
        ));

        assertTrue(removed, "存在多余文件时必须报告已清理");
        assertFalse(Files.exists(dir.resolve(
                        "resourcepack/assets/buildingwand/models/item/wand_stone.json")),
                "旧的 wand_stone.json 必须被删除");
        assertFalse(Files.exists(dir.resolve(
                        "resourcepack/assets/buildingwand/textures/item/wand_stone.png")),
                "旧的 wand_stone.png 必须被删除");
        assertTrue(Files.exists(dir.resolve("pack.yml")), "清单内的文件不能被误删");
    }

    @Test
    void 目录干净时不应报告变化(@TempDir Path dir) throws Exception {
        write(dir, "pack.yml");
        write(dir, "configuration/items/wands.yml");

        boolean removed = CraftEnginePackInstaller.removeStaleFiles(dir, List.of(
                "pack.yml",
                "configuration/items/wands.yml"));

        assertFalse(removed, "没有多余文件时不应报告变化，否则每次启动都会无谓重写");
    }

    @Test
    void 目标目录不存在时应安全返回(@TempDir Path dir) throws Exception {
        Path missing = dir.resolve("not-there");
        assertFalse(CraftEnginePackInstaller.removeStaleFiles(missing, List.of("pack.yml")));
    }

    @Test
    void 只清理本包目录不越界(@TempDir Path dir) throws Exception {
        // 同级还有一个别的资源包，绝不能被碰到
        Path mine = dir.resolve("buildingwand");
        Path other = dir.resolve("betternether");
        write(mine, "pack.yml");
        write(other, "pack.yml");
        write(other, "configuration/items/keep.yml");

        CraftEnginePackInstaller.removeStaleFiles(mine, List.of("pack.yml"));

        assertTrue(Files.exists(other.resolve("pack.yml")), "不得影响其它资源包");
        assertTrue(Files.exists(other.resolve("configuration/items/keep.yml")),
                "不得影响其它资源包的配置");
    }
}
