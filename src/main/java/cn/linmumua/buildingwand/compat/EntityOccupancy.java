package cn.linmumua.buildingwand.compat;

import cn.linmumua.buildingwand.wand.Pos;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.BoundingBox;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 「这一格被实体占着吗」的共享判据，预览与真正落块都调这里。
 *
 * <p><b>为什么必须共享</b>：CraftEngine 的家具/方块用隐形实体充当碰撞箱
 * （资源包里 {@code hitboxes: - type: shulker} 配 {@code blocks_building: true}），
 * 而 shulker 是 {@link LivingEntity}。方块塞进它身体里会被挤掉/弹飞，
 * 所以落块侧一直拒绝这种位置。预览如果不知道这回事，就会把这些位置照常画出来——
 * 玩家看到「能放」、右键却一格都不落，这是最难解释的一类不一致。
 * 与其在两处各写一份判据（迟早会走偏），不如两边调同一个函数。</p>
 *
 * <p>本类刻意同时容纳纯数学部分（{@link #coveredBlocks}）与 Bukkit 部分
 * （{@link #blockedPositions}）。纯数学部分只吃 {@code double}、只返回 JDK 集合，
 * 可以在没有服务端依赖的单元测试里直接验证边界规则——半开区间、负数、退化体积
 * 这几条一旦写错，症状是「离墙一格莫名能放 / 贴着地板放不了」，
 * 进游戏靠肉眼几乎不可能定位。</p>
 */
public final class EntityOccupancy {

    private EntityOccupancy() {
    }

    /**
     * 一个世界坐标 AABB 覆盖到的方块坐标集合。
     *
     * <p>方块 {@code (x,y,z)} 的占用范围是 {@code [x, x+1) × [y, y+1) × [z, z+1)}，
     * 因此每个轴取 {@code floor(min)} 到 {@code ceil(max) - 1}：</p>
     * <ul>
     *   <li>max 正好落在整数 {@code k} 上时，方块 {@code k} 只是被「碰到边界」而不算覆盖
     *       （{@code ceil(k) - 1 = k - 1}），这样才对得上半开区间语义；</li>
     *   <li>负数由 floor/ceil 自然落到正确格子，例如 min = -0.3 属于方块 -1；</li>
     *   <li>任一面退化（min == max）表示体积为零，一个方块也覆盖不到，直接返回空集。
     *       这种情况不能靠公式兜底：min = max = 0.5 时公式会算出方块 0。</li>
     * </ul>
     *
     * <p><b>不做浮点误差吸附</b>：实体包围盒由 float 尺寸算出，越过整数边界一丁点
     * （如 1.0000001）在放置侧会被 Bukkit 的严格 AABB 重叠判定算作「压到相邻格」，
     * 预览必须跟着这么算，否则两边又不一致了。</p>
     *
     * @return 覆盖到的方块坐标；零体积或非法输入（含 NaN）返回空集
     */
    public static Set<Pos> coveredBlocks(double minX, double minY, double minZ,
                                         double maxX, double maxY, double maxZ) {
        // 用「取反的 >」而不是「<=」：NaN 参与比较恒为 false，
        // 于是脏数据也会走空集分支，不必额外判 isFinite
        if (!(maxX > minX && maxY > minY && maxZ > minZ)) {
            return Set.of();
        }
        int fromX = (int) Math.floor(minX);
        int fromY = (int) Math.floor(minY);
        int fromZ = (int) Math.floor(minZ);
        int toX = (int) Math.ceil(maxX) - 1;
        int toY = (int) Math.ceil(maxY) - 1;
        int toZ = (int) Math.ceil(maxZ) - 1;

        Set<Pos> covered = new HashSet<>();
        for (int x = fromX; x <= toX; x++) {
            for (int y = fromY; y <= toY; y++) {
                for (int z = fromZ; z <= toZ; z++) {
                    covered.add(new Pos(x, y, z));
                }
            }
        }
        return covered;
    }

    /**
     * 候选位置中，被活着的实体占据的那些。
     *
     * <p><b>为什么只查一次</b>：一次预览最多可能是几百格，逐格
     * {@code world.getNearbyEntities} 等于把同一片区块的实体表反复扫几百遍。
     * 这里先算候选位置的整体包络盒，查一次拿回附近所有实体，再用纯数学把每个实体的
     * 包围盒摊成方块、与候选集合求交。成本只跟「附近有几个实体」有关，与候选格数无关。</p>
     *
     * @param world            候选位置所在世界
     * @param candidates       待判定的位置（预览计划 / 单格落块）
     * @param ignoredEntityIds 不参与判据的实体 UUID，传本插件自己 spawn 的预览实体
     * @return 被实体占据的位置（{@code candidates} 的子集）；一个都没有时返回空集
     */
    public static Set<Pos> blockedPositions(World world, List<Pos> candidates,
                                            Collection<UUID> ignoredEntityIds) {
        if (world == null || candidates == null || candidates.isEmpty()) {
            return Set.of();
        }
        Set<Pos> targets = new HashSet<>(candidates);

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (Pos pos : candidates) {
            minX = Math.min(minX, pos.x());
            minY = Math.min(minY, pos.y());
            minZ = Math.min(minZ, pos.z());
            maxX = Math.max(maxX, pos.x());
            maxY = Math.max(maxY, pos.y());
            maxZ = Math.max(maxZ, pos.z());
        }
        // 方块索引 → 世界坐标：整格占 [x, x+1)
        BoundingBox envelope = new BoundingBox(minX, minY, minZ,
                maxX + 1.0, maxY + 1.0, maxZ + 1.0);

        Collection<Entity> nearby = world.getNearbyEntities(envelope);
        if (nearby.isEmpty()) {
            return Set.of();
        }

        Set<Pos> blocked = new HashSet<>();
        for (Entity entity : nearby) {
            // 只认活着的 LivingEntity：盔甲架、掉落物、箭这些不影响落块，
            // 而 CraftEngine 家具的 shulker 碰撞箱正好是 LivingEntity
            if (!(entity instanceof LivingEntity living) || living.isDead()) {
                continue;
            }
            if (ignoredEntityIds != null && ignoredEntityIds.contains(living.getUniqueId())) {
                continue;
            }
            BoundingBox box = living.getBoundingBox();
            for (Pos pos : coveredBlocks(box.getMinX(), box.getMinY(), box.getMinZ(),
                    box.getMaxX(), box.getMaxY(), box.getMaxZ())) {
                if (targets.contains(pos)) {
                    blocked.add(pos);
                }
            }
        }
        return blocked;
    }
}
