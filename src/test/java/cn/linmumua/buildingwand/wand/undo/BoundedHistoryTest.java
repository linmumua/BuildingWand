package cn.linmumua.buildingwand.wand.undo;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 锁死撤销历史的容量语义。
 *
 * <p>为什么值得单独测：容量边界写错在游戏里极难察觉——差一次要多按好几次才发现，
 * 挤错了「最旧」还是「最新」则会撤销到错误的操作上。这里把三条规则钉死：
 * 保留最近 N 次、超出挤掉最旧、从最近一次开始逐次往前撤。</p>
 */
class BoundedHistoryTest {

    @Test
    void 默认保留三次() {
        BoundedHistory<String> history = new BoundedHistory<>(3);
        UUID player = UUID.randomUUID();

        history.push(player, "第1次");
        history.push(player, "第2次");
        history.push(player, "第3次");

        assertEquals(3, history.size(player), "三次操作都应保留");
    }

    @Test
    void 超出容量时挤掉最旧的一次() {
        BoundedHistory<String> history = new BoundedHistory<>(3);
        UUID player = UUID.randomUUID();

        history.push(player, "第1次");
        history.push(player, "第2次");
        history.push(player, "第3次");
        history.push(player, "第4次");

        assertEquals(3, history.size(player), "超过容量后仍只保留 3 条");
        // 从最近往前撤：第4、第3、第2；第1 已被挤掉
        assertEquals("第4次", history.pollLatest(player));
        assertEquals("第3次", history.pollLatest(player));
        assertEquals("第2次", history.pollLatest(player));
        assertNull(history.pollLatest(player), "被挤掉的最旧一次不应还能撤销");
    }

    @Test
    void 可以逐次往前撤三次() {
        BoundedHistory<String> history = new BoundedHistory<>(3);
        UUID player = UUID.randomUUID();

        history.push(player, "第1次");
        history.push(player, "第2次");
        history.push(player, "第3次");

        assertEquals("第3次", history.pollLatest(player), "第一次撤销应是最新那次");
        assertEquals("第2次", history.pollLatest(player), "再撤一次应到上一次");
        assertEquals("第1次", history.pollLatest(player), "第三次应撤到最早保留的那次");
        assertNull(history.pollLatest(player), "撤完三次后没有可撤销的内容");
    }

    @Test
    void 容量为一只能撤最近一次() {
        BoundedHistory<String> history = new BoundedHistory<>(1);
        UUID player = UUID.randomUUID();

        history.push(player, "第1次");
        history.push(player, "第2次");

        assertEquals("第2次", history.pollLatest(player));
        assertNull(history.pollLatest(player), "容量为 1 时不应能往更早撤");
    }

    @Test
    void 不同玩家互不影响() {
        BoundedHistory<String> history = new BoundedHistory<>(3);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        history.push(a, "A 的操作");
        history.push(b, "B 的操作");

        assertEquals("A 的操作", history.pollLatest(a));
        assertEquals("B 的操作", history.pollLatest(b), "取走 A 的记录不应影响 B");
    }

    @Test
    void 清除后不可撤销() {
        BoundedHistory<String> history = new BoundedHistory<>(3);
        UUID player = UUID.randomUUID();

        history.push(player, "一次操作");
        history.clear(player);

        assertNull(history.pollLatest(player), "玩家退出或重置后不应还能撤销");
    }

    @Test
    void 容量非法时收敛为一() {
        assertEquals(1, new BoundedHistory<String>(0).capacity(), "容量 0 应被收敛为 1");
        assertEquals(1, new BoundedHistory<String>(-5).capacity(), "负容量应被收敛为 1");
    }
}
