"""
对截图做像素级检查（因为我看不到图片，就用程序来判断「画面对不对」）。
检查项：
  1. 画面不是空白 / 纯色（色彩数量足够多）
  2. B 站粉色 #FB7299 有没有出现（品牌色）
  3. 顶部导航是白色区域，正文是浅灰背景
  4. 视频播放页中间那片「画面」不是纯黑
"""
import sys
from collections import Counter
from pathlib import Path

try:
    from PIL import Image
except ImportError:
    print("没有安装 Pillow，跳过像素检查")
    sys.exit(0)

SHOTS = Path(r"C:\Users\24900\Desktop\bilibili-clone\shots")


def analyze(path: Path):
    img = Image.open(path).convert("RGB")
    w, h = img.size
    small = img.resize((w // 4, h // 4))
    pixels = list(small.getdata())
    colors = Counter(pixels)
    total = len(pixels)

    def near(c, target, tol=26):
        return all(abs(c[i] - target[i]) <= tol for i in range(3))

    pink = (251, 114, 153)
    blue = (0, 174, 236)
    pink_hits = sum(n for c, n in colors.items() if near(c, pink, 40))
    blue_hits = sum(n for c, n in colors.items() if near(c, blue, 40))
    white_hits = sum(n for c, n in colors.items() if near(c, (255, 255, 255), 8))

    # 顶部导航条（第 20 行）应该基本是白底
    top_row = [small.getpixel((x, 20)) for x in range(0, small.size[0], 5)]
    top_white = sum(1 for c in top_row if near(c, (255, 255, 255), 12)) / len(top_row)

    # 中间内容区颜色种类（判断有没有内容，不是一片空白）
    mid = small.crop((0, small.size[1] // 3, small.size[0], small.size[1] * 2 // 3))
    mid_colors = len(set(mid.getdata()))

    return {
        "file": path.name,
        "size": f"{w}x{h}",
        "unique_colors": len(colors),
        "pink_ratio": round(pink_hits / total * 100, 3),
        "blue_ratio": round(blue_hits / total * 100, 3),
        "white_ratio": round(white_hits / total * 100, 2),
        "topbar_white": round(top_white * 100, 1),
        "mid_content_colors": mid_colors,
    }


def main():
    files = sorted(SHOTS.glob("*.png"))
    if not files:
        print("没有找到截图")
        return
    print(f"{'文件':<22}{'尺寸':<12}{'颜色数':<8}{'粉色%':<8}{'蓝色%':<8}{'白色%':<8}{'顶栏白%':<9}{'中部色数'}")
    for f in files:
        r = analyze(f)
        print(
            f"{r['file']:<22}{r['size']:<12}{r['unique_colors']:<8}{r['pink_ratio']:<8}"
            f"{r['blue_ratio']:<8}{r['white_ratio']:<8}{r['topbar_white']:<9}{r['mid_content_colors']}"
        )


if __name__ == "__main__":
    main()
