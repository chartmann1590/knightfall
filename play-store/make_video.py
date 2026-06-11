"""
Build the Knightfall promo video:
 - Title card + each screenshot for 4-5 seconds each
 - Windows SAPI voiceover (WAV) per segment
 - Burn-in captions via ffmpeg drawtext
 - Final h264 MP4 at 1080x1920 (portrait phone)
"""
import os, subprocess, shutil, math
from PIL import Image, ImageDraw, ImageFont

PYTHON = r"C:\Users\Charles\AppData\Local\Programs\Python\Python310\python.exe"
STORE  = r"h:\knightfall\play-store"
PHONE  = os.path.join(STORE, "graphics", "phone")
TMP    = r"h:\tmp\knightfall_video"
OUT    = os.path.join(STORE, "video", "knightfall_promo.mp4")
os.makedirs(TMP, exist_ok=True)

# ── Palette ──────────────────────────────────────────────────────────────────
BG   = (26, 22, 14)
GOLD = (232, 181, 74)
W, H = 1080, 1920   # portrait 1080p

def font(size, bold=False):
    for p in [r"C:\Windows\Fonts\arialbd.ttf" if bold else r"C:\Windows\Fonts\Arial.ttf",
              r"C:\Windows\Fonts\calibri.ttf"]:
        if os.path.exists(p):
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()

# ── Segments: (label, screenshot_file_or_None, voiceover_text, duration_s) ──
phone_shots = sorted([f for f in os.listdir(PHONE) if f.endswith('.png')])

SEGMENTS = [
    ("title",  None,
     "Introducing Knightfall. Beautiful chess on Android — powered by Stockfish and an on-device AI coach.",
     5.5),
    ("home",   "01_home.png",
     "From the home screen, choose how you want to play. Challenge the AI, find a quick match, or invite a friend.",
     5.0),
    ("game",   "02_ai_game.png",
     "Play against Stockfish 18 at six difficulty levels. Your on-device Gemma coach watches every move and gives real coaching advice.",
     6.0),
    ("select", "03_piece_selected.png",
     "Legal move dots show exactly where each piece can go. Tap to move, or drag and drop — your choice.",
     4.5),
    ("coach",  "04_hint.png",
     "Stuck? Ask Coach Gemma anything about your position. She runs fully offline — no cloud, no subscription.",
     5.0),
    ("settings","05_settings.png",
     "Every privacy toggle is right here in Settings. Opt in or out of anything — crash reports, analytics, even your public profile.",
     5.0),
    ("leaderboard","06_leaderboard.png",
     "Compete on the global leaderboard. Your Elo rating updates after every rated match.",
     4.0),
    ("outro",  None,
     "Knightfall. Free, no ads, plays offline. Download it now.",
     4.0),
]

