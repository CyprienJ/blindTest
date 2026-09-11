# 11. Audio Synchronization (Option 2 — detail)

[Up: Target design](../target.md)

Historical target design, not an implementation reference. Validated roadmap
decisions and executable code take precedence. See [current architecture](../../architecture.md).

### Principle

```
Server                          Clients
   │                               │
   │  startAt = now() + 3000ms     │
   │──── ROUND_STARTED ───────────►│
   │                               │ audio.load(url)
   │                               │ setTimeout(play, startAt - Date.now())
   │  scheduleRoundEnd(startAt     │
   │    + timerSeconds * 1000)     │ audio.play() ← exactly at startAt
```

```typescript
schedulePlay(audioUrl: string, startAt: number): void {
  this.audio.src = audioUrl;
  this.audio.load();

  const delay = startAt - Date.now();
  if (delay <= 0) {
    this.audio.currentTime = Math.abs(delay) / 1000;
    this.audio.play();
  } else {
    setTimeout(() => { this.audio.currentTime = 0; this.audio.play(); }, delay);
  }
}
```

| Case | Behaviour |
|---|---|
| Arrival after `startAt` | Immediate play with `currentTime = lag / 1000` |
| Reconnection mid-round | Client requests `CURRENT_SNAPSHOT`, which retains original `startsAt`, `deadline`, and playback URL |
| Slow network | 3s buffer absorbs typical latencies |

---
