/* LOBBY VIEWS — Join Session, Create Session, Lobby Room */

function JoinSessionView() {
  const [code, setCode] = React.useState(['X','K','9','F','2',' ']);
  const refs = React.useRef([]);
  const handleChange = (i, v) => {
    const next = [...code];
    next[i] = v.slice(-1).toUpperCase();
    setCode(next);
    if (v && i < 5) refs.current[i + 1]?.focus();
  };
  const handleKey = (i, e) => {
    if (e.key === 'Backspace' && !code[i] && i > 0) refs.current[i - 1]?.focus();
  };
  const complete = code.every(c => c && c.trim());
  return (
    <AuthFrame
      title="Join a session"
      subtitle="Enter the 6-character code your host shared."
      footer={<>Don't have a code? <a href="#" style={{ color: 'var(--accent)' }}>Host your own</a></>}
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: 22 }}>
        <div style={{ display: 'flex', gap: 8, justifyContent: 'center' }}>
          {code.map((c, i) => (
            <input
              key={i}
              ref={el => refs.current[i] = el}
              value={c.trim()}
              onChange={e => handleChange(i, e.target.value)}
              onKeyDown={e => handleKey(i, e)}
              className="mono"
              style={{
                width: 50, height: 64, textAlign: 'center',
                fontSize: 26, fontWeight: 700,
                background: 'var(--bg-1)',
                border: `1px solid ${c.trim() ? 'var(--accent)' : 'var(--line)'}`,
                borderRadius: 'var(--r-md)',
                color: 'var(--fg-0)',
                textTransform: 'uppercase',
                outline: 'none',
              }}
            />
          ))}
        </div>

        <div className="bt-field">
          <label className="bt-label">Your display name</label>
          <input className="bt-input" defaultValue="Léa" placeholder="Choose any name" />
          <span style={{ fontSize: 11.5, color: 'var(--fg-3)' }}>You're joining as a guest — no account required.</span>
        </div>

        <button className="bt-btn bt-btn-primary bt-btn-lg" disabled={!complete}>
          {complete ? 'Join lobby →' : `Enter ${6 - code.filter(c => c.trim()).length} more character${6 - code.filter(c => c.trim()).length === 1 ? '' : 's'}`}
        </button>

        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, color: 'var(--fg-2)', fontSize: 12.5 }}>
          {Icon.link}
          <span>Or open the link your friend sent you</span>
        </div>
      </div>
    </AuthFrame>
  );
}

