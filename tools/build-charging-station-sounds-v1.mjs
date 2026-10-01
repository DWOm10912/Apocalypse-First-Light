// Charging Station sounds: charging start beeps, charge-complete chime, and the charging hum loop, from three
// user-generated source recordings (mono 48 kHz Ogg Vorbis). Placing and taking the item use the vanilla leather armour
// equip sound (user's choice), played by ChargingStationBlock, so there is no set-down recording.
//   node tools/build-charging-station-sounds-v1.mjs [sourceDir]     (default E:/Download; needs ffmpeg on PATH)
// Sources (1 s stereo 48 kHz WAV, left/right correlation 0.998-0.999, checked by SHA-256 prefix):
// - charger_start: one 80 ms piezo blip, a 12.5 kHz tick into a 6.26 kHz tone, very high and piercing. Used twice,
//   pitched down to 2.8 kHz and 3.8 kHz (rate 0.45 / 0.6, longer by the same factor), as two rising beeps;
// - charger_full: a 5.3 kHz tick at 0.00 s (cut), then 932 / 621 / 932 Hz notes and a 697 Hz ding with its octave, loudest
//   at 0.546 s, ringing out by 1.0 s;
// - charger_hum: 50 Hz with a 149 Hz fundamental and harmonics, steady in colour but swelling 18 dB over the second, the
//   high coil whine growing with it and the file ending at full level (the user heard that as a crackle at the end).
//   Only the calm first 0.70 s is used: levelled to one level and made a 0.6 s loop of 30 whole 50 Hz periods
//   (buildLoop), so it never swells and the seam does not click.
// Reference: the industrial electrical box door; the beeps sit 8 LU below it, the chime 2 LU below, the hum 16 LU below.
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {buildSounds, buildLoop} from './sound-mix-lib.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const SRC = process.argv[2] ?? 'E:/Download';
const SOUNDS = path.join(ROOT, 'src/main/resources/assets/apocalypse_firstlight/sounds');
const SOURCES = {charger_start: '3003622ec79a0d46', charger_full: '4b4470646a7c1118'};
const HUM = {name: 'charger_hum', sha: 'e61ccd39b5439ae3'};

// - start: played together with the leather set-down sound; the beeps follow it (peaks at 0.12 s and 0.30 s);
// - full: the leading tick is cut; the ding keeps its place.
// 'at' is where the source's own peak sample lands.
const report = buildSounds({srcDir: SRC, soundsDir: SOUNDS, sources: SOURCES, outputs: {
  start: {file: 'charging_station/start.ogg', reference: 'industrial_electrical_box/open.ogg', offset: -8,
    layers: [{src: 'charger_start', at: 0.12, gain: 0, rate: 0.45}, {src: 'charger_start', at: 0.30, gain: 0, rate: 0.6}]},
  full: {file: 'charging_station/full.ogg', reference: 'industrial_electrical_box/open.ogg', offset: -2,
    layers: [{src: 'charger_full', at: 0.49, gain: 0, trim: 0.06}]},
}});
report.hum = buildLoop({srcDir: SRC, soundsDir: SOUNDS, source: HUM, file: 'charging_station/hum.ogg', from: 0.02, period: 0.02,
  periods: 30, crossfade: 4, reference: 'industrial_electrical_box/open.ogg', offset: -16});
console.log(JSON.stringify(report, null, 1));
