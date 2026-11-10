# Spaze Drum Boombox 0.1.0

Drum-Pad-Boombox mit 16 Pads, 4 Bänken, 16-Step-Sequencer und USB-MIDI.

## Funktionen

- 16 anschlagdynamische Pads, Kits Street / Steel / Room (selbst synthetisiert, CC0)
- 4 Bänke A–D, Lautstärke / Tune / Filter / Decay je Pad
- 16-Step-Sequencer mit Tempo und Swing, Play / Stop / Rec (quantisiert)
- Note Repeat im aktuellen Tempo
- Hintergrund-Wiedergabe über Vordergrunddienst und Medienbenachrichtigung mit Stop
- USB-MIDI (Android MIDI API, Hot-Plug):
  - APC mini mk1 und mk2: Pad-Noten, LED-Rückmeldung in den App-Padfarben, Steps, Bänke
  - Studio-Controller: normale MIDI-Noten (Standardbereich C1–D#2 plus Learn)
  - generisches MIDI-Learn je Pad und Funktion, speichern und zurücksetzen

## Wichtiger Hinweis zum Studio-Controller

Ein angeschlossenes Studio-Pad-Gerät wird nur als USB-MIDI-Klasse gelesen: die Noten und die Anschlagdynamik, die es sendet. Ein natives Standalone-MIDI-Profil dieses Geräts wird nicht nachgebildet und nicht zugesagt.

Die Optik ist eine eigene, generische Sampler-Boombox. Es gibt keine Markenlogos und keine Produktnamen in der Oberfläche.

## Hardware

MIDI, LEDs, Latenz auf dem Telefon, Hintergrund mit ausgeschaltetem Display und 200-%-Schrift am Gerät wurden in dieser Version nicht mit echter Hardware abgenommen.

## Build

Paket `de.spaze.drumboombox`, versionName 0.1.0, versionCode 1.
Signatur über den projekt-eigenen Release-Keystore (nicht im Repository).
