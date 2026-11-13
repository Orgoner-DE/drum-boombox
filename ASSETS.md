# Assets — Spaze Drum Boombox 0.2.0

Alle Drum-Sounds in `app/src/main/assets/samples/` wurden für diese App selbst synthetisiert
(Python-Skript `tools/generate_samples.py`, 44,1 kHz, 16-bit PCM, Mono).

Lizenz der Sounds: **CC0 1.0** (Public Domain Dedication). Keine gerippten Bibliotheken.

## Echte Aufnahmen (ab 0.2.0): Kits Dusty, Live, Crate

`app/src/main/assets/samples/{dusty,live,crate}/` enthalten echte akustische Aufnahmen, beide Quellen unter **CC0 1.0**:

- Virtuosity Drums (Versilian Studios / sfzinstruments) – https://github.com/sfzinstruments/virtuosity_drums
- VCSL – Versilian Community Sample Library – https://github.com/sgossner/VCSL

Aufbereitet (Mikrofone derselben Aufnahme summiert, getrimmt, normalisiert, 44,1 kHz / 16 Bit / Mono) mit
`boombap/tools/build_samples.py` im Repository Orgoner-DE.github.io (BAP-50), dort steht die Quelle jeder Datei in
`boombap/samples/LICENSE.md`. In die App kopiert mit `tools/import_real_kits.py`.
Wird `tools/generate_samples.py` erneut ausgeführt, danach `tools/import_real_kits.py` laufen lassen, damit die Kits im Manifest bleiben.

Kits: Street, Steel, Room. Je 16 Pads: Kick, Snare, Clap, Rim, geschlossene/offene HiHat, Shaker, Ride, drei Toms, Glocke, drei Percussion, Crash.

Schrift Orbitron (`app/src/main/assets/fonts/orbitron.ttf`): SIL Open Font License 1.1, siehe `LICENSE-Orbitron-OFL.txt`.

Icons und Oberfläche: eigene Grafik, keine Markenlogos.
