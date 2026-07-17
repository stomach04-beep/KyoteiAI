"""
BatteryAlert アイコン自動生成スクリプト
- ダーク紺背景 + バッテリー輪郭（シアン）+ 稲妻ボルト（発光シアン）
- 全 mipmap サイズ（48〜192px）を WEBP で出力
- Adaptive Icon 用 foreground / background XML も更新する
"""

import os
from PIL import Image, ImageDraw, ImageFilter

RES = r"C:\Users\81905\AndroidStudioProjects\BatteryAlert\app\src\main\res"

# ──────────────────────────────────────────────
# アイコン画像生成
# ──────────────────────────────────────────────

def apply_glow(canvas, polygon, color=(0, 210, 255)):
    """ポリゴンにじわじわ広がるグロー（発光）エフェクトを適用する"""
    for blur_r, alpha in [(28, 20), (16, 45), (8, 85), (3, 130)]:
        glow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
        ImageDraw.Draw(glow).polygon(polygon, fill=(*color, alpha))
        glow = glow.filter(ImageFilter.GaussianBlur(blur_r))
        canvas = Image.alpha_composite(canvas, glow)
    return canvas


def create_full_icon(size=1024):
    """
    レガシー mipmap 用：背景あり（角丸正方形）の完成形アイコン
    - ダーク紺グラデーション背景
    - シアン輪郭のバッテリー + 発光稲妻ボルト
    """
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))

    # --- グラデーション背景 ---
    bg = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    bg_draw = ImageDraw.Draw(bg)
    for y in range(size):
        t = y / max(size - 1, 1)
        r = int(13 + 12 * t)   # #0D → #19
        g = int(18 + 14 * t)   # #12 → #20
        b = int(53 + 32 * t)   # #35 → #55
        bg_draw.line([(0, y), (size - 1, y)], fill=(r, g, b, 255))

    # 角丸マスクを適用
    corner_r = int(size * 0.22)
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, size - 1, size - 1], radius=corner_r, fill=255)
    bg.putalpha(mask)
    canvas.paste(bg, mask=bg)

    # --- バッテリー本体 ---
    # 全体（本体+端子）を横幅の 50% で中央に配置
    bw   = size * 0.48   # バッテリー本体の幅
    bh   = size * 0.28   # バッテリー本体の高さ
    nt_w = size * 0.055  # 端子の幅
    total_w = bw + nt_w

    cx   = size / 2
    cy   = size / 2
    bx   = cx - total_w / 2         # 本体左端
    by   = cy - bh / 2              # 本体上端
    b_r  = size * 0.04              # 角丸半径
    lw   = max(3, int(size * 0.022))  # 輪郭線幅

    draw = ImageDraw.Draw(canvas)

    # 本体：暗い塗りつぶし
    draw.rounded_rectangle(
        [bx, by, bx + bw, by + bh],
        radius=int(b_r),
        fill=(18, 28, 80, 220)
    )
    # 本体：シアン輪郭
    draw.rounded_rectangle(
        [bx, by, bx + bw, by + bh],
        radius=int(b_r),
        outline=(0, 212, 255, 230),
        width=lw
    )

    # 端子（右側の突起）
    nt_h  = bh * 0.38
    nt_x  = bx + bw
    nt_y  = cy - nt_h / 2
    nt_r  = size * 0.018
    draw.rounded_rectangle(
        [nt_x, nt_y, nt_x + nt_w, nt_y + nt_h],
        radius=int(nt_r),
        fill=(0, 212, 255, 210)
    )

    # --- 稲妻ボルト ---
    # バッテリー本体の中央に配置
    bcx = bx + bw * 0.50
    bcy = cy
    s   = bh * 0.42   # ボルトのスケール

    # 6頂点の稲妻型ポリゴン（時計回り）
    bolt = [
        (bcx + s * 0.35,  bcy - s * 1.0),   # 上
        (bcx - s * 0.25,  bcy + s * 0.06),  # 中央左（上）
        (bcx + s * 0.20,  bcy + s * 0.06),  # 中央右（上）
        (bcx - s * 0.35,  bcy + s * 1.0),   # 下
        (bcx + s * 0.25,  bcy - s * 0.06),  # 中央右（下）
        (bcx - s * 0.20,  bcy - s * 0.06),  # 中央左（下）
    ]

    # グロー（外側から内側へ）
    canvas = apply_glow(canvas, bolt)

    # 本体（明るいシアン）
    ImageDraw.Draw(canvas).polygon(bolt, fill=(150, 238, 255, 255))

    # ハイライト（白に近い中心）：ボルトを 55% に縮小して重ねる
    highlight = [(bcx + (x - bcx) * 0.55, bcy + (y - bcy) * 0.55) for x, y in bolt]
    ImageDraw.Draw(canvas).polygon(highlight, fill=(215, 250, 255, 255))

    return canvas


