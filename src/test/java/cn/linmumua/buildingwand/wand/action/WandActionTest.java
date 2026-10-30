package cn.linmumua.buildingwand.wand.action;

import cn.linmumua.buildingwand.wand.BlockMatcher;
import cn.linmumua.buildingwand.wand.Face;
import cn.linmumua.buildingwand.wand.LockMode;
import cn.linmumua.buildingwand.wand.Pos;
import cn.linmumua.buildingwand.wand.WorldView;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 锁死三个核心的算法形状。
 * 这些断言编码的是「原模组的手感」：面要铺满、锁要限制成一排、破坏要挡住视线、
 * 天使要穿到背面。改动算法若破坏了这些意图，测试必须失败。
 */
class WandActionTest {

    private static final String STONE = "minecraft:stone";
    private static final String DIRT = "minecraft:dirt";

    /** EXACT 匹配：双方 id 相同，且都不是空气。 */
    private static final BlockMatcher EXACT =
            (target, candidate) -> !WorldView.AIR.equals(target) && target.equals(candidate);
    /** ANY 匹配：只要双方不是空气。 */
    private static final BlockMatcher ANY =
            (target, candidate) -> !WorldView.AIR.equals(target) && !WorldView.AIR.equals(candidate);

    // ---------------------------------------------------------------- 建造核心

    @Test
    void 建造核心应铺满整个同质面() {
        GridWorld world = new GridWorld();
        // 3x3 石台，顶面朝上
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                world.set(new Pos(x, 0, z), STONE);
            }
        }

        List<Pos> result = new ConstructionAction()
                .compute(world, new Pos(0, 0, 0), Face.UP, LockMode.NOLOCK, EXACT, 100);

        assertEquals(9, result.size(), "3x3 的面应当铺满 9 格");
        Set<Pos> set = new HashSet<>(result);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                assertTrue(set.contains(new Pos(x, 1, z)), "缺少 (" + x + ",1," + z + ")");
            }
        }
    }

    @Test
    void 建造核心在水平锁下只铺一排() {
        GridWorld world = new GridWorld();
        // 3x3 竖墙，位于 z=0，之杖从北面命中
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                world.set(new Pos(x, y, 0), STONE);
            }
        }
        Pos hit = new Pos(0, 0, 0);

        List<Pos> locked = new ConstructionAction()
                .compute(world, hit, Face.NORTH, LockMode.HORIZONTAL, EXACT, 100);
        assertEquals(3, locked.size(), "水平锁下只应铺出同一高度的一排");
        for (Pos p : locked) {
            assertEquals(0, p.y(), "水平锁不应出现竖直扩散");
            assertEquals(-1, p.z(), "结果都应贴着命中面的外侧");
        }

        List<Pos> free = new ConstructionAction()
                .compute(world, hit, Face.NORTH, LockMode.NOLOCK, EXACT, 100);
        assertEquals(9, free.size(), "无锁时应铺满整面墙");
    }

    @Test
    void 建造核心遵守上限() {
        GridWorld world = new GridWorld();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                world.set(new Pos(x, 0, z), STONE);
            }
        }

        List<Pos> result = new ConstructionAction()
                .compute(world, new Pos(0, 0, 0), Face.UP, LockMode.NOLOCK, EXACT, 4);

        assertEquals(4, result.size(), "不得超过本次上限");
    }

    @Test
    void 建造核心按匹配模式决定是否跨越异种方块() {
        GridWorld world = new GridWorld();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                world.set(new Pos(x, 0, z), STONE);
            }
        }
        // 中间换成泥土
        world.set(new Pos(1, 0, 0), DIRT);
        Pos hit = new Pos(0, 0, 0);

        List<Pos> exact = new ConstructionAction()
                .compute(world, hit, Face.UP, LockMode.NOLOCK, EXACT, 100);
        assertFalse(exact.contains(new Pos(1, 1, 0)), "EXACT 模式不应延伸到泥土上方");

        List<Pos> any = new ConstructionAction()
                .compute(world, hit, Face.UP, LockMode.NOLOCK, ANY, 100);
        assertTrue(any.contains(new Pos(1, 1, 0)), "ANY 模式应把泥土当成同质面的一部分");
    }

    // ---------------------------------------------------------------- 测试用世界

    private static final class GridWorld implements WorldView {
        private final Map<Pos, String> blocks = new HashMap<>();
        private final Set<Pos> unbreakable = new HashSet<>();

        void set(Pos pos, String id) {
            blocks.put(pos, id);
        }

        void setUnbreakable(Pos pos) {
            unbreakable.add(pos);
        }

        @Override
        public String blockAt(Pos pos) {
            return blocks.getOrDefault(pos, AIR);
        }

        @Override
        public boolean isPlaceable(Pos pos) {
            return AIR.equals(blockAt(pos));
        }

        @Override
        public boolean isPermeable(Pos pos) {
            return AIR.equals(blockAt(pos));
        }

        @Override
        public boolean isBreakable(Pos pos) {
            return !AIR.equals(blockAt(pos)) && !unbreakable.contains(pos);
        }
    }
}
