#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""生成 README 用的展示图，输出到 docs/images/。

素材来源：
- 之杖贴图：本仓库内置资源包（src/main/resources/craftengine/...）
- 原版材料贴图（木棍、圆石、铁锭、钻石、工作台）：本机 Minecraft 客户端 jar，
  只在本地读取用于合成展示图，不单独入库

用法：python tools/render_showcase.py [客户端jar路径]
"""
from __future__ import annotations

import io
import os
import sys
import zipfile
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "docs" / "images"
WAND_TEX = ROOT / "src/main/resources/craftengine/buildingwand/resourcepack/assets/buildingwand/textures"
DEFAULT_CLIENT = Path(os.environ.get("APPDATA", "")) / ".minecraft/versions/1.8.9/1.8.9.jar"

FONT = "C:/Windows/Fonts/msyh.ttc"
FONT_BOLD = "C:/Windows/Fonts/msyhbd.ttc"

# 与 lang/zh_CN.yml 保持一致的角色色
BRAND_A = (0x9B, 0xE7, 0xB0)
BRAND_B = (0x4F, 0x9D, 0x69)
ACCENT = (0xF5, 0xC2, 0x6B)
COMMAND = (0x7D, 0xD3, 0xFC)
SUCCESS = (0x86, 0xEF, 0xAC)
WARN = (0xFB, 0xBF, 0x24)
GRAY = (0xAA, 0xAA, 0xAA)
DARK_GRAY = (0x55, 0x55, 0x55)
WHITE = (0xFF, 0xFF, 0xFF)

BG = (0x1E, 0x1F, 0x22)
PANEL = (0xC6, 0xC6, 0xC6)

TIERS = [
    ("stone", "石之杖", 9, "cobblestone"),
    ("iron", "铁之杖", 27, "iron_ingot"),
    ("diamond", "钻石之杖", 128, "diamond"),
]
VANILLA = {
    "stick": "assets/minecraft/textures/items/stick.png",
    "cobblestone": "assets/minecraft/textures/blocks/cobblestone.png",
    "iron_ingot": "assets/minecraft/textures/items/iron_ingot.png",
    "diamond": "assets/minecraft/textures/items/diamond.png",
    "crafting_table_top": "assets/minecraft/textures/blocks/crafting_table_top.png",
}


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(FONT_BOLD if bold else FONT, size)


def pixel(img: Image.Image, size: int) -> Image.Image:
    """像素贴图放大，保持锐利边缘。"""
    return img.convert("RGBA").resize((size, size), Image.NEAREST)


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def draw_gradient_text(draw, xy, text, fnt, c1, c2):
    """逐字插值的渐变文字，对应 MiniMessage 的 <gradient>。"""
    x, y = xy
    n = max(1, len(text) - 1)
    for i, ch in enumerate(text):
        draw.text((x, y), ch, font=fnt, fill=lerp(c1, c2, i / n))
        x += draw.textlength(ch, font=fnt)
    return x


def draw_segments(draw, xy, segments, fnt):
    x, y = xy
    for text, color in segments:
        draw.text((x, y), text, font=fnt, fill=color)
        x += draw.textlength(text, font=fnt)
    return x


def slot(draw, x, y, s):
    """原版物品栏槽位：深色左上描边 + 浅色右下描边。"""
    draw.rectangle([x, y, x + s - 1, y + s - 1], fill=(0x8B, 0x8B, 0x8B))
    draw.line([(x, y), (x + s - 1, y)], fill=(0x37, 0x37, 0x37), width=2)
    draw.line([(x, y), (x, y + s - 1)], fill=(0x37, 0x37, 0x37), width=2)
    draw.line([(x, y + s - 1), (x + s - 1, y + s - 1)], fill=WHITE, width=2)
    draw.line([(x + s - 1, y), (x + s - 1, y + s - 1)], fill=WHITE, width=2)


def load_textures(client_jar: Path) -> dict[str, Image.Image]:
    tex = {}
    with zipfile.ZipFile(client_jar) as zf:
        for key, path in VANILLA.items():
            tex[key] = Image.open(io.BytesIO(zf.read(path))).convert("RGBA")
    for tier, *_ in TIERS:
        tex[f"{tier}_wand"] = Image.open(WAND_TEX / "item" / f"{tier}_wand.png").convert("RGBA")
    tex["preview"] = Image.open(WAND_TEX / "block" / "preview.png").convert("RGBA")
    return tex


# ---------------------------------------------------------------- 横幅

def render_banner(tex):
    w, h = 1280, 320
    img = Image.new("RGB", (w, h), BG)
    d = ImageDraw.Draw(img)
    # 底部一条品牌绿渐变线
    for x in range(w):
        d.line([(x, h - 6), (x, h - 1)], fill=lerp(BRAND_A, BRAND_B, x / w))

    icon = 128
    for i, (tier, *_) in enumerate(TIERS):
        img.paste(pixel(tex[f"{tier}_wand"], icon), (80 + i * 110, 96), pixel(tex[f"{tier}_wand"], icon))

    draw_gradient_text(d, (480, 78), "建筑之杖", font(84, True), BRAND_A, BRAND_B)
    d.text((484, 186), "BuildingWand", font=font(34, True), fill=WHITE)
    d.text((484, 236), "ConstructionWand 的 Paper / Folia 服务端移植", font=font(26), fill=GRAY)
    img.save(OUT / "banner.png")


# ---------------------------------------------------------------- 三档之杖

def render_wands(tex):
    card_w, card_h, gap = 360, 300, 30
    w = card_w * 3 + gap * 4
    h = card_h + gap * 2
    img = Image.new("RGB", (w, h), BG)
    d = ImageDraw.Draw(img)
    for i, (tier, name, limit, _) in enumerate(TIERS):
        x = gap + i * (card_w + gap)
        y = gap
        d.rounded_rectangle([x, y, x + card_w, y + card_h], radius=18, fill=(0x2A, 0x2C, 0x30))
        icon = pixel(tex[f"{tier}_wand"], 128)
        img.paste(icon, (x + (card_w - 128) // 2, y + 34), icon)
        f_name = font(36, True)
        tw = d.textlength(name, font=f_name)
        d.text((x + (card_w - tw) / 2, y + 184), name, font=f_name, fill=ACCENT)
        segs = [("一次最多铺 ", GRAY), (str(limit), WHITE), (" 格", GRAY)]
        f_body = font(26)
        tw = sum(d.textlength(t, font=f_body) for t, _ in segs)
        draw_segments(d, (x + (card_w - tw) / 2, y + 240), segs, f_body)
    img.save(OUT / "wands.png")


# ---------------------------------------------------------------- 合成配方

def render_recipes(tex):
    s = 72                      # 槽位边长
    grid = s * 3
    block_w = grid + 70 + s + 60
    block_h = grid + 90
    # 最后一块面板右缘在 3*block_w-4，右侧留白与左侧对齐
    w = block_w * 3 + 20
    h = block_h + 60
    img = Image.new("RGB", (w, h), BG)
    d = ImageDraw.Draw(img)
    shape = [" AA", " PA", "P  "]
    for i, (tier, name, _, material) in enumerate(TIERS):
        ox = 40 + i * block_w
        oy = 40
        d.rounded_rectangle([ox - 16, oy - 16, ox + block_w - 44, oy + block_h - 24],
                            radius=14, fill=PANEL)
        for r, row in enumerate(shape):
            for c, ch in enumerate(row):
                x, y = ox + c * s, oy + r * s
                slot(d, x, y, s)
                key = {"A": material, "P": "stick"}.get(ch)
                if key:
                    icon = pixel(tex[key], s - 16)
                    img.paste(icon, (x + 8, y + 8), icon)
        # 箭头
        ax = ox + grid + 14
        ay = oy + grid // 2
        d.polygon([(ax, ay - 10), (ax + 30, ay - 10), (ax + 30, ay - 22),
                   (ax + 52, ay), (ax + 30, ay + 22), (ax + 30, ay + 10), (ax, ay + 10)],
                  fill=(0x8B, 0x8B, 0x8B))
        # 产物
        rx = ox + grid + 70
        ry = oy + grid // 2 - s // 2
        slot(d, rx, ry, s)
        icon = pixel(tex[f"{tier}_wand"], s - 16)
        img.paste(icon, (rx + 8, ry + 8), icon)
        f = font(28, True)
        tw = d.textlength(name, font=f)
        d.text((ox + (grid + 70 + s - tw) / 2, oy + grid + 22), name, font=f, fill=(0x3F, 0x3F, 0x3F))
    img.save(OUT / "recipes.png")


# ---------------------------------------------------------------- 聊天提示

def render_messages():
    lines = [
        [("prefix",), ("加载成功", SUCCESS), (" · ", DARK_GRAY), ("感谢您使用我的插件 ", GRAY), ("OvO", ACCENT)],
        [("prefix",), ("收到之杖", SUCCESS), (" · ", DARK_GRAY), ("钻石之杖", ACCENT), (" 已放入背包", GRAY)],
        [("prefix",), ("放置限制", GRAY), (" » ", DARK_GRAY), ("水平", ACCENT)],
        [("prefix",), ("方块不足", WARN), (" · ", DARK_GRAY), ("本次只铺了 ", GRAY), ("9", ACCENT), (" 格", GRAY)],
        [("prefix",), ("已撤销", SUCCESS), (" · ", DARK_GRAY), ("还原了 ", GRAY), ("27", ACCENT), (" 格方块", GRAY)],
        [("header",)],
        [("/bw give [玩家] <等级>", COMMAND), (" » ", DARK_GRAY), ("发放之杖", GRAY)],
        [("/bw undo", COMMAND), (" » ", DARK_GRAY), ("撤销上一次操作", GRAY)],
        [("/bw reload", COMMAND), (" » ", DARK_GRAY), ("重载配置、语言与资源包", GRAY)],
    ]
    fsize, lh, pad = 28, 46, 28
    w = 1060
    h = pad * 2 + lh * len(lines)
    img = Image.new("RGB", (w, h), (0x14, 0x15, 0x17))
    d = ImageDraw.Draw(img)
    f = font(fsize)
    fb = font(fsize, True)
    for i, line in enumerate(lines):
        x, y = pad, pad + i * lh
        for seg in line:
            if seg == ("prefix",):
                x = draw_gradient_text(d, (x, y), "建筑之杖", fb, BRAND_A, BRAND_B)
                x = draw_segments(d, (x, y), [(" » ", DARK_GRAY)], f)
            elif seg == ("header",):
                x = draw_gradient_text(d, (x, y), "建筑之杖", fb, BRAND_A, BRAND_B)
                x = draw_segments(d, (x, y), [(" · 指令", DARK_GRAY)], f)
            else:
                x = draw_segments(d, (x, y), [seg], f)
    img.save(OUT / "messages.png")


# ---------------------------------------------------------------- 预览示意

def render_preview(tex):
    """示意图：左边站立时的铺面预览，右边潜行时的撤销预览。"""
    s = 64
    cols, rows = 6, 4
    panel_w = cols * s + 80
    w = panel_w * 2 + 60
    h = rows * s + 170
    img = Image.new("RGB", (w, h), BG)
    d = ImageDraw.Draw(img)
    stone = pixel(tex["cobblestone"], s)
    ghost = pixel(tex["preview"], s)

    def wall(ox, oy, top_rows):
        for r in range(rows):
            for c in range(cols):
                if r >= rows - top_rows:
                    img.paste(stone, (ox + c * s, oy + r * s))

    # 左：已有两层墙，上方一层是白边预览
    ox, oy = 40, 100
    wall(ox, oy, 2)
    for c in range(cols):
        img.paste(ghost, (ox + c * s, oy + 1 * s), ghost)
    d.text((ox, 30), "站立 · 铺面预览", font=font(30, True), fill=WHITE)
    d.text((ox, 66), "白边方块标出即将铺开的位置", font=font(22), fill=GRAY)

    # 右：刚铺的那一层被红色描边标出
    ox2 = ox + panel_w + 20
    wall(ox2, oy, 3)
    red = (0xFF, 0x40, 0x40)
    for c in range(cols):
        x, y = ox2 + c * s, oy + 1 * s
        d.rectangle([x + 1, y + 1, x + s - 2, y + s - 2], outline=red, width=4)
    d.text((ox2, 30), "潜行 · 撤销预览", font=font(30, True), fill=WHITE)
    d.text((ox2, 66), "红色发光标出撤销会回滚的方块", font=font(22), fill=GRAY)

    d.text((40, h - 46), "示意图，实际效果以游戏内为准", font=font(20), fill=DARK_GRAY)
    img.save(OUT / "preview.png")


def main():
    client = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_CLIENT
    if not client.is_file():
        raise SystemExit(f"找不到 Minecraft 客户端 jar：{client}")
    OUT.mkdir(parents=True, exist_ok=True)
    tex = load_textures(client)
    render_banner(tex)
    render_wands(tex)
    render_recipes(tex)
    render_messages()
    render_preview(tex)
    for p in sorted(OUT.glob("*.png")):
        print(p.relative_to(ROOT), Image.open(p).size)


if __name__ == "__main__":
    main()
