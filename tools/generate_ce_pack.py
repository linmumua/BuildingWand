#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""从 ConstructionWand 模组 jar 生成 CraftEngine 资源包。

为什么直接从模组 jar 取资源，而不是自己画：
模组本身就是一个标准的 Minecraft 资源包（assets/constructionwand/...），里面有官方的
三档之杖贴图与两种核心贴图、以及对应的 item 模型。手绘版本无论怎么调都
不可能比原版更还原，而且一旦调整就要同步改三份。这里只做两件事：
  1. 把命名空间 constructionwand 改成 buildingwand（避免和别的插件撞车）；
  2. 把贴图与模型搬到 CE 资源包该在的位置，并按 CE 的 items/categories 规范写配置。

用法：python tools/generate_ce_pack.py
依赖：pip install pillow（仅用于尺寸校验）
"""
from __future__ import annotations

import json
import shutil
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MOD_JAR = ROOT / "constructionwand-1.20.2-2.12.jar"
OUT = ROOT / "src" / "main" / "resources" / "craftengine" / "buildingwand"

NAMESPACE = "buildingwand"
SOURCE_NS = "constructionwand"

# 模组里的物品 id -> 本插件显示信息
WANDS = {
    "stone": ("石之杖", ["<gray>一次最多铺 9 格"]),
    "iron": ("铁之杖", ["<gray>一次最多铺 27 格"]),
    "diamond": ("钻石之杖", ["<gray>一次最多铺 128 格"]),
}

CATEGORY_ID = f"{NAMESPACE}:buildingwand"

# 预览方块：64x64 贴图，四条边各 1px 纯白，其余全透明。
PREVIEW_BLOCK = "preview"
PREVIEW_TEXTURE_SIZE = 64


def extract(zf: zipfile.ZipFile, src_path: str, dest: Path) -> None:
    """把模组 jar 里的一个条目解出来，顺手改写命名空间。"""
    data = zf.read(src_path)
    dest.parent.mkdir(parents=True, exist_ok=True)
    if src_path.endswith(".json"):
        text = data.decode("utf-8").replace(f"{SOURCE_NS}:", f"{NAMESPACE}:")
        # 模组用 custom_model_data 谓词切换「安装核心后」的外观，服务端用不到这套
        model = json.loads(text)
        model.pop("overrides", None)
        dest.write_text(json.dumps(model, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    else:
        dest.write_bytes(data)


def write_preview_block(assets: Path, conf_dir: Path) -> None:
    """生成预览方块：64x64 贴图、四条边各 1px 纯白、中间全透明。

    <p>BlockDisplay 没有任何透明度控制，想做出「只描一圈边框、中间能透过去看地形」的效果，
    只能靠方块自身的材质，所以要注册一个专用的预览方块。</p>

    只注册方块本体，【不写 items 段、也不挂进分类】：否则 CE 会把同一个模型再登记成
    一个物品，物品浏览器里就出现重复条目（之前踩过这个坑）。方块没有对应物品时，
    玩家在浏览器里看不到它，也无法放置，正好符合「纯视觉载体」的定位。
    """
    from PIL import Image

    block_models = assets / "models" / "block"
    block_textures = assets / "textures" / "block"
    block_models.mkdir(parents=True, exist_ok=True)
    block_textures.mkdir(parents=True, exist_ok=True)

    size = PREVIEW_TEXTURE_SIZE
    image = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    pixels = image.load()
    for i in range(size):
        pixels[i, 0] = (255, 255, 255, 255)              # 上边
        pixels[i, size - 1] = (255, 255, 255, 255)       # 下边
        pixels[0, i] = (255, 255, 255, 255)              # 左边
        pixels[size - 1, i] = (255, 255, 255, 255)       # 右边
    image.save(block_textures / f"{PREVIEW_BLOCK}.png")

    model = {
        "parent": "minecraft:block/cube_all",
        # force_translucent：边框透明度可配（preview.border-opacity），半透明像素要走
        # 半透明渲染层，否则会被当成镂空处理，只剩「全显示 / 全隐藏」两种结果
        "textures": {"all": {"sprite": f"{NAMESPACE}:block/{PREVIEW_BLOCK}",
                             "force_translucent": True}},
    }
    (block_models / f"{PREVIEW_BLOCK}.json").write_text(
        json.dumps(model, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    lines = [
        "# 建筑之杖 · 预览方块（由 tools/generate_ce_pack.py 生成，请勿手工编辑）",
        "#",
        f"# 贴图 {PREVIEW_TEXTURE_SIZE}x{PREVIEW_TEXTURE_SIZE}，四条边各 1px 纯白，中间全透明。",
        "# BlockDisplay 没有透明度控制，只能靠方块自身材质做「描边 + 中间透过去」。",
        "#",
        "# 【刻意不写 items 段、也不挂分类】：写了会让 CE 把同一模型再登记成物品，",
        "# 物品浏览器里就会出现重复条目。方块没有对应物品，玩家拿不到也放不下。",
        "",
        "blocks:",
        f"  {NAMESPACE}:{PREVIEW_BLOCK}:",
        "    settings:",
        "      template:",
        "        - default:settings/transparent_1x1x1",
        "      merges:",
        "        hardness: 0",
        "        resistance: 0",
        "        is_suffocating: false",
        "        is_view_blocking: false",
        "        is_redstone_conductor: false",
        "        can_occlude: false",
        "        replaceable: true",
        "        block_light: 0",
        "        propagate_skylight: true",
        "    state:",
        "      auto_state: solid",
        "      model:",
        f"        path: {NAMESPACE}:block/{PREVIEW_BLOCK}",
    ]
    blocks_dir = conf_dir / "blocks"
    blocks_dir.mkdir(parents=True, exist_ok=True)
    (blocks_dir / "preview.yml").write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> None:
    if not MOD_JAR.is_file():
        raise SystemExit(f"缺少模组 jar: {MOD_JAR}")

    assets = OUT / "resourcepack" / "assets" / NAMESPACE
    models_dir = assets / "models" / "item"
    textures_dir = assets / "textures" / "item"
    items_dir = OUT / "configuration" / "items"

    for old in (assets, OUT / "configuration"):
        if old.exists():
            shutil.rmtree(old)
    for d in (models_dir, textures_dir, items_dir):
        d.mkdir(parents=True, exist_ok=True)

    with zipfile.ZipFile(MOD_JAR) as zf:
        # ---- 贴图 + 模型：之杖与核心物品
        # 只取 item/<tier>_wand.json。模组里还有 *_wand_core.json（安装核心后换外观的变体）
        # 和 overlay_core.png，它们靠 custom_model_data 谓词 + overrides 触发，
        # 而我们的物品定义指向 item/<tier>_wand，服务端永远用不到，取进来就是死文件。
        for tier in WANDS:
            extract(zf, f"assets/{SOURCE_NS}/models/item/{tier}_wand.json",
                    models_dir / f"{tier}_wand.json")
            extract(zf, f"assets/{SOURCE_NS}/textures/item/{tier}_wand.png",
                    textures_dir / f"{tier}_wand.png")

    # ---- 物品定义
    lines = ["items:"]
    for tier, (name, lore) in WANDS.items():
        lines += [
            f"  {NAMESPACE}:{tier}_wand:",
            "    material: paper",
            "    data:",
            f"      item-name: '<!i>{name}'",
        ]
        lines.append("      lore:")
        lines += [f"        - '{entry}'" for entry in lore]
        lines += [
            "    model:",
            "      type: minecraft:model",
            f"      path: {NAMESPACE}:item/{tier}_wand",
        ]
    (items_dir / "wands.yml").write_text("\n".join(lines) + "\n", encoding="utf-8")

    # ---- 分类（CE 的物品浏览器里要能看到这一栏，否则只能靠指令发放）
    category_lines = [
        "categories:",
        f"  {CATEGORY_ID}:",
        "    priority: 10",
        "    name: '<!i>建筑之杖'",
        "    lore:",
        "      - '<!i><gray>ConstructionWand 服务端移植'",
        f"    icon: {NAMESPACE}:diamond_wand",
        "    list:",
    ]
    for tier in WANDS:
        category_lines.append(f"      - {NAMESPACE}:{tier}_wand")
    (OUT / "configuration" / "categories.yml").write_text(
        "\n".join(category_lines) + "\n", encoding="utf-8")

    # ---- 预览方块：只有方块定义，没有物品、不进分类
    write_preview_block(assets, OUT / "configuration")

    # ---- pack.yml
    (OUT / "pack.yml").write_text("\n".join([
        "author: linmumua",
        "version: 1.0.0",
        'description: "建筑之杖 CraftEngine 资源包 | 作者 linmumua"',
        f"namespace: {NAMESPACE}",
    ]) + "\n", encoding="utf-8")

    # 清单：插件启动时按这份清单把包释放到 CraftEngine 的 resources 目录。
    # 必须显式排除 _index.txt 自身：它是上次运行留下的文件，会被 rglob 扫进来，
    # 一旦列进清单，释放器每次启动都会发现「内容变了」而反复重写整个包。
    produced = sorted(
        p.relative_to(OUT).as_posix()
        for p in OUT.rglob("*")
        if p.is_file() and p.name != "_index.txt"
    )
    (OUT / "_index.txt").write_text("\n".join(produced) + "\n", encoding="utf-8")

    print(f"已生成 {len(produced) + 1} 个文件 -> {OUT}")
    for name in produced + ["_index.txt"]:
        print("  " + name)


if __name__ == "__main__":
    main()
