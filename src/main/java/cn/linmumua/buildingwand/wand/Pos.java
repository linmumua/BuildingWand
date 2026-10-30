package cn.linmumua.buildingwand.wand;

/**
 * 方块坐标（纯算法层，不依赖 Bukkit）。
 */
public record Pos(int x, int y, int z) {

    public Pos offset(int dx, int dy, int dz) {
        return new Pos(x + dx, y + dy, z + dz);
    }

    public Pos offset(Face face) {
        return offset(face.dx(), face.dy(), face.dz());
    }

    public Pos offset(Face a, Face b) {
        return offset(a.dx() + b.dx(), a.dy() + b.dy(), a.dz() + b.dz());
    }
}
