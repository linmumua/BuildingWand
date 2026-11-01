#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""校验生成的 CraftEngine 资源包。

资源来自 ConstructionWand 模组本体（标准 Minecraft 资源包格式），所以校验重点不再是
「UV 是否越界」这类自绘贴图才有的问题，而是：

1. 贴图尺寸与模组一致（16x16），避免有人误缩放导致手持时模糊；
2. 每个模型引用的贴图键都在 textures 里定义、且对应文件真实存在——
   漏一张就表现为「物品是紫黑格」；
3. 命名空间必须是 buildingwand，不允许残留 constructionwand；
4. 物品定义引用的模型文件存在；分类只由 categories.yml 的 list 决定，
   物品本身不写 category（两边都写会让 CE 把同一物品登记两遍，浏览器里出现重复）；
5. categories.yml 里列出的每个物品 id 都有对应的物品定义；
6. 没有多余的死资源（*_wand_core.json 与 overlay_core.png 是模组「安装核心后换外观」
   的变体，我们的物品定义指向 item/<tier>_wand，这些文件不该出现在包里）；
7. 预览方块只注册方块本体，不带 items 段、不进分类，避免物品浏览器出现重复。

用法：python tools/verify_ce_pack.py
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
PACK = ROOT / "src" / "main" / "resources" / "craftengine" / "buildingwand"
NS = "buildingwand"
MODELS = PACK / "resourcepack" / "assets" / NS / "models" / "item"
TEXTURES = PACK / "resourcepack" / "assets" / NS / "textures" / "item"
BLOCK_MODELS = PACK / "resourcepack" / "assets" / NS / "models" / "block"
BLOCK_TEXTURES = PACK / "resourcepack" / "assets" / NS / "textures" / "block"
CONF = PACK / "configuration"

# 预览方块：64x64、四条边各 1px 纯白、中间全透明
PREVIEW_BLOCK = "preview"
PREVIEW_SIZE = 64

TIERS = ("stone", "iron", "diamond")

errors: list[str] = []
checks = 0


def check(cond: bool, msg: str) -> None:
    global checks
    checks += 1
    if not cond:
        errors.append(msg)


def resolve_texture(ref: str) -> Path | None:
    """把 buildingwand:item/xxx 解析成贴图文件路径。"""
    if ":" not in ref:
        return None
    namespace, path = ref.split(":", 1)
    if namespace != NS:
        return None
    return TEXTURES / (path.split("/")[-1] + ".png")


# ---------------------------------------------------------------- 贴图
for tier in TIERS:
    tex = TEXTURES / f"{tier}_wand.png"
    check(tex.is_file(), f"缺少贴图 {tex.name}")
    if tex.is_file():
        with Image.open(tex) as im:
            check(im.size == (16, 16), f"{tex.name} 尺寸应为 16x16（模组原始尺寸），实际 {im.size}")

# 贴图清单必须恰好是三档之杖：既抓漏产出，也抓 overlay_core.png
# 这类已废弃的死资源又被塞回来。
expected_textures = {f"{t}_wand.png" for t in TIERS}
actual_textures = {p.name for p in TEXTURES.iterdir() if p.is_file()}
check(actual_textures == expected_textures,
      f"贴图清单不符：多出 {sorted(actual_textures - expected_textures)}，"
      f"缺少 {sorted(expected_textures - actual_textures)}")

# ---------------------------------------------------------------- 模型
# 同理：模型只应有 item/<tier>_wand，模组的 *_wand_core.json
# （安装核心后换外观）没有任何物品引用，属于死文件。
model_files = sorted(MODELS.glob("*.json"))
expected_models = {f"{t}_wand.json" for t in TIERS}
actual_models = {p.name for p in model_files}
check(actual_models == expected_models,
      f"模型清单不符：多出 {sorted(actual_models - expected_models)}，"
      f"缺少 {sorted(expected_models - actual_models)}")

for model_path in model_files:
    text = model_path.read_text(encoding="utf-8")
    check("constructionwand" not in text,
          f"{model_path.name} 残留 constructionwand 命名空间")

    model = json.loads(text)
    parent = model.get("parent")
    check(parent is not None, f"{model_path.name} 缺少 parent")
    textures = model.get("textures", {})
    check(bool(textures), f"{model_path.name} 没有定义贴图")

    for key, ref in textures.items():
        if not isinstance(ref, str):
            continue
        target = resolve_texture(ref)
        check(target is not None,
              f"{model_path.name} 的 {key} 指向了非本命名空间的贴图: {ref}")
        if target is not None:
            check(target.is_file(),
                  f"{model_path.name} 的 {key} 指向不存在的贴图文件: {target.name}")

