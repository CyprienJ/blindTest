/* GAME VIEWS — Round Playing, Round Revealed, End Screen */

function GameRoomView({ revealed = false }) {
  const players = [
    { name: 'Léa', tone: 3, score: 850, found: revealed, host: true, delta: revealed ? 100 : 0 },
    { name: 'Maxime', tone: 1, score: 720, found: revealed, delta: revealed ? 80 : 0 },
    { name: 'Sofia', tone: 6, score: 690, found: revealed, delta: revealed ? 60 : 0 },
    { name: 'Jordan', tone: 4, score: 540, found: revealed, delta: 0 },
    { name: 'TomatoTom', tone: 2, score: 410, found: false, guest: true, delta: 0 },
  ];

  // Animate timer + audio progress on round-playing
  const [t, setT] = React.useState(revealed ? 30 : 12);
  const [answer, setAnswer] = React.useState(revealed ? '' : 'billie j');
  const [skipVotes, setSkipVotes] = React.useState(revealed ? 0 : 1);

  React.useEffect(() => {
    if (revealed) return;
    const id = setInterval(() => setT(x => x >= 30 ? 12 : x + 0.25), 250);
    return () => clearInterval(id);
  }, [revealed]);

  const timerPct = (t / 30) * 100;
  const timeLeft = Math.max(0, Math.ceil(30 - t));

  return (
    <div className="bt-screen">
      <TopBar active="play" />
      <div style={{ flex: 1, display: 'grid', gridTemplateColumns: '1fr 320px', minHeight: 0 }}>
        {/* Main play area */}
        <div style={{ padding: 28, display: 'flex', flexDirection: 'column', overflow: 'auto' }}>
          {/* Header strip */}
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 18 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              <span className="bt-chip" style={{ background: 'var(--bg-1)' }}>
                <span className="mono">XK9F2A</span>
              </span>
              <span style={{ fontSize: 13, color: 'var(--fg-2)' }}>Track <span style={{ color: 'var(--fg-0)', fontWeight: 600 }}>7</span> of 15</span>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
              <span className="bt-chip bt-chip-accent">{revealed ? 'REVEALED' : 'PLAYING'}</span>
              <span className="mono" style={{ fontSize: 13, color: 'var(--fg-2)' }}>{revealed ? '+5s post-reveal' : `${timeLeft}s left`}</span>
            </div>
          </div>

          {/* Cover + waveform area */}
          <div className="bt-card" style={{ flex: '0 0 auto', display: 'flex', gap: 28, padding: 28, marginBottom: 18 }}>
            <div className="bt-placeholder" style={{ width: 200, height: 200, borderRadius: 'var(--r-lg)', flexShrink: 0, fontSize: 13 }}>
              {revealed ? <img alt="cover" style={{ display: 'none' }} /> : null}
              {revealed ? <span>cover · Thriller</span> : <span style={{ filter: 'blur(8px)' }}>?????</span>}
            </div>
            <div style={{ flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
              <div>
                <div style={{ fontSize: 11, color: 'var(--fg-2)', textTransform: 'uppercase', letterSpacing: '0.08em', fontWeight: 600 }}>
                  {revealed ? 'Now revealed' : 'Now playing'}
                </div>
                <div style={{ fontSize: 28, fontWeight: 700, letterSpacing: '-0.01em', marginTop: 4 }}>
                  {revealed ? 'Billie Jean' : <span style={{ color: 'var(--fg-3)' }}>— — — — —</span>}
                </div>
                <div style={{ fontSize: 15, color: 'var(--fg-1)', marginTop: 6 }}>
                  {revealed ? 'Michael Jackson · 1983 · 80s Classics' : <span style={{ color: 'var(--fg-3)' }}>artist · year</span>}
                </div>
              </div>

              {/* Audio progress */}
              <div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 14, marginBottom: 10 }}>
                  <div className="bt-wave">{[0,1,2,3,4,5,6].map(i => <span key={i} style={{ animationPlayState: revealed ? 'paused' : 'running', height: revealed ? 6 : 24 }}></span>)}</div>
                  <span className="mono" style={{ fontSize: 12, color: 'var(--fg-2)', marginLeft: 'auto' }}>
                    0:{String(Math.floor(t)).padStart(2, '0')} / 0:30
                  </span>
                </div>
                <div className="bt-progress"><div style={{ width: `${timerPct}%` }}></div></div>
              </div>
            </div>
          </div>

          {/* Answer form */}
          {!revealed ? (
            <div className="bt-card" style={{ padding: 18 }}>
              <div style={{ fontSize: 11.5, color: 'var(--fg-2)', textTransform: 'uppercase', letterSpacing: '0.06em', fontWeight: 600, marginBottom: 10 }}>Your answer</div>
              <div style={{ display: 'flex', gap: 10 }}>
                <input className="bt-input bt-input-lg" value={answer} onChange={e => setAnswer(e.target.value)} placeholder="Type the title or artist..." />
                <button className="bt-btn bt-btn-primary bt-btn-lg">
                  {Icon.send} Submit
                </button>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 10, fontSize: 12, color: 'var(--fg-3)' }}>
                <span>Server checks the answer (typos tolerated up to 2 chars)</span>
                <span>Press <span className="mono" style={{ background: 'var(--bg-3)', padding: '1px 5px', borderRadius: 4 }}>↵</span> to send</span>
              </div>
            </div>
          ) : (
            <div className="bt-card" style={{ padding: 18 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 12, color: 'var(--accent)' }}>
                {Icon.check}
                <div>
                  <div style={{ fontSize: 14, fontWeight: 700 }}>Round complete</div>
                  <div style={{ fontSize: 12.5, color: 'var(--fg-2)' }}>Next track in 4 seconds — host can skip ahead</div>
                </div>
                <div style={{ flex: 1 }}></div>
                <button className="bt-btn bt-btn-ghost bt-btn-sm">{Icon.skip} Next now</button>
              </div>
            </div>
          )}

          {/* Skip vote + activity */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14, marginTop: 18 }}>
            <div className="bt-card bt-card-tight">
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
                <span style={{ fontSize: 13, fontWeight: 600 }}>Skip vote</span>
                <span style={{ fontSize: 12, color: 'var(--fg-2)' }}>{skipVotes}/{Math.ceil(players.length * 0.6)} needed</span>
              </div>
              <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                <div style={{ flex: 1, height: 6, background: 'var(--bg-3)', borderRadius: 3, overflow: 'hidden' }}>
                  <div style={{ width: `${(skipVotes / Math.ceil(players.length * 0.6)) * 100}%`, height: '100%', background: 'var(--warn)' }}></div>
                </div>
                <button className="bt-btn bt-btn-ghost bt-btn-sm" onClick={() => setSkipVotes(v => Math.min(Math.ceil(players.length * 0.6), v + 1))} disabled={revealed}>
                  {Icon.skip} Skip
                </button>
              </div>
            </div>

            <div className="bt-card bt-card-tight">
              <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 8 }}>Activity</div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 4, fontSize: 12.5, color: 'var(--fg-1)' }}>
                {revealed ? (
                  <>
                    <ActivityLine tone={3} who="Léa" what="found in 4.2s" accent />
                    <ActivityLine tone={1} who="Maxime" what="found in 8.1s" accent />
                    <ActivityLine tone={6} who="Sofia" what="found in 11.7s" accent />
                  </>
                ) : (
                  <>
                    <ActivityLine tone={3} who="Léa" what="found the answer!" accent />
                    <ActivityLine tone={2} who="TomatoTom" what="voted to skip" />
                    <ActivityLine tone={4} who="Jordan" what="is typing..." muted />
                  </>
                )}
              </div>
            </div>
          </div>
        </div>

        {/* Scoreboard sidebar */}
        <div style={{ borderLeft: '1px solid var(--line-soft)', display: 'flex', flexDirection: 'column', minHeight: 0 }}>
          <div style={{ padding: '20px 20px 12px', borderBottom: '1px solid var(--line-soft)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <h2 className="bt-h2">Scoreboard</h2>
              <span className="bt-chip" style={{ fontSize: 10 }}>LIVE</span>
            </div>
          </div>
          <div style={{ overflow: 'auto', flex: 1, padding: 12 }}>
            {[...players].sort((a, b) => b.score - a.score).map((p, i) => (
              <div key={p.name} style={{
                display: 'flex', alignItems: 'center', gap: 10,
                padding: '10px 10px',
                borderRadius: 'var(--r-md)',
                background: p.found && revealed ? 'oklch(0.24 0.06 130 / 0.4)' : 'transparent',
                marginBottom: 2,
                border: i === 0 && revealed ? '1px solid oklch(0.4 0.12 130)' : '1px solid transparent',
              }}>
                <div style={{ width: 18, textAlign: 'right', fontSize: 12, color: 'var(--fg-3)', fontWeight: 700 }}>{i + 1}</div>
                <Avatar name={p.name} tone={p.tone} size={30} />
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                    <span style={{ fontSize: 13, fontWeight: 600, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{p.name}</span>
                    {p.host && <span style={{ color: 'var(--accent)', fontSize: 10 }}>★</span>}
                  </div>
                  <div className="mono" style={{ fontSize: 11, color: 'var(--fg-3)' }}>{p.score}pts {p.delta > 0 && <span style={{ color: 'var(--accent)', marginLeft: 4 }}>+{p.delta}</span>}</div>
                </div>
                {p.found
                  ? <div style={{ color: 'var(--accent)' }}>{Icon.check}</div>
                  : !revealed && <div style={{ width: 8, height: 8, borderRadius: '50%', background: 'var(--bg-3)' }}></div>
                }
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

function ActivityLine({ tone, who, what, accent, muted }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '3px 0' }}>
      <Avatar name={who} tone={tone} size={18} />
      <span style={{ fontWeight: 600 }}>{who}</span>
      <span style={{ color: accent ? 'var(--accent)' : muted ? 'var(--fg-3)' : 'var(--fg-2)' }}>{what}</span>
    </div>
  );
}

function EndScreenView() {
  const final = [
    { name: 'Léa', tone: 3, score: 1240, host: true },
    { name: 'Maxime', tone: 1, score: 1090 },
    { name: 'Sofia', tone: 6, score: 980 },
    { name: 'Jordan', tone: 4, score: 720 },
    { name: 'TomatoTom', tone: 2, score: 510, guest: true },
  ];
  return (
    <div className="bt-screen">
      <TopBar active="play" />
      <div style={{ flex: 1, padding: 28, overflow: 'auto', display: 'flex', flexDirection: 'column', gap: 24 }}>
        <div style={{ textAlign: 'center' }}>
          <div className="bt-chip bt-chip-accent" style={{ marginBottom: 12 }}>
            <span className="bt-chip-dot"></span> Game finished
          </div>
          <h1 style={{ fontSize: 36, fontWeight: 700, letterSpacing: '-0.02em', margin: '0 0 6px' }}>That's a wrap.</h1>
          <p style={{ color: 'var(--fg-2)', fontSize: 14, margin: 0 }}>15 tracks · 5 players · 11 minutes · 80s Classics</p>
        </div>

        {/* Podium */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1.2fr 1fr', gap: 14, alignItems: 'end', maxWidth: 720, margin: '0 auto', width: '100%' }}>
          {[final[1], final[0], final[2]].map((p, idx) => {
            const ranks = [2, 1, 3];
            const r = ranks[idx];
            const h = r === 1 ? 200 : r === 2 ? 150 : 120;
            const colors = {
              1: 'var(--accent)',
              2: 'oklch(0.78 0.02 80)',
              3: 'oklch(0.62 0.08 50)',
            };
            return (
              <div key={p.name} style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 10 }}>
                <Avatar name={p.name} tone={p.tone} size={r === 1 ? 64 : 48} />
                <div style={{ fontSize: r === 1 ? 17 : 14, fontWeight: 700 }}>{p.name}</div>
                <div className="mono" style={{ fontSize: 13, color: 'var(--fg-2)' }}>{p.score} pts</div>
                <div style={{
                  width: '100%', height: h,
                  background: r === 1 ? 'linear-gradient(180deg, oklch(0.32 0.10 130) 0%, oklch(0.22 0.04 130) 100%)' : 'var(--bg-2)',
                  borderRadius: 'var(--r-md) var(--r-md) 0 0',
                  border: `1px solid ${r === 1 ? 'oklch(0.40 0.12 130)' : 'var(--line-soft)'}`,
                  borderBottom: 'none',
                  display: 'flex', alignItems: 'flex-start', justifyContent: 'center', paddingTop: 14,
                  fontSize: 32, fontWeight: 800, color: colors[r],
                }}>{r}</div>
              </div>
            );
          })}
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: '1.4fr 1fr', gap: 18, maxWidth: 1080, margin: '0 auto', width: '100%' }}>
          {/* Final leaderboard */}
          <div className="bt-card">
            <h2 className="bt-h2" style={{ marginBottom: 12 }}>Final standings</h2>
            <table className="bt-table">
              <thead>
                <tr>
                  <th style={{ width: 40 }}>#</th>
                  <th>Player</th>
                  <th style={{ width: 80 }}>Found</th>
                  <th style={{ width: 90, textAlign: 'right' }}>Score</th>
                </tr>
              </thead>
              <tbody>
                {final.map((p, i) => (
                  <tr key={p.name}>
                    <td style={{ fontWeight: 700, color: 'var(--fg-2)' }}>{i + 1}</td>
                    <td>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                        <Avatar name={p.name} tone={p.tone} size={28} />
                        <span style={{ fontWeight: 600 }}>{p.name}</span>
                        {p.host && <span style={{ color: 'var(--accent)', fontSize: 10 }}>★</span>}
                        {p.guest && <span className="bt-chip" style={{ height: 18, fontSize: 10 }}>guest</span>}
                      </div>
                    </td>
                    <td><span className="mono" style={{ color: 'var(--fg-1)' }}>{15 - i * 2}/15</span></td>
                    <td style={{ textAlign: 'right' }}><span className="mono" style={{ fontWeight: 700 }}>{p.score}</span></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Tracks recap */}
          <div className="bt-card">
            <h2 className="bt-h2" style={{ marginBottom: 12 }}>Tracks played</h2>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8, maxHeight: 380, overflow: 'auto' }}>
              {[
                ['Africa', 'Toto'],
                ['Take On Me', 'a-ha'],
                ['Billie Jean', 'M. Jackson'],
                ['Dancing Queen', 'ABBA'],
                ['Sweet Dreams', 'Eurythmics'],
                ['Like a Prayer', 'Madonna'],
                ['Don\'t Stop Me Now', 'Queen'],
                ['Tainted Love', 'Soft Cell'],
              ].map(([t, a], i) => (
                <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '6px 4px' }}>
                  <div className="bt-placeholder" style={{ width: 36, height: 36, fontSize: 10 }}>♪</div>
                  <div style={{ flex: 1 }}>
                    <div style={{ fontSize: 13, fontWeight: 600 }}>{t}</div>
                    <div style={{ fontSize: 11.5, color: 'var(--fg-2)' }}>{a}</div>
                  </div>
                  <div className="mono" style={{ fontSize: 11, color: 'var(--fg-3)' }}>0{i + 1}</div>
                </div>
              ))}
            </div>
          </div>
        </div>

        <div style={{ display: 'flex', gap: 10, justifyContent: 'center', marginTop: 4 }}>
          <button className="bt-btn bt-btn-primary bt-btn-lg">{Icon.play} Play again</button>
          <button className="bt-btn bt-btn-ghost bt-btn-lg">{Icon.history} Save to history</button>
          <button className="bt-btn bt-btn-ghost bt-btn-lg">{Icon.send} Share results</button>
        </div>
      </div>
    </div>
  );
}

Object.assign(window, { GameRoomView, EndScreenView });