function CreateSessionView() {
  const [mode, setMode] = React.useState('mode1');
  const [timer, setTimer] = React.useState(30);
  const [trackCount, setTrackCount] = React.useState(15);
  const [speedBonus, setSpeedBonus] = React.useState(true);
  const [skipPct, setSkipPct] = React.useState(60);
  const [source, setSource] = React.useState('playlist');
  const [pl, setPl] = React.useState('80s Classics');

  const playlists = ['80s Classics', '00s French Pop', 'OST · Movies', 'Hip-Hop Essentials'];

  return (
    <div className="bt-screen">
      <TopBar active="play" />
      <div style={{ flex: 1, display: 'grid', gridTemplateColumns: '1.4fr 1fr', gap: 28, padding: 28, overflow: 'auto', minHeight: 0 }}>
        <div>
          <h1 className="bt-h1">New session</h1>
          <p className="bt-sub">Configure how your blindtest will play.</p>

          <div className="bt-card" style={{ marginBottom: 20 }}>
            <h2 className="bt-h2" style={{ marginBottom: 14 }}>Reveal mode</h2>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
              {[
                { id: 'mode1', label: 'First to find', desc: 'Title revealed as soon as one player gets it.' },
                { id: 'mode2', label: 'Race the clock', desc: 'Revealed when everyone finds — or timer ends. Speed bonus applies.' },
              ].map(opt => (
                <button
                  key={opt.id}
                  onClick={() => setMode(opt.id)}
                  style={{
                    textAlign: 'left',
                    padding: 16,
                    background: mode === opt.id ? 'var(--bg-2)' : 'var(--bg-1)',
                    border: `1px solid ${mode === opt.id ? 'var(--accent)' : 'var(--line-soft)'}`,
                    borderRadius: 'var(--r-md)',
                    color: 'var(--fg-0)',
                    cursor: 'pointer',
                    fontFamily: 'inherit',
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
                    <span style={{ fontWeight: 700, fontSize: 14 }}>{opt.label}</span>
                    <div style={{
                      width: 16, height: 16, borderRadius: '50%',
                      border: `2px solid ${mode === opt.id ? 'var(--accent)' : 'var(--line)'}`,
                      display: 'flex', alignItems: 'center', justifyContent: 'center',
                    }}>
                      {mode === opt.id && <div style={{ width: 7, height: 7, background: 'var(--accent)', borderRadius: '50%' }}></div>}
                    </div>
                  </div>
                  <div style={{ fontSize: 12.5, color: 'var(--fg-2)', lineHeight: 1.5 }}>{opt.desc}</div>
                </button>
              ))}
            </div>
          </div>

          <div className="bt-card" style={{ marginBottom: 20 }}>
            <h2 className="bt-h2" style={{ marginBottom: 14 }}>Rules</h2>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
              <SliderRow label="Timer per track" value={timer} unit="s" min={10} max={60} step={5} onChange={setTimer} />
              <SliderRow label="Number of tracks" value={trackCount} min={5} max={30} step={1} onChange={setTrackCount} />
              <SliderRow label="Skip vote threshold" value={skipPct} unit="%" min={30} max={100} step={10} onChange={setSkipPct} />
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <div>
                  <div style={{ fontSize: 13.5, fontWeight: 600 }}>Speed bonus</div>
                  <div style={{ fontSize: 12, color: 'var(--fg-2)' }}>Earlier answers score more (Mode 2 only)</div>
                </div>
                <Toggle on={speedBonus} onChange={setSpeedBonus} />
              </div>
            </div>
          </div>

          <div className="bt-card">
            <h2 className="bt-h2" style={{ marginBottom: 14 }}>Music source</h2>
            <div className="bt-segment" style={{ marginBottom: 14 }}>
              {[['playlist', 'Playlist'], ['category', 'Category'], ['decade', 'Decade']].map(([k, l]) => (
                <button key={k} className={source === k ? 'on' : ''} onClick={() => setSource(k)}>{l}</button>
              ))}
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
              {playlists.map(p => (
                <button key={p}
                  onClick={() => setPl(p)}
                  style={{
                    textAlign: 'left',
                    padding: 12,
                    background: pl === p ? 'var(--bg-2)' : 'var(--bg-1)',
                    border: `1px solid ${pl === p ? 'var(--accent)' : 'var(--line-soft)'}`,
                    borderRadius: 'var(--r-md)',
                    cursor: 'pointer',
                    fontFamily: 'inherit',
                    color: 'var(--fg-0)',
                    display: 'flex', alignItems: 'center', gap: 10,
                  }}>
                  <div className="bt-placeholder" style={{ width: 38, height: 38 }}>♪</div>
                  <div>
                    <div style={{ fontSize: 13, fontWeight: 600 }}>{p}</div>
                    <div style={{ fontSize: 11, color: 'var(--fg-3)' }}>{20 + p.length} tracks</div>
                  </div>
                </button>
              ))}
            </div>
          </div>
        </div>

        {/* Right rail — summary */}
        <div>
          <div className="bt-card" style={{ position: 'sticky', top: 0 }}>
            <div className="bt-chip bt-chip-accent" style={{ marginBottom: 12 }}>
              <span className="bt-chip-dot"></span> Summary
            </div>
            <SummaryRow k="Reveal mode" v={mode === 'mode1' ? 'First to find' : 'Race the clock'} />
            <SummaryRow k="Tracks" v={`${trackCount}`} />
            <SummaryRow k="Timer" v={`${timer}s per track`} />
            <SummaryRow k="Skip threshold" v={`${skipPct}% of players`} />
            <SummaryRow k="Speed bonus" v={speedBonus ? 'On' : 'Off'} />
            <SummaryRow k="Source" v={pl} />
            <SummaryRow k="Est. duration" v={`~${Math.round(trackCount * (timer + 8) / 60)} min`} />

            <button className="bt-btn bt-btn-primary bt-btn-lg" style={{ width: '100%', marginTop: 18 }}>
              Create session →
            </button>
            <button className="bt-btn bt-btn-ghost" style={{ width: '100%', marginTop: 8 }}>
              Save as preset
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

function SliderRow({ label, value, unit = '', min, max, step, onChange }) {
  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 8 }}>
        <span style={{ fontSize: 13.5, fontWeight: 600 }}>{label}</span>
        <span className="mono" style={{ fontSize: 14, fontWeight: 600, color: 'var(--accent)' }}>{value}{unit}</span>
      </div>
      <input type="range" min={min} max={max} step={step} value={value} onChange={e => onChange(+e.target.value)}
        style={{ width: '100%', accentColor: 'oklch(0.84 0.18 130)' }} />
    </div>
  );
}

function Toggle({ on, onChange }) {
  return (
    <button onClick={() => onChange(!on)} style={{
      width: 38, height: 22,
      background: on ? 'var(--accent)' : 'var(--bg-3)',
      borderRadius: 11,
      border: 'none',
      position: 'relative',
      cursor: 'pointer',
      transition: 'background .15s',
    }}>
      <div style={{
        position: 'absolute',
        top: 3, left: on ? 19 : 3,
        width: 16, height: 16, borderRadius: '50%',
        background: on ? 'var(--accent-ink)' : 'var(--fg-1)',
        transition: 'left .15s',
      }}></div>
    </button>
  );
}

function SummaryRow({ k, v }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', padding: '9px 0', borderBottom: '1px solid var(--line-soft)', fontSize: 13.5 }}>
      <span style={{ color: 'var(--fg-2)' }}>{k}</span>
      <span style={{ fontWeight: 600 }}>{v}</span>
    </div>
  );
}

