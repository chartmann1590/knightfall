Add-Type -AssemblyName System.Speech

$TMP = "h:\tmp\knightfall_video"
$OUT = "h:\knightfall\play-store\video\knightfall_promo.mp4"

$segments = @(
    @{ i=0; text="Introducing Knightfall. Beautiful chess on Android, powered by Stockfish and an on-device AI coach."; dur=5.5 },
    @{ i=1; text="From the home screen, choose how you want to play. Challenge the AI, find a quick match, or invite a friend."; dur=5.0 },
    @{ i=2; text="Play against Stockfish 18 at six difficulty levels. Your on-device Gemma coach watches every move and gives real coaching advice."; dur=6.0 },
    @{ i=3; text="Legal move dots show exactly where each piece can go. Tap to move, or drag and drop, your choice."; dur=4.5 },
    @{ i=4; text="Stuck? Ask Coach Gemma anything about your position. She runs fully offline, no cloud, no subscription."; dur=5.0 },
    @{ i=5; text="Every privacy toggle is right here in Settings. Opt in or out of anything, crash reports, analytics, even your public profile."; dur=5.0 },
    @{ i=6; text="Compete on the global leaderboard. Your Elo rating updates after every rated match."; dur=4.0 },
    @{ i=7; text="Knightfall. Free, no ads, plays offline. Download it now."; dur=4.0 }
)

foreach ($seg in $segments) {
    $wav = "$TMP\audio_$($seg.i.ToString('D2')).wav"
    $synth = New-Object System.Speech.Synthesis.SpeechSynthesizer
    $synth.SelectVoice("Microsoft Zira Desktop")
    $synth.Rate = 1
    $synth.SetOutputToWaveFile($wav)
    $synth.Speak($seg.text)
    $synth.Dispose()
    Write-Host "TTS $($seg.i) done"
}

foreach ($seg in $segments) {
    $wavIn  = "$TMP\audio_$($seg.i.ToString('D2')).wav"
    $wavOut = "$TMP\audio_$($seg.i.ToString('D2'))_pad.wav"
    $dur = $seg.dur
    ffmpeg -y -i $wavIn -af "apad=pad_dur=$dur,atrim=end=$dur" $wavOut 2>$null
    Write-Host "Padded audio $($seg.i)"
}

$alist = @()
foreach ($seg in $segments) {
    $alist += "file '$TMP\audio_$($seg.i.ToString('D2'))_pad.wav'"
}
$alist -join "`n" | Out-File -Encoding utf8 "$TMP\audio_list.txt"
ffmpeg -y -f concat -safe 0 -i "$TMP\audio_list.txt" -c copy "$TMP\audio_all.wav" 2>$null
Write-Host "Audio concatenated"

ffmpeg -y `
    -f concat -safe 0 -i "$TMP\concat.txt" `
    -i "$TMP\audio_all.wav" `
    -c:v libx264 -preset slow -crf 22 -pix_fmt yuv420p `
    -c:a aac -b:a 128k -shortest `
    "$OUT"

Write-Host "Video saved to $OUT"
