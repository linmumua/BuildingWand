package cn.linmumua.buildingwand.wand.undo;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 「每个玩家保留最近 N 次」的有界存取器。
 *
 * <p>单独抽出来是为了让「保留几次、撤到第几次为止」这条规则能被单元测试直接锁死：
 * {@link UndoHistory} 本体依赖 Bukkit 的 World / Player / BlockData，测试跑不起来，
 * 而容量边界恰恰是最容易写错、又最难在游戏里察觉的地方——差一次要多按好几次才能发现。</p>
 *
 * @param <T> 槽位里存的东西
 */
final class BoundedHistory<T> {

    private final Map<UUID, Deque<T>> histories = new HashMap<>();
    private final int capacity;

    BoundedHistory(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    /** 追加一次记录；超出容量时挤掉最旧的那次。 */
    void push(UUID id, T value) {
        Deque<T> deque = histories.computeIfAbsent(id, key -> new ArrayDeque<>());
        deque.addLast(value);
        while (deque.size() > capacity) {
            deque.removeFirst();
        }
    }

    /** 取出并移除最近一次记录。返回 null 表示没有可撤销的内容。 */
    T pollLatest(UUID id) {
        Deque<T> deque = histories.get(id);
        if (deque == null || deque.isEmpty()) {
            return null;
        }
        T value = deque.removeLast();
        if (deque.isEmpty()) {
            histories.remove(id);
        }
        return value;
    }

    /**
     * 只读地看一眼最近一次记录，不移除。
     *
     * <p>撤销预览每 4 tick 就要问一次「下次撤销会回滚哪些方块」，
     * 用 {@link #pollLatest} 会把记录吃掉，玩家只是瞄一眼就再也撤不回去了。</p>
     */
    T peekLatest(UUID id) {
        Deque<T> deque = histories.get(id);
        if (deque == null || deque.isEmpty()) {
            return null;
        }
        return deque.peekLast();
    }

    /** 当前保留的条数，仅用于测试与诊断。 */
    int size(UUID id) {
        Deque<T> deque = histories.get(id);
        return deque == null ? 0 : deque.size();
    }

    void clear(UUID id) {
        histories.remove(id);
    }

    int capacity() {
        return capacity;
    }
}