# ---------------------------------------------------------------- 物品定义
wands_yml = (CONF / "items" / "wands.yml").read_text(encoding="utf-8")

for tier in TIERS:
    item_id = f"{NS}:{tier}_wand"
    check(f"{item_id}:" in wands_yml, f"wands.yml 缺少 {item_id}")
    check(f"path: {NS}:item/{tier}_wand" in wands_yml,
          f"{item_id} 的模型路径不正确")
    check((MODELS / f"{tier}_wand.json").is_file(),
          f"{item_id} 引用的模型 {tier}_wand.json 不存在")

# ---------------------------------------------------------------- 预览方块
# 只注册方块本体，没有 items 段、也不进分类：写了会让 CE 把同一模型再登记成物品，
# 物品浏览器里出现重复条目。
preview_yml_path = CONF / "blocks" / "preview.yml"
check(preview_yml_path.is_file(), "缺少 blocks/preview.yml（预览方块定义）")
preview_yml = preview_yml_path.read_text(encoding="utf-8") if preview_yml_path.is_file() else ""
preview_id = f"{NS}:{PREVIEW_BLOCK}"
check(f"  {preview_id}:" in preview_yml, f"preview.yml 缺少方块 {preview_id}")
check("\nitems:" not in preview_yml,
      "preview.yml 不应包含 items 段，否则预览方块会变成物品并造成浏览器重复")
check(f"path: {NS}:block/{PREVIEW_BLOCK}" in preview_yml,
      f"{preview_id} 的模型路径不正确")

preview_model = BLOCK_MODELS / f"{PREVIEW_BLOCK}.json"
check(preview_model.is_file(), f"{preview_id} 引用的模型 {PREVIEW_BLOCK}.json 不存在")
if preview_model.is_file():
    model_json = json.loads(preview_model.read_text(encoding="utf-8"))
    tex = model_json.get("textures", {}).get("all")
    check(isinstance(tex, dict) and tex.get("sprite") == f"{NS}:block/{PREVIEW_BLOCK}",
          f"{preview_id} 的模型没有正确引用贴图 {NS}:block/{PREVIEW_BLOCK}")
    check(isinstance(tex, dict) and tex.get("force_translucent") is True,
          f"{preview_id} 的贴图缺少 force_translucent，半透明边框会被当成镂空")

preview_png = BLOCK_TEXTURES / f"{PREVIEW_BLOCK}.png"
check(preview_png.is_file(), f"{preview_id} 引用的贴图 {PREVIEW_BLOCK}.png 不存在")
if preview_png.is_file():
    preview_img = Image.open(preview_png).convert("RGBA")
    width, height = preview_img.size
    check((width, height) == (PREVIEW_SIZE, PREVIEW_SIZE),
          f"预览贴图应为 {PREVIEW_SIZE}x{PREVIEW_SIZE}，实际 {width}x{height}")
    px = preview_img.load()
    edge_bad = 0
    inside_opaque = 0
    for y in range(height):
        for x in range(width):
            r, g, b, a = px[x, y]
            if x in (0, width - 1) or y in (0, height - 1):
                # 边缘必须是不透明的纯白
                if (r, g, b) != (255, 255, 255) or a != 255:
                    edge_bad += 1
            elif a != 0:
                # 内部必须全透明
                inside_opaque += 1
    check(edge_bad == 0, f"预览贴图边缘有 {edge_bad} 个像素不是纯白不透明")
    check(inside_opaque == 0, f"预览贴图内部有 {inside_opaque} 个像素不透明，应为全透明")

# ---------------------------------------------------------------- 分类
categories_yml = (CONF / "categories.yml").read_text(encoding="utf-8")
check("categories:" in categories_yml, "categories.yml 缺少 categories 根键")
check(f"{NS}:buildingwand:" in categories_yml, "categories.yml 缺少建筑之杖分类")

declared = re.findall(rf"- ({NS}:[a-z_]+)", categories_yml)
expected_count = len(TIERS)
check(len(declared) == expected_count,
      f"分类里应列出 {expected_count} 个物品，实际 {len(declared)}")

all_items = wands_yml
check(preview_id not in declared,
      "预览方块不应挂进分类，否则会在物品浏览器里多出一个重复条目")
for item_id in declared:
    check(f"{item_id}:" in all_items,
          f"分类引用了未定义的物品 {item_id}")

print(f"已执行 {checks} 项校验")
if errors:
    print(f"发现 {len(errors)} 个问题：")
    for msg in errors:
        print("  - " + msg)
    sys.exit(1)
print("资源包校验通过。")