function LobbyRoomView() {
  const players = [
    { name: 'Léa', tone: 3, host: true, ready: true, guest: false },
    { name: 'Maxime', tone: 1, ready: true, guest: false },
    { name: 'Sofia', tone: 6, ready: true, guest: false },
    { name: 'TomatoTom', tone: 2, ready: false, guest: true },
    { name: 'Jordan', tone: 4, ready: true, guest: false },
  ];
  const friends = [
    { name: 'Anaïs', tone: 5, online: true, invited: false },
    { name: 'Yacine', tone: 4, online: true, invited: true },
    { name: 'Chloé', tone: 6, online: false, invited: false },
    { name: 'Hugo', tone: 1, online: true, invited: false },
  ];
  const [code] = React.useState('XK9F2A');
  const [copied, setCopied] = React.useState(false);

  return (
    <div className="bt-screen">
      <TopBar active="play" />
      <div style={{ flex: 1, display: 'grid', gridTemplateColumns: '1fr 360px', gap: 28, padding: 28, overflow: 'auto', minHeight: 0 }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', marginBottom: 20, flexWrap: 'wrap', gap: 16 }}>
            <div>
              <div style={{ color: 'var(--fg-2)', fontSize: 12, letterSpacing: '0.08em', textTransform: 'uppercase', fontWeight: 600, marginBottom: 6 }}>Session code</div>
              <div className="mono" style={{ fontSize: 48, fontWeight: 700, letterSpacing: '0.08em', lineHeight: 1, display: 'flex', alignItems: 'center', gap: 14 }}>
                {code}
                <button className="bt-btn bt-btn-ghost bt-btn-sm" onClick={() => { setCopied(true); setTimeout(() => setCopied(false), 1500); }}>
                  {copied ? Icon.check : Icon.copy} {copied ? 'Copied' : 'Copy'}
                </button>
              </div>
              <div style={{ fontSize: 13, color: 'var(--fg-2)', marginTop: 8 }}>Share the code or send a friend invite ↗</div>
            </div>

            <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
              <span className="bt-chip bt-chip-accent"><span className="bt-chip-dot"></span> Lobby open</span>
              <span className="bt-chip">{players.length} players</span>
            </div>
          </div>

          <div className="bt-card" style={{ marginBottom: 20 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 14 }}>
              <h2 className="bt-h2">Players in lobby</h2>
              <span style={{ fontSize: 12, color: 'var(--fg-2)' }}>{players.filter(p => p.ready).length}/{players.length} ready</span>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))', gap: 10 }}>
              {players.map(p => (
                <div key={p.name} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: 12, background: 'var(--bg-2)', borderRadius: 'var(--r-md)', border: '1px solid var(--line-soft)' }}>
                  <Avatar name={p.name} tone={p.tone} size={36} />
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                      <span style={{ fontSize: 13.5, fontWeight: 600, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{p.name}</span>
                      {p.host && <span className="bt-chip bt-chip-accent" style={{ height: 18, fontSize: 10 }}>HOST</span>}
                    </div>
                    <div style={{ fontSize: 11.5, color: 'var(--fg-3)' }}>
                      {p.guest ? 'Guest' : 'Registered'} {p.ready ? '· Ready' : '· Not ready'}
                    </div>
                  </div>
                  {p.ready ? <div style={{ color: 'var(--accent)' }}>{Icon.check}</div> : <div style={{ width: 14, height: 14, border: '2px solid var(--line)', borderRadius: '50%' }}></div>}
                </div>
              ))}
            </div>
          </div>

          <div className="bt-card">
            <h2 className="bt-h2" style={{ marginBottom: 12 }}>Game rules</h2>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 12 }}>
              {[
                ['Reveal', 'First to find'],
                ['Tracks', '15'],
                ['Timer', '30s'],
                ['Source', '80s Classics'],
              ].map(([k, v]) => (
                <div key={k} style={{ padding: 12, background: 'var(--bg-2)', borderRadius: 'var(--r-md)' }}>
                  <div style={{ fontSize: 11, color: 'var(--fg-2)', textTransform: 'uppercase', letterSpacing: '0.05em', fontWeight: 600 }}>{k}</div>
                  <div style={{ fontSize: 14, fontWeight: 600, marginTop: 4 }}>{v}</div>
                </div>
              ))}
            </div>
            <div style={{ display: 'flex', gap: 10, marginTop: 18 }}>
              <button className="bt-btn bt-btn-primary bt-btn-lg" style={{ flex: 1 }}>
                {Icon.play} Start game
              </button>
              <button className="bt-btn bt-btn-ghost">{Icon.cog} Edit rules</button>
            </div>
          </div>
        </div>

        {/* Right rail — Invite friends */}
        <div className="bt-card" style={{ alignSelf: 'flex-start' }}>
          <h2 className="bt-h2" style={{ marginBottom: 4 }}>Invite friends</h2>
          <p style={{ fontSize: 12.5, color: 'var(--fg-2)', margin: '0 0 14px' }}>They'll get a notification — no code needed.</p>
          <div style={{ position: 'relative', marginBottom: 12 }}>
            <input className="bt-input" placeholder="Search friends..." style={{ paddingLeft: 36 }} />
            <div style={{ position: 'absolute', left: 12, top: 13, color: 'var(--fg-3)' }}>{Icon.search}</div>
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            {friends.map(f => (
              <div key={f.name} style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '8px 4px' }}>
                <div style={{ position: 'relative' }}>
                  <Avatar name={f.name} tone={f.tone} size={32} />
                  {f.online && <div style={{ position: 'absolute', bottom: 0, right: 0, width: 8, height: 8, borderRadius: '50%', background: 'var(--accent)', border: '2px solid var(--bg-1)' }}></div>}
                </div>
                <div style={{ flex: 1 }}>
                  <div style={{ fontSize: 13.5, fontWeight: 600 }}>{f.name}</div>
                  <div style={{ fontSize: 11, color: 'var(--fg-3)' }}>{f.online ? 'Online' : 'Last seen 2h ago'}</div>
                </div>
                {f.invited
                  ? <span className="bt-chip" style={{ height: 22, fontSize: 11 }}>Invited</span>
                  : <button className="bt-btn bt-btn-ghost bt-btn-sm">Invite</button>
                }
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

Object.assign(window, { JoinSessionView, CreateSessionView, LobbyRoomView });
