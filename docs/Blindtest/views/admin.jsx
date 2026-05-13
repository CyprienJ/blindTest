/* ADMIN VIEWS — Track list, Track import (YouTube + preview), Playlist editor, User management */

function AdminSide({ active }) {
  const links = [
    ['catalogue', 'Track catalogue', Icon.disc],
    ['imports', 'Imports', Icon.download],
    ['playlists', 'Playlists', Icon.list],
    ['users', 'Users', Icon.users],
  ];
  return (
    <div className="bt-side">
      <div className="bt-side-heading">Admin</div>
      {links.map(([k, l, i]) => (
        <a key={k} href="#" className={active === k ? 'active' : ''}>
          {i}<span>{l}</span>
          {k === 'imports' && <span className="bt-chip bt-chip-warn" style={{ marginLeft: 'auto', height: 18, fontSize: 10, padding: '0 6px' }}>2</span>}
        </a>
      ))}
    </div>
  );
}

function AdminTracksView() {
  const [statusF, setStatusF] = React.useState('all');
  const tracks = [
    { id: 1, title: 'Africa', artist: 'Toto', decade: 1980, cat: 'Pop',     status: 'PUBLISHED', dur: '4:55', added: 'Mar 12' },
    { id: 2, title: 'Take On Me', artist: 'a-ha', decade: 1980, cat: 'Pop', status: 'PUBLISHED', dur: '3:46', added: 'Mar 12' },
    { id: 3, title: 'Billie Jean', artist: 'Michael Jackson', decade: 1980, cat: 'Pop', status: 'PUBLISHED', dur: '4:54', added: 'Mar 11' },
    { id: 4, title: 'Dancing Queen', artist: 'ABBA', decade: 1970, cat: 'Disco', status: 'PUBLISHED', dur: '3:51', added: 'Mar 11' },
    { id: 5, title: 'Roses', artist: 'OutKast', decade: 2000, cat: 'Hip-Hop', status: 'PENDING', dur: '6:09', added: 'Today' },
    { id: 6, title: 'Untitled #4', artist: 'Imported draft', decade: null, cat: '—', status: 'PENDING', dur: '3:22', added: 'Today' },
    { id: 7, title: 'Bad Quality Cut', artist: 'Various', decade: 2010, cat: 'Pop', status: 'REJECTED', dur: '2:04', added: 'Mar 10' },
    { id: 8, title: 'Like a Prayer', artist: 'Madonna', decade: 1980, cat: 'Pop', status: 'PUBLISHED', dur: '5:39', added: 'Mar 9' },
  ];
  const filtered = statusF === 'all' ? tracks : tracks.filter(t => t.status === statusF.toUpperCase());

  const statusChip = (s) => {
    if (s === 'PUBLISHED') return <span className="bt-chip bt-chip-accent">PUBLISHED</span>;
    if (s === 'PENDING') return <span className="bt-chip bt-chip-warn">PENDING</span>;
    return <span className="bt-chip bt-chip-danger">REJECTED</span>;
  };

  return (
    <div className="bt-screen">
      <TopBar active="catalogue" role="admin" />
      <div className="bt-shell" style={{ flex: 1, minHeight: 0 }}>
        <AdminSide active="catalogue" />
        <div className="bt-main">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 }}>
            <h1 className="bt-h1">Track catalogue</h1>
            <button className="bt-btn bt-btn-primary">{Icon.plus} Import from YouTube</button>
          </div>
          <p className="bt-sub">All tracks across PENDING, PUBLISHED, REJECTED. Only PUBLISHED tracks are playable in sessions.</p>

          <div style={{ display: 'flex', gap: 10, marginBottom: 18, alignItems: 'center' }}>
            <div className="bt-segment">
              {[['all','All · 8'], ['published','Published · 4'], ['pending','Pending · 2'], ['rejected','Rejected · 1']].map(([k, l]) => (
                <button key={k} className={statusF === k ? 'on' : ''} onClick={() => setStatusF(k)}>{l}</button>
              ))}
            </div>
            <div style={{ flex: 1 }}></div>
            <div style={{ position: 'relative' }}>
              <input className="bt-input" style={{ width: 240, paddingLeft: 34 }} placeholder="Search tracks..." />
              <div style={{ position: 'absolute', left: 10, top: 13, color: 'var(--fg-3)' }}>{Icon.search}</div>
            </div>
            <button className="bt-btn bt-btn-ghost">{Icon.filter} Filters</button>
          </div>

          <div className="bt-card" style={{ padding: 0 }}>
            <table className="bt-table">
              <thead>
                <tr>
                  <th style={{ width: 36 }}><input type="checkbox" /></th>
                  <th>Track</th>
                  <th>Category</th>
                  <th>Decade</th>
                  <th>Duration</th>
                  <th>Status</th>
                  <th>Added</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {filtered.map(t => (
                  <tr key={t.id}>
                    <td><input type="checkbox" /></td>
                    <td>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                        <div className="bt-placeholder" style={{ width: 36, height: 36, fontSize: 10 }}>♪</div>
                        <div>
                          <div style={{ fontSize: 13.5, fontWeight: 600 }}>{t.title}</div>
                          <div style={{ fontSize: 11.5, color: 'var(--fg-2)' }}>{t.artist}</div>
                        </div>
                      </div>
                    </td>
                    <td><span className="bt-chip">{t.cat}</span></td>
                    <td><span className="mono">{t.decade ? `${t.decade}s` : '—'}</span></td>
                    <td><span className="mono">{t.dur}</span></td>
                    <td>{statusChip(t.status)}</td>
                    <td style={{ color: 'var(--fg-2)' }}>{t.added}</td>
                    <td style={{ textAlign: 'right' }}>
                      <div style={{ display: 'flex', gap: 4, justifyContent: 'flex-end' }}>
                        {t.status === 'PENDING' && <>
                          <button className="bt-btn bt-btn-primary bt-btn-sm">{Icon.check}</button>
                          <button className="bt-btn bt-btn-danger bt-btn-sm">{Icon.x}</button>
                        </>}
                        <button className="bt-btn bt-btn-ghost bt-btn-sm">{Icon.dots}</button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  );
}

function AdminImportView() {
  const [progress, setProgress] = React.useState(72);
  const [stage, setStage] = React.useState('PROCESSING');
  React.useEffect(() => {
    const id = setInterval(() => {
      setProgress(p => {
        if (p >= 100) return 25;
        return p + 1;
      });
    }, 120);
    return () => clearInterval(id);
  }, []);

  return (
    <div className="bt-screen">
      <TopBar active="imports" role="admin" />
      <div className="bt-shell" style={{ flex: 1, minHeight: 0 }}>
        <AdminSide active="imports" />
        <div className="bt-main">
          <h1 className="bt-h1">Import from YouTube</h1>
          <p className="bt-sub">Paste a URL, fill in metadata. yt-dlp + FFmpeg run server-side; progress streams via WebSocket.</p>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 18 }}>
            {/* Left — form */}
            <div className="bt-card">
              <h2 className="bt-h2" style={{ marginBottom: 14 }}>New import</h2>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
                <div className="bt-field">
                  <label className="bt-label">YouTube URL</label>
                  <div style={{ position: 'relative' }}>
                    <input className="bt-input" defaultValue="https://www.youtube.com/watch?v=Zi_XLOBDo_Y" style={{ paddingLeft: 34 }} />
                    <div style={{ position: 'absolute', left: 10, top: 13, color: 'var(--fg-3)' }}>{Icon.link}</div>
                  </div>
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                  <div className="bt-field">
                    <label className="bt-label">Title</label>
                    <input className="bt-input" defaultValue="Billie Jean" />
                  </div>
                  <div className="bt-field">
                    <label className="bt-label">Artist</label>
                    <input className="bt-input" defaultValue="Michael Jackson" />
                  </div>
                  <div className="bt-field">
                    <label className="bt-label">Composer</label>
                    <input className="bt-input" defaultValue="Michael Jackson" />
                  </div>
                  <div className="bt-field">
                    <label className="bt-label">Work / Album</label>
                    <input className="bt-input" defaultValue="Thriller" />
                  </div>
                  <div className="bt-field">
                    <label className="bt-label">Decade</label>
                    <input className="bt-input" defaultValue="1980" />
                  </div>
                  <div className="bt-field">
                    <label className="bt-label">Category</label>
                    <input className="bt-input" defaultValue="Pop" />
                  </div>
                </div>

                <div className="bt-field">
                  <label className="bt-label">Cover image</label>
                  <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
                    <div className="bt-placeholder" style={{ width: 56, height: 56 }}>drop</div>
                    <button className="bt-btn bt-btn-ghost">{Icon.upload} Upload image</button>
                  </div>
                </div>

                <button className="bt-btn bt-btn-primary bt-btn-lg">{Icon.download} Start import</button>
              </div>
            </div>

            {/* Right — pipeline + preview */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
              <div className="bt-card">
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
                  <h2 className="bt-h2">Pipeline · <span className="mono" style={{ color: 'var(--fg-2)', fontWeight: 500 }}>job_8a2f</span></h2>
                  <span className="bt-chip bt-chip-warn"><span className="bt-chip-dot"></span> {stage}</span>
                </div>

                <Stage label="Queued" sub="Admin submitted URL" state="done" />
                <Stage label="Downloading" sub="yt-dlp · 320kbps · 4MB / 5.5MB" state="done" />
                <Stage label="Processing" sub="FFmpeg → MP3 conversion + normalization" state="active" progress={progress} />
                <Stage label="Done" sub="Preview ready · awaiting validation" state="pending" />

                <div style={{ marginTop: 14, padding: 12, background: 'var(--bg-0)', borderRadius: 'var(--r-md)', fontFamily: 'var(--font-mono)', fontSize: 11.5, color: 'var(--fg-2)', maxHeight: 88, overflow: 'auto' }}>
                  [download] 99.8% of 5.52MiB at  1.2MiB/s ETA 00:00<br />
                  [download] 100% of 5.52MiB in 00:04<br />
                  [ExtractAudio] Destination: /media/job_8a2f.mp3<br />
                  [ffmpeg] loudnorm I=-16 LRA=11 TP=-1.5<br />
                  <span style={{ color: 'var(--accent)' }}>WS → IMPORT_PROGRESS {`{`}progress: {progress}{`}`}</span>
                </div>
              </div>

              <div className="bt-card">
                <h2 className="bt-h2" style={{ marginBottom: 12 }}>In-game preview</h2>
                <div style={{ display: 'flex', gap: 14, alignItems: 'center' }}>
                  <div className="bt-placeholder" style={{ width: 80, height: 80 }}>cover</div>
                  <div style={{ flex: 1 }}>
                    <div style={{ fontSize: 14, fontWeight: 700 }}>Billie Jean</div>
                    <div style={{ fontSize: 12, color: 'var(--fg-2)' }}>Michael Jackson · 1980s · Pop</div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginTop: 10 }}>
                      <button className="bt-btn bt-btn-ghost bt-btn-sm">{Icon.play} Listen</button>
                      <div className="bt-progress" style={{ flex: 1 }}><div style={{ width: '32%' }}></div></div>
                      <span className="mono" style={{ fontSize: 11, color: 'var(--fg-3)' }}>0:09 / 0:30</span>
                    </div>
                  </div>
                </div>
                <div style={{ display: 'flex', gap: 8, marginTop: 14 }}>
                  <button className="bt-btn bt-btn-primary" style={{ flex: 1 }}>{Icon.check} Validate (publish)</button>
                  <button className="bt-btn bt-btn-danger" style={{ flex: 1 }}>{Icon.x} Reject</button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function Stage({ label, sub, state, progress }) {
  const dotBg = state === 'done' ? 'var(--accent)' : state === 'active' ? 'oklch(0.32 0.10 80)' : 'var(--bg-3)';
  const dotInk = state === 'done' ? 'var(--accent-ink)' : state === 'active' ? 'var(--warn)' : 'var(--fg-3)';
  return (
    <div style={{ display: 'flex', gap: 12, padding: '8px 0' }}>
      <div style={{ width: 22, height: 22, borderRadius: '50%', background: dotBg, color: dotInk, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0, fontSize: 11, fontWeight: 700, position: 'relative' }}>
        {state === 'done' ? Icon.check : state === 'active' ? <span style={{ width: 8, height: 8, borderRadius: '50%', background: 'currentColor', animation: 'wave 1s ease-in-out infinite' }}></span> : '·'}
      </div>
      <div style={{ flex: 1 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13.5, fontWeight: 600 }}>
          <span style={{ color: state === 'pending' ? 'var(--fg-3)' : 'var(--fg-0)' }}>{label}</span>
          {state === 'active' && <span className="mono" style={{ color: 'var(--warn)' }}>{progress}%</span>}
        </div>
        <div style={{ fontSize: 11.5, color: 'var(--fg-2)', marginTop: 2 }}>{sub}</div>
        {state === 'active' && (
          <div className="bt-progress" style={{ marginTop: 6 }}><div style={{ width: `${progress}%`, background: 'var(--warn)' }}></div></div>
        )}
      </div>
    </div>
  );
}

