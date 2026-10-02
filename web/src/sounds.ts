// The game's original sound effects (src/main/resources/sounds), served by WebServer.

const FILES = {
  move: 'moveSound1.wav',
  capture: 'captureSound1.wav',
  castle: 'castlingSound1.wav',
  check: 'checkSound1.wav',
  win: 'winningSound1.wav',
  lose: 'losingSound1.wav',
  draw: 'drawSound1.wav',
  hint: 'hintSound1.wav',
  back: 'goBackSound1.wav',
  invalid: 'invalidMoveSound1.wav',
  select: 'selectPieceSound1.wav',
  start: 'switchSound1.wav',
} as const;

export type Sound = keyof typeof FILES;

const cache = new Map<Sound, HTMLAudioElement>();

export function play(sound: Sound, enabled: boolean) {
  if (!enabled) return;
  let audio = cache.get(sound);
  if (!audio) {
    audio = new Audio(`/sounds/${FILES[sound]}`);
    audio.volume = sound === 'select' ? 0.35 : 0.7;
    cache.set(sound, audio);
  }
  audio.currentTime = 0;
  // browsers refuse sound before the first click; nothing to do about it
  audio.play().catch(() => {});
}
