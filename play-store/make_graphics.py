"""Generate Play Store graphics: feature image (1024x500), icon (512x512), and tablet screenshots."""
from PIL import Image, ImageDraw, ImageFont
import os, shutil, math

PY = r"C:\Users\Charles\AppData\Local\Programs\Python\Python310"
STORE = r"h:\knightfall\play-store"
PHONE = os.path.join(STORE, "graphics", "phone")

# ── Colors (from app theme) ─────────────────────────────────────────────────
BG        = (26, 22, 14)       # dark brown-black
GOLD      = (198, 149, 60)     # primary gold
LIGHT_SQ  = (234, 202, 160)    # light board square
DARK_SQ   = (139, 100, 60)     # dark board square
WHITE_PC  = (255, 255, 255)
BLACK_PC  = (40, 32, 20)
TEXT_WH   = (230, 220, 200)

def font(size, bold=False):
    for path in [
        r"C:\Windows\Fonts\Arial.ttf",
        r"C:\Windows\Fonts\arialbd.ttf" if bold else None,
        r"C:\Windows\Fonts\calibri.ttf",
    ]:
        if path and os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()

def draw_board_mini(draw, bx, by, size, pieces=True):
    """Draw a small chess board at (bx,by) with given total size."""
    cell = size // 8
    # Squares
    for row in range(8):
        for col in range(8):
            light = (row + col) % 2 == 1
            color = LIGHT_SQ if light else DARK_SQ
            draw.rectangle([bx+col*cell, by+row*cell, bx+(col+1)*cell-1, by+(row+1)*cell-1], fill=color)
    if not pieces:
        return
    # Draw simplified pieces (circles for pawns, rectangles for major pieces)
    p_size = int(cell * 0.55)
    pad = (cell - p_size) // 2
    def draw_piece(col, row, color):
        x = bx + col*cell + pad
        y = by + row*cell + pad
        draw.ellipse([x, y, x+p_size, y+p_size], fill=color, outline=(0,0,0,180), width=1)

    # Starting position pawns
    for c in range(8):
        draw_piece(c, 1, BLACK_PC)
        draw_piece(c, 6, WHITE_PC)
    # Rooks
    for c in [0,7]:
        draw_piece(c, 0, BLACK_PC)
        draw_piece(c, 7, WHITE_PC)
    # Knights
    for c in [1,6]:
        draw_piece(c, 0, BLACK_PC)
        draw_piece(c, 7, WHITE_PC)
    # Bishops
    for c in [2,5]:
        draw_piece(c, 0, BLACK_PC)
        draw_piece(c, 7, WHITE_PC)
    # Queens
    draw_piece(3, 0, BLACK_PC)
    draw_piece(3, 7, WHITE_PC)
    # Kings
    draw_piece(4, 0, BLACK_PC)
    draw_piece(4, 7, WHITE_PC)

