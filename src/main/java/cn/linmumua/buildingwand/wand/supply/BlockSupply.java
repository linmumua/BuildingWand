package cn.linmumua.buildingwand.wand.supply;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 方块供给。负责「从玩家身上取出一块指定方块」，取不到就返回失败。
 */
public interface BlockSupply {

    /** 手中是否还有可提供的该方块（用于预览与上限裁剪）。 */
    boolean has(Material type);

    /** 尝试取走 1 个；成功返回 true。 */
    boolean takeOne(Material type);

    /**
     * 标准实现：副手 → 快捷栏 → 主背包。
     * 潜影盒等容器供给尚未实现（原模组的容器能力不在本期范围）。
     */
    static BlockSupply inventory(Player player) {
        return new InventorySupply(player);
    }

    /** 随机模式：从快捷栏随机挑一个方块物来放置。 */
    static BlockSupply random(Player player) {
        return new RandomSupply(player);
    }

    /** 背包供给。 */
    final class InventorySupply implements BlockSupply {
        private final Player player;

        InventorySupply(Player player) {
            this.player = player;
        }

        @Override
        public boolean has(Material type) {
            return findSlot(type) != NOT_FOUND;
        }

        @Override
        public boolean takeOne(Material type) {
            int slot = findSlot(type);
            if (slot == NOT_FOUND) {
                return false;
            }
            ItemStack stack = slotItem(slot);
            if (stack == null) {
                return false;
            }
            stack.setAmount(stack.getAmount() - 1);
            if (stack.getAmount() <= 0) {
                setSlotItem(slot, null);
            }
            return true;
        }

        /** 未找到 */
        private static final int NOT_FOUND = -1;
        /** 副手（用负值编码，避免与背包下标冲突） */
        private static final int OFFHAND = -2;

        /** 副手优先，其次快捷栏，最后主背包。 */
        private int findSlot(Material type) {
            PlayerInventory inv = player.getInventory();
            if (matches(inv.getItemInOffHand(), type)) {
                return OFFHAND;
            }
            for (int i = 0; i < inv.getSize(); i++) {
                if (matches(inv.getItem(i), type)) {
                    return i;
                }
            }
            return NOT_FOUND;
        }

        private ItemStack slotItem(int slot) {
            return slot == OFFHAND ? player.getInventory().getItemInOffHand() : player.getInventory().getItem(slot);
        }

        private void setSlotItem(int slot, ItemStack stack) {
            if (slot == OFFHAND) {
                player.getInventory().setItemInOffHand(stack);
            } else {
                player.getInventory().setItem(slot, stack);
            }
        }

        private static boolean matches(ItemStack stack, Material type) {
            return stack != null && stack.getType() == type && stack.getAmount() > 0;
        }
    }

    /** 随机供给：每次从快捷栏里随机挑一种方块。 */
    final class RandomSupply implements BlockSupply {
        private final Player player;

        RandomSupply(Player player) {
            this.player = player;
        }

        @Override
        public boolean has(Material type) {
            return !candidates().isEmpty();
        }

        @Override
        public boolean takeOne(Material type) {
            List<Integer> slots = candidates();
            if (slots.isEmpty()) {
                return false;
            }
            int slot = slots.get(ThreadLocalRandom.current().nextInt(slots.size()));
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack == null) {
                return false;
            }
            stack.setAmount(stack.getAmount() - 1);
            if (stack.getAmount() <= 0) {
                player.getInventory().setItem(slot, null);
            }
            return true;
        }

        /** 供执行层取「本次要放的方块」。 */
        public Material pickBlock() {
            List<Integer> slots = candidates();
            if (slots.isEmpty()) {
                return null;
            }
            int slot = slots.get(ThreadLocalRandom.current().nextInt(slots.size()));
            ItemStack stack = player.getInventory().getItem(slot);
            return stack == null ? null : stack.getType();
        }

        private List<Integer> candidates() {
            List<Integer> out = new ArrayList<>();
            PlayerInventory inv = player.getInventory();
            for (int i = 0; i < 9; i++) {
                ItemStack stack = inv.getItem(i);
                if (stack != null && stack.getAmount() > 0 && stack.getType().isBlock()) {
                    out.add(i);
                }
            }
            return out;
        }
    }
}
