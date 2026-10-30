package cn.linmumua.buildingwand.wand;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 预览「这一轮要不要重建实体」的判定依据。
 *
 * <p>为什么要测：这个哈希用来跳过重建。一旦判等出错——该重建时不重建——表现是
 * 「预览偶尔停在旧位置」，只在特定形状下偶发，靠肉眼和日志都几乎查不出来。
 * 因此必须保证：位置集合相同则哈希相同；任何一格、任何一维变了，哈希必须变。</p>
 */
class PosHashTest {

    @Test
    void 相同位置集合哈希相同() {
        List<Pos> a = List.of(new Pos(0, 1, 0), new Pos(1, 1, 0), new Pos(2, 1, 0));
        List<Pos> b = List.of(new Pos(0, 1, 0), new Pos(1, 1, 0), new Pos(2, 1, 0));

        assertEquals(PosHash.of(a), PosHash.of(b),
                "同样的位置集合必须得到同样的哈希，否则会无谓重建");
    }

    @Test
    void 任一格子变化哈希必须改变() {
        List<Pos> base = List.of(new Pos(0, 1, 0), new Pos(1, 1, 0), new Pos(2, 1, 0));
        long expected = PosHash.of(base);

        // 分别从 x / y / z 三个方向各偏一格；任何一维漏进哈希都会被抓到
        assertNotEquals(expected, PosHash.of(List.of(new Pos(9, 1, 0), new Pos(1, 1, 0), new Pos(2, 1, 0))),
                "x 变化必须改变哈希");
        assertNotEquals(expected, PosHash.of(List.of(new Pos(0, 9, 0), new Pos(1, 1, 0), new Pos(2, 1, 0))),
                "y 变化必须改变哈希");
        assertNotEquals(expected, PosHash.of(List.of(new Pos(0, 1, 9), new Pos(1, 1, 0), new Pos(2, 1, 0))),
                "z 变化必须改变哈希");
    }

    @Test
    void 格子数量不同哈希必须改变() {
        List<Pos> three = List.of(new Pos(0, 0, 0), new Pos(1, 0, 0), new Pos(2, 0, 0));
        List<Pos> four = List.of(new Pos(0, 0, 0), new Pos(1, 0, 0), new Pos(2, 0, 0), new Pos(3, 0, 0));

        assertNotEquals(PosHash.of(three), PosHash.of(four));
    }

    @Test
    void 空集合哈希稳定且不抛异常() {
        assertEquals(PosHash.of(List.of()), PosHash.of(new ArrayList<>()));
    }

    @Test
    void 长条预览的相邻坐标不会碰撞() {
        // 模拟钻石档铺出的一长排，确认 64 个相邻坐标彼此哈希不同
        List<Long> hashes = new ArrayList<>();
        for (int x = 0; x < 64; x++) {
            hashes.add(PosHash.of(List.of(new Pos(0, 0, 0), new Pos(x, 0, 0))));
        }
        assertEquals(64, hashes.stream().distinct().count(), "64 个不同坐标不应产生哈希碰撞");
    }
}