# ── Feature Image 1024×500 ──────────────────────────────────────────────────
def make_feature():
    W, H = 1024, 500
    img = Image.new("RGB", (W, H), BG)
    draw = ImageDraw.Draw(img)

    # Gradient overlay (left dark, right slightly lighter)
    for x in range(W):
        alpha = int(40 * x / W)
        for y in range(H):
            r, g, b = img.getpixel((x, y))
            img.putpixel((x, y), (min(255, r+alpha//4), min(255, g+alpha//4), min(255, b+alpha//4)))

    # Chess board on the right side
    board_size = 380
    board_x = W - board_size - 40
    board_y = (H - board_size) // 2
    draw_board_mini(draw, board_x, board_y, board_size, pieces=True)

    # Glow behind board
    glow = Image.new("RGBA", (W, H), (0,0,0,0))
    gd = ImageDraw.Draw(glow)
    for r in range(220, 0, -10):
        alpha = int(40 * (1 - r/220))
        gd.ellipse([board_x + board_size//2 - r, board_y + board_size//2 - r,
                    board_x + board_size//2 + r, board_y + board_size//2 + r],
                   fill=(198, 149, 60, alpha))
    img = Image.alpha_composite(img.convert("RGBA"), glow).convert("RGB")
    draw = ImageDraw.Draw(img)

    # Knight icon (simplified polygon)
    kx, ky = 80, H//2 - 80
    # Draw a chess knight as a large golden icon using overlapping shapes
    for offset in range(8, 0, -2):
        draw.ellipse([kx-offset, ky-offset, kx+100+offset, ky+120+offset],
                     fill=(*GOLD[:3],), outline=None)
    draw.rectangle([kx+10, ky+80, kx+90, ky+120], fill=GOLD)
    draw.ellipse([kx+5, ky+5, kx+95, ky+75], fill=(40, 32, 16))
    # Knight shape (simplified)
    pts = [kx+50, ky+10, kx+85, ky+30, kx+80, ky+60, kx+60, ky+75,
           kx+70, ky+75, kx+90, ky+120, kx+10, ky+120, kx+30, ky+75,
           kx+20, ky+60, kx+25, ky+35]
    draw.polygon(pts, fill=GOLD, outline=(30,20,5), width=2)

    # Title text
    title_font = font(72, bold=True)
    sub_font = font(28)
    tag_font = font(22)

    draw.text((80, H//2 + 60), "Knightfall", fill=GOLD, font=title_font)
    draw.text((83, H//2 + 140), "Chess  ·  Stockfish AI  ·  Gemma Coach", fill=TEXT_WH, font=sub_font)
    draw.text((83, H//2 + 178), "Free  ·  No Ads  ·  Plays Offline", fill=(160, 140, 110), font=tag_font)

    out = os.path.join(STORE, "graphics", "feature_image_1024x500.png")
    img.save(out)
    print(f"Feature image → {out}")

# ── App Icon 512×512 ────────────────────────────────────────────────────────
def make_icon():
    # Copy the app's existing adaptive icon foreground if we can find it, else generate
    src = r"h:\knightfall\app\src\main\res\mipmap-xxxhdpi\ic_launcher.png"
    out_dir = os.path.join(STORE, "graphics")
    out = os.path.join(out_dir, "icon_512x512.png")
    if os.path.exists(src):
        img = Image.open(src).resize((512, 512), Image.LANCZOS)
        img.save(out)
        print(f"Icon (from launcher) → {out}")
        return

    # Generate icon from scratch
    W = H = 512
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # Rounded square background
    r = 100
    draw.rounded_rectangle([0, 0, W, H], radius=r, fill=(*BG,))
    # Chess board pattern (4x4 mini board) in background
    cs = W // 8
    for row in range(8):
        for col in range(8):
            if (row + col) % 2 == 1:
                draw.rectangle([col*cs, row*cs, (col+1)*cs, (row+1)*cs], fill=(40, 32, 14))

    # Gold circle
    margin = 60
    draw.ellipse([margin, margin, W-margin, H-margin], fill=GOLD)
    # Inner dark
    draw.ellipse([margin+20, margin+20, W-margin-20, H-margin-20], fill=(30, 24, 12))

    # Knight text/shape using a large N
    f_icon = font(220, bold=True)
    draw.text((W//2, H//2), "♞", fill=GOLD, font=f_icon, anchor="mm")

    img.save(out)
    print(f"Icon (generated) → {out}")

# ── Tablet screenshots from phone screenshots ───────────────────────────────
def make_tablet_screenshots():
    phone_shots = sorted([f for f in os.listdir(PHONE) if f.endswith('.png')])

    for tab_dir, tab_w, tab_h in [
        ("tablet_7in", 1200, 1920),
        ("tablet_10in", 1600, 2560),
    ]:
        out_dir = os.path.join(STORE, "graphics", tab_dir)
        os.makedirs(out_dir, exist_ok=True)
        for fname in phone_shots:
            src = os.path.join(PHONE, fname)
            phone_img = Image.open(src)
            pw, ph = phone_img.size

            # Create tablet canvas
            tab = Image.new("RGB", (tab_w, tab_h), BG)

            # Scale phone screenshot to fit nicely centered with margins
            scale = min((tab_w * 0.65) / pw, (tab_h * 0.85) / ph)
            new_w = int(pw * scale)
            new_h = int(ph * scale)
            scaled = phone_img.resize((new_w, new_h), Image.LANCZOS)

            # Draw tablet frame (slightly rounded rectangle behind the phone)
            d = ImageDraw.Draw(tab)
            # Background gradient bands
            for i in range(tab_h):
                darkness = int(20 * (1 - abs(i - tab_h//2) / (tab_h//2)))
                band_color = (BG[0]+darkness, BG[1]+darkness, BG[2]+darkness)
                d.line([(0, i), (tab_w, i)], fill=band_color)

            # Subtle chess board watermark in top-left corner
            board_wm = 240
            draw_board_mini(d, 20, 20, board_wm, pieces=False)
            # Fade the watermark
            wm = Image.new("RGBA", (tab_w, tab_h), (0,0,0,0))
            wmd = ImageDraw.Draw(wm)
            for x in range(board_wm + 20):
                alpha = int(200 * (1 - x / (board_wm + 20)))
                wmd.line([(x, 0), (x, board_wm + 20)], fill=(0, 0, 0, alpha))

            # Paste phone screenshot centered
            x_off = (tab_w - new_w) // 2
            y_off = (tab_h - new_h) // 2
            if phone_img.mode == "RGBA":
                tab.paste(scaled, (x_off, y_off), scaled)
            else:
                tab.paste(scaled, (x_off, y_off))

            # App name watermark bottom-right
            d2 = ImageDraw.Draw(tab)
            f_wm = font(32, bold=True)
            d2.text((tab_w - 20, tab_h - 20), "Knightfall", fill=(*GOLD, 180),
                    font=f_wm, anchor="rb")

            out = os.path.join(out_dir, fname)
            tab.save(out)
        print(f"Tablet {tab_dir}: {len(phone_shots)} screenshots")

make_feature()
make_icon()
make_tablet_screenshots()
print("All graphics done.")
