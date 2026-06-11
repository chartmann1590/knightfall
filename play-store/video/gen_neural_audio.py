"""Generate voiceover audio using Microsoft Edge neural TTS (en-US-AvaNeural)."""
import asyncio, os, subprocess
import edge_tts

TMP   = r"h:\tmp\knightfall_video"
VOICE = "en-US-AvaNeural"

SEGMENTS = [
    (0, "Introducing Knightfall — beautiful chess on Android, powered by Stockfish and an on-device AI coach.", 5.5),
    (1, "From the home screen, choose how you want to play: challenge the AI, find a quick match, or invite a friend.", 5.0),
    (2, "Play against Stockfish 18 at six difficulty levels. Your on-device Gemma coach watches every move and gives real coaching advice.", 6.0),
    (3, "Legal move dots show exactly where each piece can go. Tap to move, or drag and drop — your choice.", 4.5),
    (4, "Stuck? Just ask Coach Gemma about your position. She runs fully offline — no cloud, no subscription.", 5.0),
    (5, "Every privacy toggle is right here in Settings. Opt in or out of anything — crash reports, analytics, even your public profile.", 5.0),
    (6, "Compete on the global leaderboard. Your Elo rating updates after every rated match.", 4.0),
    (7, "Knightfall. Free, no ads, plays offline. Download it now.", 4.0),
]

async def gen(i, text, dur):
    mp3 = os.path.join(TMP, f"nn_{i:02d}.mp3")
    wav = os.path.join(TMP, f"audio_{i:02d}.wav")
    padded = os.path.join(TMP, f"audio_{i:02d}_pad.wav")

    comm = edge_tts.Communicate(text, VOICE, rate="+5%", volume="+0%")
    await comm.save(mp3)

    # MP3 -> WAV
    subprocess.run(
        ["ffmpeg", "-y", "-i", mp3, wav],
        capture_output=True, check=True,
    )
    # Pad/trim to exact segment duration so all clips stay in sync
    subprocess.run(
        ["ffmpeg", "-y", "-i", wav,
         "-af", f"apad=pad_dur={dur},atrim=end={dur}",
         padded],
        capture_output=True, check=True,
    )
    print(f"  [{i}] done ({dur}s)")

async def main():
    os.makedirs(TMP, exist_ok=True)
    print(f"Generating {len(SEGMENTS)} segments with {VOICE}...")
    for i, text, dur in SEGMENTS:
        await gen(i, text, dur)
    print("All neural audio segments ready.")

asyncio.run(main())
