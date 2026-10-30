package cn.linmumua.buildingwand.wand.undo;

import cn.linmumua.buildingwand.wand.Pos;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 撤销历史：每个玩家保留最近 N 次操作（默认 3 次，见 config.yml 的 misc.undo-history）。
 *
 * <p>一条 {@link Change} 记录「这次操作把某个格子从 before 变成了 after，
 * 以及消耗掉的那个物品」，撤销时反向执行即可。</p>
 *
 * <p>容量语义由 {@link BoundedHistory} 保证：超出容量时挤掉最旧的一次，
 * 撤销永远从最近一次开始，逐次向前。</p>
 */
public final class UndoHistory {

    /** 单格变化。returned 为 null 表示撤销时不需要退还物品。 */
    public record Change(Pos pos, BlockData before, BlockData after, ItemStack returned) {
    }

    /** 一次完整的之杖操作。 */
    public record Operation(List<Change> changes, boolean destroy) {
    }

    /** 每个玩家保留最近 N 次操作，N 来自配置（默认 3）。 */
    private final BoundedHistory<Operation> history;

    public UndoHistory(int capacity) {
        this.history = new BoundedHistory<>(capacity);
    }

    /**
     * 记录一次操作，覆盖掉该玩家此前记录的那一次。
     * 空操作（没有实际改动任何方块）不覆盖，避免一次无效点击把可撤销的记录冲掉。
     */
    public void push(Player player, Operation operation) {
        if (operation.changes().isEmpty()) {
            return;
        }
        history.push(player.getUniqueId(), operation);
    }

    /**
     * 撤销该玩家最近一次操作。撤销后记录即被清除，再调用会返回 0。
     *
     * @return 撤销的方块数；0 表示没有可撤销的操作
     */
    public int undo(Player player) {
        Operation operation = history.pollLatest(player.getUniqueId());
        if (operation == null) {
            return 0;
        }
        World world = player.getWorld();
        List<ItemStack> refunds = new ArrayList<>();

        // 逆序还原，保证同一格被多次改动时也能回到最初状态
        List<Change> changes = operation.changes();
        for (int i = changes.size() - 1; i >= 0; i--) {
            Change change = changes.get(i);
            Pos pos = change.pos();
            world.getBlockAt(pos.x(), pos.y(), pos.z()).setBlockData(change.before(), false);
            if (change.returned() != null) {
                refunds.add(change.returned());
            }
        }

        for (ItemStack refund : refunds) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(refund);
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
        }
        return changes.size();
    }

    /**
     * 只读地取最近一次可撤销的操作，不移除记录。
     *
     * <p>给撤销预览用：玩家蹲下时显示「下一次撤销会回滚哪些方块」，
     * 松手后仍然要能真的撤销，所以这里绝不能消耗记录。</p>
     *
     * @return 没有可撤销内容时返回 null
     */
    public Operation peekLatest(Player player) {
        return history.peekLatest(player.getUniqueId());
    }

    public void clear(Player player) {
        history.clear(player.getUniqueId());
    }
}
