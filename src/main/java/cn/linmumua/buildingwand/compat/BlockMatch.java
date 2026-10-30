package cn.linmumua.buildingwand.compat;

import cn.linmumua.buildingwand.wand.BlockMatcher;
import cn.linmumua.buildingwand.wand.MatchMode;
import cn.linmumua.buildingwand.wand.WorldView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 把 MatchMode 与配置里的相似组，编译成一个可快速调用的判定函数。
 */
public final class BlockMatch {

    private BlockMatch() {
    }

    public static BlockMatcher of(MatchMode mode, List<List<String>> similarGroups) {
        return switch (mode) {
            case EXACT -> (target, candidate) ->
                    !WorldView.AIR.equals(target) && target.equals(candidate);
            case ANY -> (target, candidate) ->
                    !WorldView.AIR.equals(target) && !WorldView.AIR.equals(candidate);
            case SIMILAR -> {
                Map<String, Integer> groupOf = new HashMap<>();
                for (int i = 0; i < similarGroups.size(); i++) {
                    for (String id : similarGroups.get(i)) {
                        groupOf.putIfAbsent(id, i);
                    }
                }
                yield (target, candidate) -> {
                    if (WorldView.AIR.equals(target) || WorldView.AIR.equals(candidate)) {
                        return false;
                    }
                    if (target.equals(candidate)) {
                        return true;
                    }
                    Integer a = groupOf.get(target);
                    Integer b = groupOf.get(candidate);
                    return a != null && a.equals(b);
                };
            }
        };
    }
}
