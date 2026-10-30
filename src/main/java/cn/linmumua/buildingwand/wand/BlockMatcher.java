package cn.linmumua.buildingwand.wand;

/**
 * 方块匹配判定。由配置驱动（EXACT / SIMILAR / ANY），算法层只认这个接口。
 */
@FunctionalInterface
public interface BlockMatcher {

    boolean matches(String target, String candidate);
}