# ──────────────────────────────────────────────
# 全サイズ保存
# ──────────────────────────────────────────────

MIPMAP_SIZES = {
    "mipmap-mdpi":    48,
    "mipmap-hdpi":    72,
    "mipmap-xhdpi":   96,
    "mipmap-xxhdpi":  144,
    "mipmap-xxxhdpi": 192,
}

def save_bitmaps(icon_1024):
    for folder, px in MIPMAP_SIZES.items():
        out_dir = os.path.join(RES, folder)
        os.makedirs(out_dir, exist_ok=True)
        resized = icon_1024.resize((px, px), Image.LANCZOS)
        resized.save(os.path.join(out_dir, "ic_launcher.webp"),       "WEBP", quality=95)
        resized.save(os.path.join(out_dir, "ic_launcher_round.webp"), "WEBP", quality=95)
        print(f"  {px}×{px}  →  {folder}")


# ──────────────────────────────────────────────
# Adaptive Icon 用 XML を更新
# ──────────────────────────────────────────────

BACKGROUND_XML = """\
<?xml version="1.0" encoding="utf-8"?>
<!-- Adaptive Icon 背景：ダーク紺の単色塗りつぶし -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#0D1235"
        android:pathData="M0,0h108v108h-108z" />
</vector>
"""

FOREGROUND_XML = """\
<?xml version="1.0" encoding="utf-8"?>
<!--
  Adaptive Icon フォアグラウンド：バッテリー輪郭 + 稲妻ボルト
  ビューポート 108×108、セーフゾーン中央 66×66dp を想定して設計
  バッテリー本体：x=29〜75, y=43〜65（46×22dp）
  端子：x=75〜80, y=50〜58（5×8dp）
  稲妻ボルト：バッテリー中心 x≒52, y=54 に配置
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <!-- バッテリー本体：暗い塗りつぶし -->
    <path
        android:fillColor="#121C50"
        android:pathData="M33,43 L72,43 Q76,43 76,47 L76,61 Q76,65 72,65 L33,65 Q29,65 29,61 L29,47 Q29,43 33,43 Z" />

    <!-- バッテリー本体：シアン輪郭 -->
    <path
        android:fillColor="#00000000"
        android:strokeColor="#00D4FF"
        android:strokeWidth="2.4"
        android:pathData="M33,43 L72,43 Q76,43 76,47 L76,61 Q76,65 72,65 L33,65 Q29,65 29,61 L29,47 Q29,43 33,43 Z" />

    <!-- バッテリー端子（右の突起）：シアン塗りつぶし -->
    <path
        android:fillColor="#00D4FF"
        android:pathData="M76,50 L80,50 Q81,50 81,51 L81,57 Q81,58 80,58 L76,58 Z" />

    <!-- 稲妻ボルト：シアン白でグローを模擬 -->
    <path
        android:fillColor="#96EEFF"
        android:fillAlpha="0.5"
        android:pathData="M55,46 L48,52.5 L53,52.5 L49,62 L56,55.5 L51,55.5 Z" />

    <!-- 稲妻ボルト：明るいシアン本体 -->
    <path
        android:fillColor="#C8F5FF"
        android:pathData="M55,46 L48,52.5 L53,52.5 L49,62 L56,55.5 L51,55.5 Z" />

</vector>
"""

def update_adaptive_xmls():
    bg_path = os.path.join(RES, "drawable", "ic_launcher_background.xml")
    fg_path = os.path.join(RES, "drawable", "ic_launcher_foreground.xml")
    with open(bg_path, "w", encoding="utf-8") as f:
        f.write(BACKGROUND_XML)
    print(f"  更新: {bg_path}")
    with open(fg_path, "w", encoding="utf-8") as f:
        f.write(FOREGROUND_XML)
    print(f"  更新: {fg_path}")


# ──────────────────────────────────────────────
# メイン
# ──────────────────────────────────────────────

if __name__ == "__main__":
    print("=== BatteryAlert アイコン生成 ===")

    print("\n[1] 1024px マスター画像を生成中...")
    icon = create_full_icon(1024)

    # プレビュー保存
    preview_path = os.path.join(RES, "..", "icon_preview.png")
    icon.save(preview_path, "PNG")
    print(f"  プレビュー保存: {os.path.abspath(preview_path)}")

    print("\n[2] 各 mipmap サイズに保存中...")
    save_bitmaps(icon)

    print("\n[3] Adaptive Icon XML を更新中...")
    update_adaptive_xmls()

    print("\n完了！")
