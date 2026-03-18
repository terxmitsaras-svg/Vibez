'use strict';

// ── State ──────────────────────────────────────────────────────────────────
const state = {
  alarms: [],          // { id, time, label, enabled, state: 'idle'|'ringing'|'snoozed' }
  ringing: null,       // alarm currently ringing
  tickInterval: null,
  snoozeMinutes: 5,
};

// ── DOM refs ────────────────────────────────────────────────────────────────
const $ = id => document.getElementById(id);
const clockEl       = $('clock');
const alarmList     = $('alarm-list');
const noAlarms      = $('no-alarms');
const alarmTimeIn   = $('alarm-time');
const alarmLabelIn  = $('alarm-label');
const addBtn        = $('add-alarm-btn');
const overlay       = $('alarm-overlay');
const ringingTime   = $('alarm-ringing-time');
const ringingName   = $('alarm-ringing-name');
const snoozeBtn     = $('snooze-btn');
const dismissBtn    = $('dismiss-btn');
const voiceToggle   = $('voice-toggle');
const voiceStatus   = $('voice-status-text');
const voiceTranscript = $('voice-transcript');
const audioEl       = $('alarm-audio');

// ── Utilities ────────────────────────────────────────────────────────────────
function pad(n) { return String(n).padStart(2, '0'); }

