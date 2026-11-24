# Knightfall - Foreground Service (dataSync) declaration

For the Play Console "Foreground service permissions" declaration for
com.chartmann.knightfall. The app declares FOREGROUND_SERVICE and
FOREGROUND_SERVICE_DATA_SYNC and runs exactly one foreground service:
WorkManager's SystemForegroundService with foregroundServiceType
"dataSync", driven by ModelDownloadWorker.

## What the foreground service does

Knightfall's optional AI coach (Google Gemma 4, ~2.5 GB .litertlm
model file) runs fully on-device. Because the model is far too large
to bundle in the app, the user can choose to download it. That
download runs as a WorkManager one-time work request
(CoachModelManager.startDownload -> ModelDownloadWorker) and is
promoted to a foreground service so the multi-gigabyte transfer is not
killed when the user leaves the app. The worker supplies
ForegroundInfo with FOREGROUND_SERVICE_TYPE_DATA_SYNC and an ongoing
progress notification ("Downloading chess coach - Gemma 4 model -
N%"). The download supports HTTP Range resume and retries, and the
user can cancel it in Settings.

## Console declaration answers

- Foreground service type: **Data sync**
- Feature description / justification (paste into Console):

  "Knightfall offers an optional on-device AI chess coach powered by
  Google's Gemma 4 model. The model file is about 2.5 GB, so it cannot
  be bundled with the app; when the user taps 'Download coach' (during
  onboarding or in Settings), the app downloads the model from
  Hugging Face in the background. The download runs as a dataSync
  foreground service with a visible progress notification so the
  large transfer can finish reliably even if the user switches apps,
  and the user can cancel it at any time in Settings. The foreground
  service is used only for this user-initiated model download and for
  nothing else; the coach itself runs entirely on-device and no data
  leaves the phone."

## Demo video required

Play requires a video demonstrating the foreground-service use case.
Capture (a clean emulator pass is fine, like the Pocket Kin demo):

1. Fresh install; complete onboarding (guest is fine) until the
   "Meet your AI coach" step, OR open Settings -> AI COACH (GEMMA 4)
   on an already-onboarded install where the model is not downloaded.
2. Tap "Download coach (2.5 GB)".
3. Show the in-app progress bar advancing, then pull down the
   notification shade and show the ongoing system notification
   "Downloading chess coach - Gemma 4 model - N%" (this is the
   foreground service made visible).
4. Switch to another app / the home screen for a few seconds, come
   back: the download is still progressing (the point of the FGS).
5. Open Settings and tap "Cancel download" to show user control, or
   let it run a little longer and end the video there. The full
   2.5 GB does not need to complete on camera.

Target length 45-90 seconds. Host the finished video (e.g. unlisted
link or a Drive "anyone with the link" URL, as used for Pocket Kin)
and paste the link into the Console declaration.