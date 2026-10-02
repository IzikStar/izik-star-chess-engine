// Sound effects, synthesised with the Web Audio API: soft wooden "tock"s for moves and short
// chimes for events. Nothing to download, and every sound sits at the same quiet level
// (the old .wav files varied a lot in loudness and length).

export type Sound =
  | 'move'
  | 'capture'
  | 'castle'
  | 'check'
  | 'win'
  | 'lose'
  | 'draw'
  | 'hint'
  | 'back'
  | 'invalid'
  | 'select'
  | 'start';

let ctx: AudioContext | null = null;
let master: GainNode | null = null;
let noise: AudioBuffer | null = null;

function audio(): AudioContext | null {
  if (!ctx) {
    try {
      ctx = new AudioContext();
    } catch {
      return null;
    }
    master = ctx.createGain();
    master.gain.value = 0.55;
    master.connect(ctx.destination);
    noise = ctx.createBuffer(1, ctx.sampleRate * 0.2, ctx.sampleRate);
    const data = noise.getChannelData(0);
    for (let i = 0; i < data.length; i++) data[i] = Math.random() * 2 - 1;
  }
  // browsers start the context suspended until the first click; resume is a no-op after that
  if (ctx.state === 'suspended') ctx.resume().catch(() => {});
  return ctx;
}

/** A piece set down on wood: a filtered click over a short low thump. */
function tock(at: number, { pitch = 1, gain = 1 } = {}) {
  const ac = ctx!;
  const src = ac.createBufferSource();
  src.buffer = noise;
  const band = ac.createBiquadFilter();
  band.type = 'bandpass';
  band.frequency.value = 1700 * pitch;
  band.Q.value = 3;
  const g = ac.createGain();
  g.gain.setValueAtTime(0, at);
  g.gain.linearRampToValueAtTime(0.9 * gain, at + 0.002);
  g.gain.exponentialRampToValueAtTime(0.001, at + 0.07);
  src.connect(band).connect(g).connect(master!);
  src.start(at);
  src.stop(at + 0.08);

  const body = ac.createOscillator();
  body.type = 'sine';
  body.frequency.setValueAtTime(220 * pitch, at);
  body.frequency.exponentialRampToValueAtTime(110 * pitch, at + 0.06);
  const bg = ac.createGain();
  bg.gain.setValueAtTime(0.5 * gain, at);
  bg.gain.exponentialRampToValueAtTime(0.001, at + 0.08);
  body.connect(bg).connect(master!);
  body.start(at);
  body.stop(at + 0.09);
}

/** A soft bell-like note. */
function note(at: number, freq: number, length = 0.35, gain = 0.25, type: OscillatorType = 'sine') {
  const ac = ctx!;
  const osc = ac.createOscillator();
  osc.type = type;
  osc.frequency.value = freq;
  const g = ac.createGain();
  g.gain.setValueAtTime(0, at);
  g.gain.linearRampToValueAtTime(gain, at + 0.01);
  g.gain.exponentialRampToValueAtTime(0.001, at + length);
  osc.connect(g).connect(master!);
  osc.start(at);
  osc.stop(at + length + 0.02);
  // a quiet octave above gives it some shimmer
  if (type === 'sine') {
    const over = ac.createOscillator();
    over.frequency.value = freq * 2;
    const og = ac.createGain();
    og.gain.setValueAtTime(0, at);
    og.gain.linearRampToValueAtTime(gain * 0.25, at + 0.01);
    og.gain.exponentialRampToValueAtTime(0.001, at + length * 0.6);
    over.connect(og).connect(master!);
    over.start(at);
    over.stop(at + length);
  }
}

const C5 = 523.25, E5 = 659.25, G5 = 783.99, C6 = 1046.5, G4 = 392, E4 = 329.63, C4 = 261.63, A4 = 440;

export function play(sound: Sound, enabled: boolean) {
  if (!enabled) return;
  const ac = audio();
  if (!ac) return;
  const t = ac.currentTime + 0.01;
  switch (sound) {
    case 'move':
      tock(t);
      break;
    case 'capture':
      tock(t, { pitch: 0.8, gain: 1.2 });
      tock(t + 0.035, { pitch: 1.15, gain: 0.7 });
      break;
    case 'castle':
      tock(t);
      tock(t + 0.11, { pitch: 0.9 });
      break;
    case 'check':
      tock(t, { gain: 1.1 });
      note(t + 0.02, A4 * 2, 0.3, 0.16);
      break;
    case 'win':
      [C5, E5, G5, C6].forEach((f, i) => note(t + i * 0.11, f, 0.5, 0.2));
      break;
    case 'lose':
      [G4, E4, C4].forEach((f, i) => note(t + i * 0.16, f, 0.55, 0.2, 'triangle'));
      break;
    case 'draw':
      note(t, E5, 0.4, 0.18);
      note(t + 0.18, E5, 0.5, 0.18);
      break;
    case 'hint':
      note(t, G5, 0.3, 0.12);
      note(t + 0.07, C6, 0.4, 0.1);
      break;
    case 'back':
      tock(t, { pitch: 0.7, gain: 0.8 });
      break;
    case 'invalid':
      note(t, 150, 0.15, 0.12, 'triangle');
      break;
    case 'select':
      tock(t, { pitch: 1.8, gain: 0.25 });
      break;
    case 'start':
      note(t, G4, 0.3, 0.16);
      note(t + 0.1, C5, 0.45, 0.16);
      break;
  }
}
