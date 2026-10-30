package cn.linmumua.buildingwand.wand.action;

import cn.linmumua.buildingwand.wand.BlockMatcher;
import cn.linmumua.buildingwand.wand.Face;
import cn.linmumua.buildingwand.wand.LockMode;
import cn.linmumua.buildingwand.wand.Pos;
import cn.linmumua.buildingwand.wand.WorldView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 铺面动作：在视线命中面的外侧做洪泛，沿着这个面铺开。
 * 算法逐条对齐原模组 ActionConstruction：
 * 方向锁决定哪些邻居入队，双锁同时开启时额外补充对角。
 */
public final class ConstructionAction {

    public List<Pos> compute(WorldView world, Pos hit, Face face, LockMode lock,
                             BlockMatcher matcher, int limit) {
        List<Pos> result = new ArrayList<>();
        ArrayDeque<Pos> candidates = new ArrayDeque<>();
        Set<Pos> seen = new HashSet<>();

        String target = world.blockAt(hit);
        Pos start = hit.offset(face);

        // 方向锁门控：只有被允许的轴向才把起点入队
        if (face.vertical()) {
            if (lock.allows(LockMode.NORTHSOUTH) || lock.allows(LockMode.EASTWEST)) {
                candidates.add(start);
            }
        } else if (lock.allows(LockMode.HORIZONTAL) || lock.allows(LockMode.VERTICAL)) {
            candidates.add(start);
        }

        while (!candidates.isEmpty() && result.size() < limit) {
            Pos cur = candidates.pollFirst();

            // 候选格必须由「同类方块」作为支撑，才算这个面的一部分
            Pos support = cur.offset(face.opposite());
            if (!matcher.matches(target, world.blockAt(support)) || !seen.add(cur)) {
                continue;
            }
            if (!world.isPlaceable(cur)) {
                continue;
            }
            result.add(cur);
            enqueueNeighbours(candidates, cur, face, lock);
        }
        return result;
    }

    /**
     * 把一个已确认的方块的邻居入队。规则与原模组一致，不能随意简化，
     * 否则铺出来的形状（尤其是双锁下的矩形面）会和原版不一致。
     */
    static void enqueueNeighbours(ArrayDeque<Pos> candidates, Pos cur, Face face, LockMode lock) {
        boolean ns = lock.allows(LockMode.NORTHSOUTH);
        boolean ew = lock.allows(LockMode.EASTWEST);
        boolean h = lock.allows(LockMode.HORIZONTAL);
        boolean v = lock.allows(LockMode.VERTICAL);

        switch (face) {
            case DOWN, UP -> {
                if (ns) {
                    candidates.add(cur.offset(Face.NORTH));
                    candidates.add(cur.offset(Face.SOUTH));
                }
                if (ew) {
                    candidates.add(cur.offset(Face.EAST));
                    candidates.add(cur.offset(Face.WEST));
                }
                if (ns && ew) {
                    candidates.add(cur.offset(Face.NORTH, Face.EAST));
                    candidates.add(cur.offset(Face.NORTH, Face.WEST));
                    candidates.add(cur.offset(Face.SOUTH, Face.EAST));
                    candidates.add(cur.offset(Face.SOUTH, Face.WEST));
                }
            }
            case NORTH, SOUTH -> {
                if (h) {
                    candidates.add(cur.offset(Face.EAST));
                    candidates.add(cur.offset(Face.WEST));
                }
                if (v) {
                    candidates.add(cur.offset(Face.UP));
                    candidates.add(cur.offset(Face.DOWN));
                }
                if (h && v) {
                    candidates.add(cur.offset(Face.UP, Face.EAST));
                    candidates.add(cur.offset(Face.UP, Face.WEST));
                    candidates.add(cur.offset(Face.DOWN, Face.EAST));
                    candidates.add(cur.offset(Face.DOWN, Face.WEST));
                }
            }
            case EAST, WEST -> {
                if (h) {
                    candidates.add(cur.offset(Face.NORTH));
                    candidates.add(cur.offset(Face.SOUTH));
                }
                if (v) {
                    candidates.add(cur.offset(Face.UP));
                    candidates.add(cur.offset(Face.DOWN));
                }
                if (h && v) {
                    candidates.add(cur.offset(Face.UP, Face.NORTH));
                    candidates.add(cur.offset(Face.UP, Face.SOUTH));
                    candidates.add(cur.offset(Face.DOWN, Face.NORTH));
                    candidates.add(cur.offset(Face.DOWN, Face.SOUTH));
                }
            }
        }
    }
}
