# DiaAbend

*[English version](README.md)*

Eine schlichte Diashow-App für Fire TV und Android TV, die die Fotos von den
SMB-Freigaben im Heimnetz auf den Fernseher bringt. Mehr macht sie nicht.

## Warum

Schlichte Fotoviewer fürs lokale Netzwerk gibt es erstaunlich wenige:

- **DLNA** taugt nicht für große Fotosammlungen: flache oder seltsame
  Ordneransichten, langsames Indizieren, und viele Server verschlucken sich an
  zehntausenden Fotos.
- **SMB-Zugriff** können nur wenige Apps, und die sind meist überladene
  Dateimanager oder Mediacenter mit angeflanschtem Bildbetrachter.
- **VLC** kommt dem noch am nächsten, ist aber eigentlich ein Videoplayer, der
  eben auch Fotos zeigen kann.

DiaAbend ist ein Fotoviewer und sonst nichts: Server wählen, Ordner wählen,
Diashow schauen.

## Funktionen

- Findet SMB-Server im lokalen Netz selbst (mDNS und Suche nach Port 445 im
  eigenen /24-Subnetz), oder du gibst Name oder IP-Adresse ein.
- Gastzugang oder Anmeldung mit Benutzername und Passwort. SMB 2.0.2 bis 3.1.1.
- Freigaben und Ordner mit der TV-Fernbedienung durchblättern und aus jedem
  Ordner eine Diashow starten.
- Diashow mit Überblendung, alle 10 Sekunden ein neues Foto.
- Fotos werden aufrecht (EXIF-Ausrichtung) und in Bildschirmgröße dekodiert,
  auch 50-Megapixel-Fotos bringen das Gerät nicht an die Speichergrenze.
- JPEG, PNG, WebP, GIF, BMP, und HEIC/HEIF ab Android 9 (Fire OS 7).
- Läuft ab Fire OS 5 (Android 5.1), auf Android TV und auch auf Handys und
  Tablets.
- Keine Werbung, kein Tracking, kein Konto. Die App spricht nur mit Servern im
  eigenen Netz.

### Fernbedienung

| Taste | In der Diashow |
|---|---|
| Rechts / Vorspulen / Weiter | Nächstes Foto |
| Links / Zurückspulen / Zurück | Voriges Foto |
| OK, hoch, runter, Play/Pause | Pause / weiter |
| Zurück-Taste | Zurück zum Ordner |

Auf einem Touchscreen wechselt Tippen ins linke oder rechte Drittel das Foto,
Tippen in die Mitte pausiert.

## DigikamFS

Besonders gut passt DiaAbend zu
[DigikamFS](https://github.com/euphi/DigikamFS), das eine digiKam-Sammlung als
`<Profil>/<Jahr>/<Album>/<N>Sterne/<Foto>` mit verkleinerten Kopien je
Auflösungsprofil bereitstellt. Sich auf dem Fernseher durch Profil- und
Sterne-Ordner zu klicken nervt schnell, deshalb erkennt DiaAbend diesen Aufbau
und überspringt sie:

- **DigikamFS-Auflösung** (Einstellungen): der Profilordner, der direkt
  geöffnet wird, z.B. `QHD`. Bei einer Freigabe pro Profil (`DigikamFS-QHD`,
  ... wie von `generate_smb_shares.py` angelegt) werden die Freigaben der
  anderen Profile ausgeblendet. Leer lassen, um alle Profile zu sehen.
- **DigikamFS-Bewertungsfilter** (Einstellungen): die Mindestbewertung in
  Sternen. Album-Ordner zeigen dann direkt die Fotos mit mindestens so vielen
  Sternen.

Andere Freigaben bleiben unberührt; die Erkennung greift nur bei Ordnern, die
wie ein DigikamFS-Export aussehen.

## Installation

Die APK gibt es auf der
[Release-Seite](https://github.com/euphi/DiaAbend/releases). Ab Version 0.1.0
ist ein Release auf F-Droid geplant.

Auf einem Fire TV: unter *Einstellungen > Mein Fire TV > Entwickleroptionen >
Unbekannte Apps installieren* eine Downloader-App (z.B. *Downloader*)
freischalten und damit die URL der APK öffnen, oder vom Rechner aus mit
`adb install DiaAbend-vX.Y.Z.apk` installieren.

## Bauen

Braucht JDK 17 oder neuer (JDK 21, um die Release-Builds bytegleich
nachzubauen) und das Android SDK (Platform 35).

```
./gradlew testDebugUnitTest      # JVM-Unit-Tests
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Releases baut und signiert GitHub Actions, siehe
[RELEASING.md](RELEASING.md).

## Lizenz

[MIT](LICENSE). Die SMB-Unterstützung kommt von
[jcifs-ng](https://github.com/AgNO3/jcifs-ng) (LGPL-2.1).
