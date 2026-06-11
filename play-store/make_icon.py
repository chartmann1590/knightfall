from PIL import Image, ImageDraw

W = H = 512
img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
draw = ImageDraw.Draw(img)

BG = (26, 22, 14, 255)
GOLD = (232, 181, 74, 255)
GOLD_DARK = (184, 134, 46, 255)

draw.rounded_rectangle([0, 0, W-1, H-1], radius=100, fill=BG)

cs = W // 8
for row in range(8):
    for col in range(8):
        if (row + col) % 2 == 1:
            draw.rectangle([col*cs, row*cs, (col+1)*cs-1, (row+1)*cs-1], fill=(35, 28, 16, 255))

mask = Image.new("L", (W, H), 0)
md = ImageDraw.Draw(mask)
md.rounded_rectangle([0, 0, W-1, H-1], radius=100, fill=255)
img.putalpha(mask)

cx, cy = W//2, H//2
r_outer = 210
r_inner = 165
for i in range(r_outer, r_outer-25, -1):
    alpha = int(200 * (r_outer - i) / 25)
    draw.ellipse([cx-i, cy-i, cx+i, cy+i], outline=(*GOLD[:3], alpha), width=1)

draw.ellipse([cx-r_inner, cy-r_inner, cx+r_inner, cy+r_inner], fill=GOLD)

r_inner2 = 140
draw.ellipse([cx-r_inner2, cy-r_inner2, cx+r_inner2, cy+r_inner2], fill=(30, 24, 12, 255))

scale = 3.8
ox = cx - int(24 * scale)
oy = cy - int(31 * scale) - 10

def ts(px, py):
    return (int(ox + px * scale), int(oy + py * scale))

body = [
    (14,58),(42,58),(42,49),(39,46),(36,43),
    (42,38),(45,31),(45,24),(36,11),(24,2),
    (22,0),(21,1.5),(21,5),(17,8),(11,16),
    (3,28),(4.5,33.8),(9.5,33.6),(17,28),
    (19.8,30.4),(13,47),(13,52),(14,58)
]
pts = [ts(x,y) for x,y in body]
draw.polygon(pts, fill=GOLD)

ex, ey = ts(19.2, 11.5)
draw.ellipse([ex-6, ey-6, ex+6, ey+6], fill=(26, 22, 14, 255))

bar_pts = [ts(10,56),ts(46,56),ts(49,59),ts(46,62),ts(10,62),ts(7,59)]
draw.polygon(bar_pts, fill=GOLD_DARK)

img.save(r"h:\knightfall\play-store\graphics\icon_512x512.png")
print("Icon saved")