# ── 1. Generate frame PNGs ────────────────────────────────────────────────────
def make_title_frame(label, text_lines, out_path):
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)

    # Checker watermark
    cs = W // 12
    for row in range(H // cs + 1):
        for col in range(W // cs + 1):
            if (row + col) % 2 == 1:
                d.rectangle([col*cs, row*cs, (col+1)*cs, (row+1)*cs], fill=(34, 28, 15))

    # Knight icon (centered, big)
    cx, cy = W // 2, H // 2 - 100
    scale = 6.0
    ox = cx - int(24 * scale)
    oy = cy - int(31 * scale) - 20

    def ts(px, py):
        return (int(ox + px * scale), int(oy + py * scale))

    # Glow
    for r in range(280, 0, -15):
        a = int(60 * (1 - r / 280))
        d.ellipse([cx-r, cy-r, cx+r, cy+r], fill=(*GOLD, a))

    d.ellipse([cx-220, cy-220, cx+220, cy+220], fill=GOLD)
    d.ellipse([cx-180, cy-180, cx+180, cy+180], fill=(30, 24, 12))

    body = [(14,58),(42,58),(42,49),(39,46),(36,43),(42,38),(45,31),(45,24),(36,11),(24,2),(22,0),(21,1.5),(21,5),(17,8),(11,16),(3,28),(4.5,33.8),(9.5,33.6),(17,28),(19.8,30.4),(13,47),(13,52),(14,58)]
    pts = [ts(x,y) for x,y in body]
    d.polygon(pts, fill=GOLD)
    ex, ey = ts(19.2, 11.5)
    d.ellipse([ex-8, ey-8, ex+8, ey+8], fill=(26,22,14))
    bar_pts = [ts(10,56),ts(46,56),ts(49,59),ts(46,62),ts(10,62),ts(7,59)]
    d.polygon(bar_pts, fill=(184,134,46))

    # Text below icon
    y = cy + 260
    for i, (txt, fsize, bold, color) in enumerate(text_lines):
        f = font(fsize, bold)
        bbox = d.textbbox((0, 0), txt, font=f)
        tw = bbox[2] - bbox[0]
        d.text(((W - tw) // 2, y), txt, fill=color, font=f)
        y += fsize + 16

    img.save(out_path)

def make_screenshot_frame(shot_file, caption, out_path):
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)

    # Top bar
    d.rectangle([0, 0, W, 90], fill=(20, 16, 8))
    d.text((W // 2, 45), "Knightfall", fill=GOLD, font=font(36, True), anchor="mm")

    # Phone frame: scale screenshot to fit with padding
    phone_img = Image.open(os.path.join(PHONE, shot_file))
    pw, ph = phone_img.size
    max_w = W - 80
    max_h = H - 230
    scale = min(max_w / pw, max_h / ph)
    nw, nh = int(pw * scale), int(ph * scale)
    scaled = phone_img.resize((nw, nh), Image.LANCZOS)

    # Phone bezel
    bx = (W - nw) // 2 - 8
    by = 100
    d.rounded_rectangle([bx-4, by-4, bx+nw+12, by+nh+12], radius=28, fill=(50, 44, 34))
    img.paste(scaled, ((W - nw) // 2, by))

    # Caption bar at bottom
    cap_y = H - 130
    d.rectangle([0, cap_y - 20, W, H], fill=(16, 12, 6))
    # Word wrap caption
    f_cap = font(34)
    words = caption.split()
    lines = []
    line = ""
    for w in words:
        test = (line + " " + w).strip()
        bbox = d.textbbox((0,0), test, font=f_cap)
        if bbox[2] - bbox[0] > W - 80:
            if line:
                lines.append(line)
            line = w
        else:
            line = test
    if line:
        lines.append(line)
    y_cap = cap_y
    for ln in lines[:2]:
        bbox = d.textbbox((0,0), ln, font=f_cap)
        tw = bbox[2] - bbox[0]
        d.text(((W - tw)//2, y_cap), ln, fill=(230, 220, 200), font=f_cap)
        y_cap += 42

    img.save(out_path)

# ── 2. Generate TTS WAV files (Windows SAPI) ────────────────────────────────
SAPI_SCRIPT = r"h:\tmp\knightfall_video\tts.ps1"
tts_commands = []
for i, (label, shot, text, dur) in enumerate(SEGMENTS):
    wav = os.path.join(TMP, f"audio_{i:02d}.wav")
    tts_commands.append(
        f'$s = New-Object System.Speech.Synthesis.SpeechSynthesizer\n'
        f'$s.SelectVoice("Microsoft Zira Desktop")\n'
        f'$s.Rate = 0\n'
        f'$s.SetOutputToWaveFile("{wav}")\n'
        f'$s.Speak("{text}")\n'
        f'$s.Dispose()\n'
        f'Write-Host "TTS {i}: done"\n'
    )
with open(SAPI_SCRIPT, "w") as f:
    f.write("Add-Type -AssemblyName System.Speech\n")
    f.write("\n".join(tts_commands))

# ── 3. Generate frame images ─────────────────────────────────────────────────
frame_paths = []
for i, (label, shot, text, dur) in enumerate(SEGMENTS):
    out = os.path.join(TMP, f"frame_{i:02d}.png")
    if shot is None:
        # Title or outro
        if i == 0:
            make_title_frame(label, [
                ("Knightfall", 110, True, GOLD),
                ("Chess · AI Coach · Online Play", 42, False, (200, 180, 140)),
                ("", 10, False, BG),
                ("Free  ·  No Ads  ·  Plays Offline", 34, False, (150, 130, 100)),
            ], out)
        else:
            make_title_frame(label, [
                ("Knightfall", 110, True, GOLD),
                ("Download free on Android", 44, False, (200, 180, 140)),
                ("", 10, False, BG),
                ("No ads. No subscriptions.", 36, False, (150, 130, 100)),
                ("Just chess.", 36, False, (150, 130, 100)),
            ], out)
    else:
        make_screenshot_frame(shot, text, out)
    frame_paths.append(out)

print("Frame images generated")

# ── 4. Write ffmpeg concat script ────────────────────────────────────────────
# We'll build a filter_complex that: trims audio to segment duration, overlays
# caption text, then concatenates everything.
# Simpler: use concat demuxer + audio concat.
concat_file = os.path.join(TMP, "concat.txt")
audio_concat = os.path.join(TMP, "audio_concat.txt")

with open(concat_file, "w") as f:
    for i, (label, shot, text, dur) in enumerate(SEGMENTS):
        frame = frame_paths[i]
        f.write(f"file '{frame}'\n")
        f.write(f"duration {dur}\n")

# Write the build script for later execution
BUILD_SH = os.path.join(STORE, "video", "build_video.ps1")
lines = [
    "# Auto-generated - run this to produce knightfall_promo.mp4",
    "Add-Type -AssemblyName System.Speech",
    "",
]

# TTS generation
for i, (label, shot, text, dur) in enumerate(SEGMENTS):
    wav = os.path.join(TMP, f"audio_{i:02d}.wav")
    safe_text = text.replace('"', "'")
    lines += [
        f'$s{i} = New-Object System.Speech.Synthesis.SpeechSynthesizer',
        f'$s{i}.SelectVoice("Microsoft Zira Desktop")',
        f'$s{i}.Rate = 1',
        f'$s{i}.SetOutputToWaveFile("{wav}")',
        f'$s{i}.Speak("{safe_text}")',
        f'$s{i}.Dispose()',
        f'Write-Host "TTS {i} done"',
        "",
    ]

# Pad/trim audio to exact duration then concat
lines += ["# Pad/trim audio segments and concatenate"]
audio_inputs = []
for i, (label, shot, text, dur) in enumerate(SEGMENTS):
    wav_in  = os.path.join(TMP, f"audio_{i:02d}.wav")
    wav_out = os.path.join(TMP, f"audio_{i:02d}_pad.wav")
    audio_inputs.append(wav_out)
    lines.append(
        f'ffmpeg -y -i "{wav_in}" -af "apad=pad_dur={dur},atrim=end={dur}" "{wav_out}"'
    )

# Concat all audio
audio_list = os.path.join(TMP, "audio_list.txt")
lines += [
    f'$alist = @(',
    *[f'  "file {p}",' for p in audio_inputs],
    f') -join "`n"',
    f'$alist | Out-File -Encoding utf8 "{audio_list}"',
    f'ffmpeg -y -f concat -safe 0 -i "{audio_list}" -c copy "{os.path.join(TMP, "audio_all.wav")}"',
]

# Video from frames + audio
lines += [
    "",
    "# Build final video",
    f'ffmpeg -y -f concat -safe 0 -i "{concat_file}" \\',
    f'  -i "{os.path.join(TMP, "audio_all.wav")}" \\',
    f'  -c:v libx264 -preset slow -crf 22 -pix_fmt yuv420p \\',
    f'  -c:a aac -b:a 128k -shortest \\',
    f'  "{OUT}"',
    f'Write-Host "Video saved to {OUT}"',
]

with open(BUILD_SH, "w", encoding="utf-8") as f:
    f.write("\n".join(lines))

print(f"Build script written to {BUILD_SH}")
print("Run: powershell -File " + BUILD_SH)
