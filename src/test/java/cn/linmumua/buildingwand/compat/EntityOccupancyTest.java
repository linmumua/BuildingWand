package cn.linmumua.buildingwand.compat;

import cn.linmumua.buildingwand.wand.Pos;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 包围盒 → 方块集合的换算规则。
 *
 * <p>为什么值得单独测：预览过滤与落块拒绝都建立在这个换算上，而它只有四条规则
 * ——半开区间、max 落在整数上不含该格、负数按 floor 落格、零体积返回空集。
 * 任何一条写错，症状都是「某类位置永远放不了」或「贴着墙能塞进半格」，
 * 在游戏里只能靠反复挪位置去试，因此在这里锁死。</p>
 *
 * <p>本测试刻意只碰纯数学方法，不引用任何 Bukkit 类：测试运行期 classpath 里
 * 没有 Paper API（见 build.gradle.kts 的 pureTestRuntime），一旦被测方法沾上
 * 服务端类型，这个测试会直接加载不了。</p>
 */
class EntityOccupancyTest {

    @Test
    void 整格对齐的包围盒只覆盖一格() {
        // 方块 (5,5,5) 的占用范围就是 [5,6)³，正好等于这个包围盒
        assertEquals(Set.of(new Pos(5, 5, 5)),
                EntityOccupancy.coveredBlocks(5, 5, 5, 6, 6, 6));
    }

    @Test
    void 半格偏移的包围盒覆盖四格() {
        // x 与 z 各横跨两个方块，y 只占一层：2 × 1 × 2 = 4 格
        assertEquals(Set.of(
                        new Pos(0, 0, 0), new Pos(0, 0, 1),
                        new Pos(1, 0, 0), new Pos(1, 0, 1)),
                EntityOccupancy.coveredBlocks(0.5, 0, 0.5, 1.5, 1, 1.5));
    }

    @Test
    void 最大值落在整数边界时不包含该格() {
        // [0,2) 只压到方块 0 和 1；若误用 floor(max) 就会多算一格 2
        assertEquals(Set.of(new Pos(0, 0, 0), new Pos(1, 0, 0)),
                EntityOccupancy.coveredBlocks(0, 0, 0, 2, 1, 1));

        // z 轴同理，确认不是只在 x 上做对了
        assertEquals(Set.of(new Pos(0, 0, 0), new Pos(0, 0, 1), new Pos(0, 0, 2)),
                EntityOccupancy.coveredBlocks(0, 0, 0, 1, 1, 3));
    }

    @Test
    void 负数坐标按向下取整落格() {
        // [-1.5,-0.5) 落在方块 -2 与 -1 里；若用截断取整会算成 -1 与 0
        assertEquals(Set.of(new Pos(-2, 0, 0), new Pos(-1, 0, 0)),
                EntityOccupancy.coveredBlocks(-1.5, 0, 0, -0.5, 1, 1));
    }

    @Test
    void 零体积包围盒覆盖不到任何方块() {
        // 点在方块里也不算「占住这一格」：区间是半开的，退化成点时长度为 0
        assertTrue(EntityOccupancy.coveredBlocks(1, 1, 1, 1, 1, 1).isEmpty());

        // 只有一个轴退化也要判空：min = max = 0.5 时若硬套公式会算出方块 0
        assertTrue(EntityOccupancy.coveredBlocks(0.5, 0, 0, 0.5, 1, 1).isEmpty());
    }

    @Test
    void 高于一格的包围盒同时占住上下两格() {
        // 玩家/家具碰撞箱常是 0.6 × 1.8 这类尺寸，落地时会跨 y=0 与 y=1
        assertEquals(Set.of(new Pos(0, 0, 0), new Pos(0, 1, 0)),
                EntityOccupancy.coveredBlocks(0.2, 0, 0.2, 0.8, 1.8, 0.8));
    }
}
