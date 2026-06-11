$TMP = "h:\tmp\knightfall_video"
$OUT = "h:\knightfall\play-store\video\knightfall_promo.mp4"

$segments = @(
    @{ i=0; dur=5.5 },
    @{ i=1; dur=5.0 },
    @{ i=2; dur=6.0 },
    @{ i=3; dur=4.5 },
    @{ i=4; dur=5.0 },
    @{ i=5; dur=5.0 },
    @{ i=6; dur=4.0 },
    @{ i=7; dur=4.0 }
)

# Build each segment as a short video (still image held for duration)
$segVideos = @()
foreach ($seg in $segments) {
    $frame = "$TMP\frame_$($seg.i.ToString('D2')).png"
    $segVid = "$TMP\seg_$($seg.i.ToString('D2')).mp4"
    $dur = $seg.dur
    ffmpeg -y -loop 1 -framerate 30 -i $frame -t $dur -c:v libx264 -preset fast -crf 22 -pix_fmt yuv420p -vf "scale=1080:1920" $segVid 2>$null
    $segVideos += $segVid
    Write-Host "Segment $($seg.i) encoded ($dur s)"
}

# Write concat list for video segments
$vlist = $segVideos | ForEach-Object { "file '$($_ -replace '\\', '/')'" }
[System.IO.File]::WriteAllLines("$TMP\vlist.txt", $vlist, [System.Text.UTF8Encoding]::new($false))

# Concat video segments
ffmpeg -y -f concat -safe 0 -i "$TMP\vlist.txt" -c copy "$TMP\video_no_audio.mp4" 2>$null
Write-Host "Video segments concatenated"

# Combine with audio
ffmpeg -y `
    -i "$TMP\video_no_audio.mp4" `
    -i "$TMP\audio_all.wav" `
    -c:v copy `
    -c:a aac -b:a 128k -shortest `
    "$OUT"

Write-Host "Final video: $OUT"
$size = [math]::Round((Get-Item $OUT).Length / 1MB, 1)
Write-Host "Size: $size MB"
