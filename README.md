# DiaAbend

*[Deutsche Version](README.de.md)*

A plain photo slideshow app for Fire TV and Android TV that shows the photos
on the SMB shares in your home network on the big screen. That's all it does.

## Why

There are surprisingly few plain photo viewers for the local network:

- **DLNA** doesn't hold up with large photo collections: flat or odd folder
  views, slow indexing, and many servers choke on tens of thousands of photos.
- **SMB** access is something only a handful of apps can do, and most of them
  are bloated file managers or media centres with a photo viewer bolted on.
- **VLC** comes closest, but it is a video player that happens to show photos
  too.

DiaAbend ("slide night") is a photo viewer and nothing else: pick a server,
pick a folder, watch the slideshow.

## Features

- Finds SMB servers on the local network by itself (mDNS, plus a scan of the
  local /24 subnet for port 445), or you enter a name or IP address.
- Guest access or login with user name and password. SMB 2.0.2 to 3.1.1.
- Browse shares and folders with the TV remote and start a slideshow of any
  folder.
- Slideshow with crossfades, a new photo every 10 seconds.
- Photos are shown upright (EXIF orientation) and decoded at screen size, so
  even 50 megapixel photos don't run the device out of memory.
- JPEG, PNG, WebP, GIF, BMP, and HEIC/HEIF on Android 9 (Fire OS 7) or newer.
- Runs on Fire OS 5 (Android 5.1) and newer, Android TV, and also on phones
  and tablets.
- No ads, no tracking, no accounts. The app only talks to servers in your own
  network.

### Remote control

| Key | In the slideshow |
|---|---|
| Right / fast forward / next | Next photo |
| Left / rewind / previous | Previous photo |
| OK, up, down, play/pause | Pause / resume |
| Back | Back to the folder |

On a touchscreen, tap the left or right third of the screen to switch photos
and the middle to pause.

## DigikamFS

DiaAbend works especially well with
[DigikamFS](https://github.com/euphi/DigikamFS), which exports a digiKam
collection as `<Profile>/<Year>/<Album>/<N>Sterne/<photo>` with downscaled
copies per resolution profile. Clicking through the profile and star folders
on a TV gets old quickly, so DiaAbend recognises this layout and skips them:

- **DigikamFS resolution** (settings): the profile folder to open directly,
  e.g. `QHD`. With one share per profile (`DigikamFS-QHD`, ... as created by
  `generate_smb_shares.py`), the other profiles' shares are hidden.
  Leave it empty to browse all profiles.
- **DigikamFS rating filter** (settings): the minimum star rating. Album
  folders then show the photos with at least that many stars directly.

Other shares are left alone; the detection only kicks in for folders that
look like a DigikamFS export.

## Install

Download the APK from the
[releases page](https://github.com/euphi/DiaAbend/releases). An F-Droid
release is planned from version 0.1.0 on.

On a Fire TV: enable *Settings > My Fire TV > Developer options > Install
unknown apps* for a downloader app (e.g. *Downloader*) and open the APK's URL
with it, or install from a computer with `adb install DiaAbend-vX.Y.Z.apk`.

## Building

Needs JDK 17 or newer (JDK 21 to reproduce the release builds byte for byte)
and the Android SDK (platform 35).

```
./gradlew testDebugUnitTest      # JVM unit tests
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Releases are built and signed by GitHub Actions, see
[RELEASING.md](RELEASING.md).

## License

[MIT](LICENSE). SMB support comes from
[jcifs-ng](https://github.com/AgNO3/jcifs-ng) (LGPL-2.1).