function nowHHMM() {
  const d = new Date();
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function addMinutes(timeStr, mins) {
  const [h, m] = timeStr.split(':').map(Number);
  const total = h * 60 + m + mins;
  return `${pad(Math.floor(total / 60) % 24)}:${pad(total % 60)}`;
}

function uid() { return Date.now().toString(36) + Math.random().toString(36).slice(2); }

// ── Clock ────────────────────────────────────────────────────────────────────
function updateClock() {
  const d = new Date();
  clockEl.textContent = `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

// ── Alarm sound ──────────────────────────────────────────────────────────────
// Synthesize alarm tone via Web Audio API (no external file needed)
let audioCtx = null;
let alarmNodes = [];

function startAlarmSound() {
  stopAlarmSound();
  try {
    audioCtx = new (window.AudioContext || window.webkitAudioContext)();
    scheduleBeeps();
  } catch (e) {
    // AudioContext not supported; fail silently
  }
}

function scheduleBeeps() {
  if (!audioCtx) return;
  let t = audioCtx.currentTime;
  const pattern = [880, 0, 880, 0, 1046, 0, 0, 0]; // Hz, 0 = silence
  const step = 0.15;

  function loop() {
    if (!audioCtx) return;
    for (let i = 0; i < pattern.length; i++) {
      const freq = pattern[i];
      if (freq > 0) {
        const osc = audioCtx.createOscillator();
        const gain = audioCtx.createGain();
        osc.type = 'square';
        osc.frequency.setValueAtTime(freq, t + i * step);
        gain.gain.setValueAtTime(0.18, t + i * step);
        gain.gain.exponentialRampToValueAtTime(0.001, t + i * step + step * 0.9);
        osc.connect(gain);
        gain.connect(audioCtx.destination);
        osc.start(t + i * step);
        osc.stop(t + i * step + step);
        alarmNodes.push(osc);
      }
    }
    t += pattern.length * step + 0.3;
  }

  loop();
  const loopInterval = setInterval(() => {
    if (!audioCtx) { clearInterval(loopInterval); return; }
    loop();
  }, (pattern.length * step + 0.3) * 1000);
  alarmNodes._loopInterval = loopInterval;
}

function stopAlarmSound() {
  if (alarmNodes._loopInterval) clearInterval(alarmNodes._loopInterval);
  alarmNodes.forEach(n => { try { n.stop(); } catch (_) {} });
  alarmNodes = [];
  if (audioCtx) { try { audioCtx.close(); } catch (_) {} audioCtx = null; }
}

// ── Render ───────────────────────────────────────────────────────────────────
function render() {
  // Clear list (keep #no-alarms as reference)
  alarmList.querySelectorAll('.alarm-item').forEach(el => el.remove());

  if (state.alarms.length === 0) {
    noAlarms.style.display = '';
    return;
  }
  noAlarms.style.display = 'none';

  state.alarms.forEach(alarm => {
    const item = document.createElement('div');
    item.className = 'alarm-item';
    if (alarm.state === 'ringing') item.classList.add('active-alarm');
    if (alarm.state === 'snoozed') item.classList.add('snoozed');
    if (!alarm.enabled) item.classList.add('disabled');
    item.dataset.id = alarm.id;

    const stateText = alarm.state === 'snoozed'
      ? `SNOOZED — rings at ${alarm.snoozeUntil}`
      : alarm.state === 'ringing' ? 'RINGING' : '';

    item.innerHTML = `
      <span class="alarm-time-display">${alarm.time}</span>
      <div class="alarm-meta">
        ${alarm.label ? `<span class="alarm-label-text">${alarm.label}</span>` : ''}
        <span class="alarm-state-badge">${stateText}</span>
      </div>
      <button class="alarm-toggle ${alarm.enabled ? 'on' : ''}" title="Toggle alarm" aria-label="Toggle"></button>
      <button class="alarm-delete" title="Delete alarm">&#x2715;</button>
    `;

    item.querySelector('.alarm-toggle').addEventListener('click', () => toggleAlarm(alarm.id));
    item.querySelector('.alarm-delete').addEventListener('click', () => deleteAlarm(alarm.id));
    alarmList.appendChild(item);
  });
}

// ── Alarm management ─────────────────────────────────────────────────────────
function addAlarm() {
  const time = alarmTimeIn.value;
  if (!time) { alarmTimeIn.focus(); return; }
  const label = alarmLabelIn.value.trim();
  state.alarms.push({ id: uid(), time, label, enabled: true, state: 'idle' });
  alarmLabelIn.value = '';
  saveAlarms();
  render();
}

function toggleAlarm(id) {
  const alarm = state.alarms.find(a => a.id === id);
  if (!alarm) return;
  alarm.enabled = !alarm.enabled;
  if (!alarm.enabled && alarm.state === 'ringing') dismissAlarm();
  if (!alarm.enabled && alarm.state === 'snoozed') alarm.state = 'idle';
  saveAlarms();
  render();
}

function deleteAlarm(id) {
  const alarm = state.alarms.find(a => a.id === id);
  if (alarm && alarm.state === 'ringing') dismissAlarm();
  state.alarms = state.alarms.filter(a => a.id !== id);
  saveAlarms();
  render();
}

function triggerAlarm(alarm) {
  if (state.ringing) return; // one at a time
  alarm.state = 'ringing';
  state.ringing = alarm;
  ringingTime.textContent = alarm.time;
  ringingName.textContent = alarm.label || '';
  overlay.classList.remove('hidden');
  startAlarmSound();
  render();
}

function snoozeAlarm() {
  if (!state.ringing) return;
  const alarm = state.ringing;
  alarm.snoozeUntil = addMinutes(nowHHMM(), state.snoozeMinutes);
  alarm.state = 'snoozed';
  state.ringing = null;
  overlay.classList.add('hidden');
  stopAlarmSound();
  showTranscriptFeedback('Snoozed for 5 min');
  saveAlarms();
  render();
}

function dismissAlarm() {
  if (!state.ringing) return;
  const alarm = state.ringing;
  alarm.state = 'idle';
  alarm.snoozeUntil = null;
  state.ringing = null;
  overlay.classList.add('hidden');
  stopAlarmSound();
  showTranscriptFeedback('Alarm dismissed');
  saveAlarms();
  render();
}

// ── Tick (check alarms every second) ─────────────────────────────────────────
function tick() {
  updateClock();
  const hhmm = nowHHMM();
  state.alarms.forEach(alarm => {
    if (!alarm.enabled) return;
    if (alarm.state === 'ringing') return;
    if (alarm.state === 'snoozed') {
      if (alarm.snoozeUntil === hhmm) {
        alarm.state = 'idle';
        triggerAlarm(alarm);
      }
      return;
    }
    if (alarm.state === 'idle' && alarm.time === hhmm) {
      triggerAlarm(alarm);
    }
  });
}

// ── Persistence ──────────────────────────────────────────────────────────────
function saveAlarms() {
  try {
    localStorage.setItem('voxalarm_alarms', JSON.stringify(state.alarms));
  } catch (_) {}
}

function loadAlarms() {
  try {
    const raw = localStorage.getItem('voxalarm_alarms');
    if (raw) {
      state.alarms = JSON.parse(raw);
      // Reset any ringing state from previous session
      state.alarms.forEach(a => { if (a.state === 'ringing') a.state = 'idle'; });
    }
  } catch (_) {}
}

// ── Voice Recognition ────────────────────────────────────────────────────────
let recognition = null;
let voiceActive = false;

const COMMANDS = {
  snooze: ['snooze', 'sleep', 'later', 'five minutes', '5 minutes'],
  dismiss: ['stop', 'dismiss', 'off', 'disable', 'cancel', 'silence', 'shut up'],
  arm: ['arm', 'set alarm', 'enable alarm', 'activate'],
};

function matchCommand(transcript) {
  const t = transcript.toLowerCase();
  if (COMMANDS.snooze.some(w => t.includes(w))) return 'snooze';
  if (COMMANDS.dismiss.some(w => t.includes(w))) return 'dismiss';
  if (COMMANDS.arm.some(w => t.includes(w))) return 'arm';
  return null;
}

function showTranscriptFeedback(msg) {
  voiceTranscript.textContent = msg;
  setTimeout(() => { voiceTranscript.textContent = ''; }, 2500);
}

function startVoice() {
  const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
  if (!SR) {
    showTranscriptFeedback('Voice not supported in this browser');
    return;
  }

  recognition = new SR();
  recognition.lang = 'en-US';
  recognition.continuous = true;
  recognition.interimResults = true;

  recognition.onresult = (e) => {
    let interim = '';
    let final = '';
    for (let i = e.resultIndex; i < e.results.length; i++) {
      const t = e.results[i][0].transcript;
      if (e.results[i].isFinal) final += t;
      else interim += t;
    }

    voiceTranscript.textContent = interim || final;

    if (final) {
      const cmd = matchCommand(final);
      if (cmd === 'snooze') {
        snoozeAlarm();
      } else if (cmd === 'dismiss') {
        if (state.ringing) dismissAlarm();
        else {
          // Disable the next armed alarm
          const next = state.alarms.find(a => a.enabled && a.state === 'idle');
          if (next) { next.enabled = false; saveAlarms(); render(); showTranscriptFeedback(`Alarm ${next.time} disabled`); }
        }
      } else if (cmd === 'arm') {
        // Re-enable all disabled alarms
        const changed = state.alarms.filter(a => !a.enabled);
        changed.forEach(a => { a.enabled = true; });
        if (changed.length) { saveAlarms(); render(); showTranscriptFeedback(`${changed.length} alarm(s) armed`); }
        else showTranscriptFeedback('All alarms already armed');
      } else if (final.trim()) {
        showTranscriptFeedback(`"${final.trim()}" — no command`);
      }
    }
  };

  recognition.onend = () => {
    if (voiceActive) {
      // Auto-restart to keep listening
      try { recognition.start(); } catch (_) {}
    }
  };

  recognition.onerror = (e) => {
    if (e.error === 'not-allowed') {
      showTranscriptFeedback('Mic access denied');
      stopVoice();
    }
  };

  recognition.start();
  voiceActive = true;
  voiceToggle.classList.add('listening');
  voiceStatus.textContent = 'Listening…';
}

function stopVoice() {
  voiceActive = false;
  if (recognition) { try { recognition.stop(); } catch (_) {} recognition = null; }
  voiceToggle.classList.remove('listening');
  voiceStatus.textContent = 'Voice Off';
  voiceTranscript.textContent = '';
}

function toggleVoice() {
  if (voiceActive) stopVoice();
  else startVoice();
}

// ── Event listeners ──────────────────────────────────────────────────────────
addBtn.addEventListener('click', addAlarm);
alarmTimeIn.addEventListener('keydown', e => { if (e.key === 'Enter') addAlarm(); });
alarmLabelIn.addEventListener('keydown', e => { if (e.key === 'Enter') addAlarm(); });
snoozeBtn.addEventListener('click', snoozeAlarm);
dismissBtn.addEventListener('click', dismissAlarm);
voiceToggle.addEventListener('click', toggleVoice);

// ── Init ─────────────────────────────────────────────────────────────────────
loadAlarms();
render();
updateClock();
state.tickInterval = setInterval(tick, 1000);

// Default time input to current time + 1 min
const d = new Date(Date.now() + 60000);
alarmTimeIn.value = `${pad(d.getHours())}:${pad(d.getMinutes())}`;