function AdminPlaylistView() {
  const tracks = [
    { id: 1, title: 'Africa', artist: 'Toto', dur: '4:55' },
    { id: 2, title: 'Take On Me', artist: 'a-ha', dur: '3:46' },
    { id: 3, title: 'Billie Jean', artist: 'Michael Jackson', dur: '4:54' },
    { id: 4, title: 'Sweet Dreams', artist: 'Eurythmics', dur: '3:35' },
    { id: 5, title: 'Like a Prayer', artist: 'Madonna', dur: '5:39' },
    { id: 6, title: 'Tainted Love', artist: 'Soft Cell', dur: '2:42' },
    { id: 7, title: 'Don\'t Stop Me Now', artist: 'Queen', dur: '3:29' },
  ];
  const available = [
    { id: 11, title: 'Karma Chameleon', artist: 'Culture Club' },
    { id: 12, title: 'Sweet Child o\' Mine', artist: 'Guns N\' Roses' },
    { id: 13, title: 'Born in the U.S.A.', artist: 'Bruce Springsteen' },
    { id: 14, title: 'Wake Me Up Before You Go-Go', artist: 'Wham!' },
  ];
  return (
    <div className="bt-screen">
      <TopBar active="playlists" role="admin" />
      <div className="bt-shell" style={{ flex: 1, minHeight: 0 }}>
        <AdminSide active="playlists" />
        <div className="bt-main">
          <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 4 }}>
            <button className="bt-btn bt-btn-ghost bt-btn-sm">{Icon.back}</button>
            <h1 className="bt-h1" style={{ margin: 0 }}>80s Classics</h1>
            <span className="bt-chip">{tracks.length} tracks</span>
          </div>
          <p className="bt-sub">Themed playlist for blindtest sessions · created by admin · last edited 2 days ago</p>

          <div style={{ display: 'grid', gridTemplateColumns: '1.4fr 1fr', gap: 18 }}>
            {/* Tracks in playlist */}
            <div className="bt-card">
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
                <h2 className="bt-h2">Tracks · drag to reorder</h2>
                <button className="bt-btn bt-btn-ghost bt-btn-sm">{Icon.edit} Rename playlist</button>
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                {tracks.map((t, i) => (
                  <div key={t.id} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: 10, background: 'var(--bg-2)', borderRadius: 'var(--r-md)', cursor: 'grab' }}>
                    <div style={{ color: 'var(--fg-3)', fontFamily: 'var(--font-mono)', fontSize: 11, width: 24 }}>{String(i + 1).padStart(2, '0')}</div>
                    <div style={{ color: 'var(--fg-3)' }}>{Icon.dots}</div>
                    <div className="bt-placeholder" style={{ width: 32, height: 32, fontSize: 10 }}>♪</div>
                    <div style={{ flex: 1 }}>
                      <div style={{ fontSize: 13, fontWeight: 600 }}>{t.title}</div>
                      <div style={{ fontSize: 11.5, color: 'var(--fg-2)' }}>{t.artist}</div>
                    </div>
                    <span className="mono" style={{ fontSize: 11.5, color: 'var(--fg-3)' }}>{t.dur}</span>
                    <button className="bt-btn bt-btn-ghost bt-btn-sm">{Icon.x}</button>
                  </div>
                ))}
              </div>
            </div>

            {/* Right rail — meta + add */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
              <div className="bt-card">
                <h2 className="bt-h2" style={{ marginBottom: 12 }}>Details</h2>
                <div className="bt-field" style={{ marginBottom: 10 }}>
                  <label className="bt-label">Name</label>
                  <input className="bt-input" defaultValue="80s Classics" />
                </div>
                <div className="bt-field" style={{ marginBottom: 10 }}>
                  <label className="bt-label">Description</label>
                  <textarea className="bt-input" rows={3} style={{ height: 'auto', padding: '10px 14px', resize: 'none' }} defaultValue="Synth-pop, big choruses, the unmistakable 80s." />
                </div>
                <div className="bt-field">
                  <label className="bt-label">Cover</label>
                  <div className="bt-placeholder" style={{ width: '100%', height: 120 }}>playlist cover</div>
                </div>
              </div>

              <div className="bt-card">
                <h2 className="bt-h2" style={{ marginBottom: 10 }}>Add tracks</h2>
                <div style={{ position: 'relative', marginBottom: 10 }}>
                  <input className="bt-input" placeholder="Search catalogue..." style={{ paddingLeft: 34 }} />
                  <div style={{ position: 'absolute', left: 10, top: 13, color: 'var(--fg-3)' }}>{Icon.search}</div>
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                  {available.map(t => (
                    <div key={t.id} style={{ display: 'flex', alignItems: 'center', gap: 10, padding: 8 }}>
                      <div className="bt-placeholder" style={{ width: 28, height: 28, fontSize: 10 }}>♪</div>
                      <div style={{ flex: 1 }}>
                        <div style={{ fontSize: 12.5, fontWeight: 600 }}>{t.title}</div>
                        <div style={{ fontSize: 11, color: 'var(--fg-3)' }}>{t.artist}</div>
                      </div>
                      <button className="bt-btn bt-btn-ghost bt-btn-sm">{Icon.plus}</button>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function AdminUsersView() {
  const users = [
    { id: 1, name: 'Léa Marin', user: 'leamusic', email: 'lea@example.com', role: 'USER', verified: true, joined: 'Mar 2026', games: 42, tone: 3 },
    { id: 2, name: 'Maxime D.', user: 'max', email: 'max@example.com', role: 'USER', verified: true, joined: 'Feb 2026', games: 28, tone: 1 },
    { id: 3, name: 'Sofia P.', user: 'sofiap', email: 'sofia@example.com', role: 'ADMIN', verified: true, joined: 'Jan 2026', games: 91, tone: 6 },
    { id: 4, name: 'Bruno A.', user: 'brunoA', email: 'bruno@example.com', role: 'USER', verified: false, joined: 'Today', games: 0, tone: 2 },
    { id: 5, name: 'Jordan K.', user: 'jordan', email: 'j.k@example.com', role: 'USER', verified: true, joined: 'Apr 2026', games: 11, tone: 4 },
    { id: 6, name: 'Chloé V.', user: 'chloe', email: 'chloe@example.com', role: 'USER', verified: true, joined: 'Mar 2026', games: 19, tone: 6 },
  ];
  return (
    <div className="bt-screen">
      <TopBar active="users" role="admin" />
      <div className="bt-shell" style={{ flex: 1, minHeight: 0 }}>
        <AdminSide active="users" />
        <div className="bt-main">
          <h1 className="bt-h1">User management</h1>
          <p className="bt-sub">Promote moderators, suspend abusive accounts, delete users.</p>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 12, marginBottom: 18 }}>
            {[
              ['Total users', '1,284'],
              ['Verified', '1,201'],
              ['Admins', '4'],
              ['New this week', '+38'],
            ].map(([k, v]) => (
              <div key={k} className="bt-card" style={{ padding: 14 }}>
                <div style={{ fontSize: 11, color: 'var(--fg-2)', textTransform: 'uppercase', letterSpacing: '0.06em', fontWeight: 600 }}>{k}</div>
                <div className="mono" style={{ fontSize: 22, fontWeight: 700, marginTop: 4 }}>{v}</div>
              </div>
            ))}
          </div>

          <div style={{ display: 'flex', gap: 10, marginBottom: 14 }}>
            <div style={{ position: 'relative', flex: 1, maxWidth: 360 }}>
              <input className="bt-input" placeholder="Search by username or email..." style={{ paddingLeft: 34 }} />
              <div style={{ position: 'absolute', left: 10, top: 13, color: 'var(--fg-3)' }}>{Icon.search}</div>
            </div>
            <div className="bt-segment">
              <button className="on">All</button>
              <button>Users</button>
              <button>Admins</button>
              <button>Unverified</button>
            </div>
          </div>

          <div className="bt-card" style={{ padding: 0 }}>
            <table className="bt-table">
              <thead>
                <tr>
                  <th>User</th>
                  <th>Email</th>
                  <th>Role</th>
                  <th>Joined</th>
                  <th style={{ textAlign: 'right' }}>Games</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {users.map(u => (
                  <tr key={u.id}>
                    <td>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                        <Avatar name={u.name} tone={u.tone} size={32} />
                        <div>
                          <div style={{ fontSize: 13.5, fontWeight: 600 }}>{u.name}</div>
                          <div className="mono" style={{ fontSize: 11.5, color: 'var(--fg-3)' }}>@{u.user}</div>
                        </div>
                      </div>
                    </td>
                    <td>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                        <span>{u.email}</span>
                        {u.verified
                          ? <span className="bt-chip bt-chip-accent" style={{ height: 18, fontSize: 10 }}>✓</span>
                          : <span className="bt-chip bt-chip-warn" style={{ height: 18, fontSize: 10 }}>unverified</span>}
                      </div>
                    </td>
                    <td>
                      <span className="bt-chip" style={{
                        background: u.role === 'ADMIN' ? 'oklch(0.32 0.10 80)' : 'var(--bg-2)',
                        color: u.role === 'ADMIN' ? 'var(--warn)' : 'var(--fg-1)',
                      }}>{u.role}</span>
                    </td>
                    <td style={{ color: 'var(--fg-2)' }}>{u.joined}</td>
                    <td style={{ textAlign: 'right' }} className="mono">{u.games}</td>
                    <td style={{ textAlign: 'right' }}>
                      <div style={{ display: 'flex', gap: 4, justifyContent: 'flex-end' }}>
                        <button className="bt-btn bt-btn-ghost bt-btn-sm">{Icon.edit}</button>
                        <button className="bt-btn bt-btn-ghost bt-btn-sm">{Icon.dots}</button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  );
}

Object.assign(window, { AdminTracksView, AdminImportView, AdminPlaylistView, AdminUsersView });
