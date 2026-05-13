/* PROFILE & SOCIAL VIEWS — Profile, History, Game Detail, Friends, Requests, Toast */

function MyProfileView() {
  const stats = [
    { k: 'Games played', v: '42' },
    { k: 'Tracks heard', v: '617' },
    { k: 'Win rate', v: '31%' },
    { k: 'Avg. score', v: '892' },
  ];
  return (
    <div className="bt-screen">
      <TopBar active="profile" />
      <div className="bt-shell" style={{ flex: 1, minHeight: 0 }}>
        <ProfileSide active="profile" />
        <div className="bt-main">
          <h1 className="bt-h1">Profile</h1>
          <p className="bt-sub">Public information visible to friends and other players.</p>

          <div className="bt-card" style={{ display: 'grid', gridTemplateColumns: '120px 1fr auto', gap: 24, alignItems: 'center' }}>
            <div style={{ position: 'relative' }}>
              <Avatar name="Léa Marin" tone={3} size={120} />
              <button className="bt-btn bt-btn-ghost bt-btn-sm" style={{ position: 'absolute', bottom: -4, right: -4, padding: '6px 8px' }}>
                {Icon.edit}
              </button>
            </div>
            <div>
              <div style={{ fontSize: 22, fontWeight: 700 }}>leamusic</div>
              <div style={{ fontSize: 13.5, color: 'var(--fg-2)', marginTop: 2 }}>lea@example.com · joined Mar 2026</div>
              <div style={{ display: 'flex', gap: 6, marginTop: 12 }}>
                <span className="bt-chip bt-chip-accent">Verified</span>
                <span className="bt-chip">42 games</span>
                <span className="bt-chip">7 friends</span>
              </div>
            </div>
            <button className="bt-btn bt-btn-ghost">Edit profile</button>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 12, margin: '20px 0' }}>
            {stats.map(s => (
              <div key={s.k} className="bt-card" style={{ padding: 18 }}>
                <div style={{ fontSize: 11.5, color: 'var(--fg-2)', textTransform: 'uppercase', letterSpacing: '0.06em', fontWeight: 600 }}>{s.k}</div>
                <div className="mono" style={{ fontSize: 28, fontWeight: 700, marginTop: 6 }}>{s.v}</div>
              </div>
            ))}
          </div>

          <div className="bt-card">
            <h2 className="bt-h2" style={{ marginBottom: 14 }}>Account settings</h2>
            <SettingsRow icon={Icon.user} label="Username" value="leamusic" />
            <SettingsRow icon={Icon.mail} label="Email" value="lea@example.com" chip="Verified" />
            <SettingsRow icon={Icon.lock} label="Password" value="Last changed 2 months ago" />
            <SettingsRow icon={Icon.shield} label="Two-factor auth" value="Not enabled" action="Enable" />
            <div style={{ marginTop: 16 }}>
              <button className="bt-btn bt-btn-danger">{Icon.trash} Delete account</button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function SettingsRow({ icon, label, value, chip, action = "Edit" }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 14, padding: '14px 0', borderBottom: '1px solid var(--line-soft)' }}>
      <div style={{ color: 'var(--fg-2)' }}>{icon}</div>
      <div style={{ flex: 1 }}>
        <div style={{ fontSize: 13, color: 'var(--fg-2)' }}>{label}</div>
        <div style={{ fontSize: 14, fontWeight: 600, marginTop: 2 }}>{value} {chip && <span className="bt-chip bt-chip-accent" style={{ marginLeft: 8, height: 18, fontSize: 10 }}>{chip}</span>}</div>
      </div>
      <button className="bt-btn bt-btn-ghost bt-btn-sm">{action}</button>
    </div>
  );
}

function ProfileSide({ active }) {
  const links = [
    ['profile', 'Profile', Icon.user],
    ['history', 'Game history', Icon.history],
    ['friends', 'Friends', Icon.users],
    ['requests', 'Friend requests', Icon.bell],
  ];
  return (
    <div className="bt-side">
      <div className="bt-side-heading">Account</div>
      {links.map(([k, l, i]) => (
        <a key={k} href="#" className={active === k ? 'active' : ''}>
          {i}<span>{l}</span>
          {k === 'requests' && <span className="bt-chip bt-chip-accent" style={{ marginLeft: 'auto', height: 18, fontSize: 10, padding: '0 6px' }}>3</span>}
        </a>
      ))}
    </div>
  );
}

function GameHistoryView() {
  const games = [
    { code: 'XK9F2A', when: '2 hours ago', tracks: 15, players: 5, rank: 1, score: 1240, total: 1500 },
    { code: 'M3PQ8C', when: 'Yesterday', tracks: 10, players: 4, rank: 2, score: 780, total: 1000 },
    { code: 'B7HT2D', when: '3 days ago', tracks: 20, players: 6, rank: 1, score: 1610, total: 2000 },
    { code: 'F2WX9E', when: 'Last week', tracks: 15, players: 3, rank: 3, score: 540, total: 1500 },
    { code: 'A1NM4F', when: 'Last week', tracks: 25, players: 8, rank: 4, score: 1200, total: 2500 },
    { code: 'K8RQ1G', when: '2 weeks ago', tracks: 10, players: 5, rank: 2, score: 720, total: 1000 },
  ];
  return (
    <div className="bt-screen">
      <TopBar active="history" />
      <div className="bt-shell" style={{ flex: 1, minHeight: 0 }}>
        <ProfileSide active="history" />
        <div className="bt-main">
          <h1 className="bt-h1">Game history</h1>
          <p className="bt-sub">All your past sessions, ranked by recency.</p>

          <div style={{ display: 'flex', gap: 10, marginBottom: 18 }}>
            <div className="bt-segment">
              <button className="on">All</button>
              <button>Hosted</button>
              <button>Joined</button>
            </div>
            <div style={{ flex: 1 }}></div>
            <button className="bt-btn bt-btn-ghost bt-btn-sm">{Icon.download} Export CSV</button>
          </div>

          <div className="bt-card" style={{ padding: 0 }}>
            <table className="bt-table">
              <thead>
                <tr>
                  <th>Code</th>
                  <th>Played</th>
                  <th>Tracks</th>
                  <th>Players</th>
                  <th>Rank</th>
                  <th style={{ textAlign: 'right' }}>Score</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {games.map(g => (
                  <tr key={g.code}>
                    <td><span className="mono" style={{ fontWeight: 600 }}>{g.code}</span></td>
                    <td style={{ color: 'var(--fg-2)' }}>{g.when}</td>
                    <td>{g.tracks}</td>
                    <td>{g.players}</td>
                    <td>
                      <span className="bt-chip" style={{
                        background: g.rank === 1 ? 'oklch(0.32 0.10 130)' : 'var(--bg-2)',
                        color: g.rank === 1 ? 'var(--accent)' : 'var(--fg-1)',
                      }}>#{g.rank}{g.rank === 1 ? ' 🏆' : ''}</span>
                    </td>
                    <td style={{ textAlign: 'right' }}><span className="mono" style={{ fontWeight: 700 }}>{g.score}</span><span style={{ color: 'var(--fg-3)', fontSize: 11.5 }} className="mono">/{g.total}</span></td>
                    <td style={{ textAlign: 'right' }}><button className="bt-btn bt-btn-ghost bt-btn-sm">View →</button></td>
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

function GameDetailView() {
  const players = [
    { name: 'Léa', tone: 3, score: 1240, found: 13, host: true },
    { name: 'Maxime', tone: 1, score: 1090, found: 12 },
    { name: 'Sofia', tone: 6, score: 980, found: 11 },
    { name: 'Jordan', tone: 4, score: 720, found: 8 },
    { name: 'TomatoTom', tone: 2, score: 510, found: 6, guest: true },
  ];
  const tracks = [
    { t: 'Africa', a: 'Toto', firstBy: 'Léa', time: '3.2s' },
    { t: 'Take On Me', a: 'a-ha', firstBy: 'Sofia', time: '5.1s' },
    { t: 'Billie Jean', a: 'Michael Jackson', firstBy: 'Léa', time: '4.2s' },
    { t: 'Dancing Queen', a: 'ABBA', firstBy: 'Maxime', time: '2.8s' },
    { t: 'Sweet Dreams', a: 'Eurythmics', firstBy: 'Léa', time: '6.7s' },
    { t: 'Like a Prayer', a: 'Madonna', firstBy: '—', time: 'timeout' },
  ];
  return (
    <div className="bt-screen">
      <TopBar active="history" />
      <div className="bt-shell" style={{ flex: 1, minHeight: 0 }}>
        <ProfileSide active="history" />
        <div className="bt-main">
          <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 4 }}>
            <button className="bt-btn bt-btn-ghost bt-btn-sm">{Icon.back}</button>
            <h1 className="bt-h1" style={{ margin: 0 }}>Game <span className="mono" style={{ color: 'var(--fg-2)' }}>XK9F2A</span></h1>
            <span className="bt-chip bt-chip-accent" style={{ marginLeft: 6 }}>Won</span>
          </div>
          <p className="bt-sub">Played 2 hours ago · 15 tracks · 5 players · 80s Classics · First-to-find mode</p>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1.3fr', gap: 18 }}>
            <div className="bt-card">
              <h2 className="bt-h2" style={{ marginBottom: 12 }}>Standings</h2>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                {players.map((p, i) => (
                  <div key={p.name} style={{ display: 'flex', alignItems: 'center', gap: 10, padding: 8, background: i === 0 ? 'oklch(0.24 0.06 130 / 0.4)' : 'var(--bg-2)', borderRadius: 'var(--r-md)' }}>
                    <div style={{ width: 22, textAlign: 'center', fontWeight: 700, color: 'var(--fg-2)' }}>{i + 1}</div>
                    <Avatar name={p.name} tone={p.tone} size={30} />
                    <div style={{ flex: 1 }}>
                      <div style={{ fontSize: 13, fontWeight: 600 }}>{p.name} {p.host && <span style={{ color: 'var(--accent)' }}>★</span>}</div>
                      <div style={{ fontSize: 11, color: 'var(--fg-3)' }}>{p.found}/15 found {p.guest && '· guest'}</div>
                    </div>
                    <div className="mono" style={{ fontWeight: 700 }}>{p.score}</div>
                  </div>
                ))}
              </div>
            </div>

            <div className="bt-card">
              <h2 className="bt-h2" style={{ marginBottom: 12 }}>Tracks (showing 6 of 15)</h2>
              <table className="bt-table">
                <thead>
                  <tr>
                    <th style={{ width: 30 }}>#</th>
                    <th>Track</th>
                    <th>First to find</th>
                    <th style={{ textAlign: 'right' }}>Time</th>
                  </tr>
                </thead>
                <tbody>
                  {tracks.map((tr, i) => (
                    <tr key={i}>
                      <td style={{ color: 'var(--fg-3)' }}>{String(i + 1).padStart(2, '0')}</td>
                      <td>
                        <div style={{ fontSize: 13, fontWeight: 600 }}>{tr.t}</div>
                        <div style={{ fontSize: 11.5, color: 'var(--fg-2)' }}>{tr.a}</div>
                      </td>
                      <td>{tr.firstBy === '—' ? <span style={{ color: 'var(--fg-3)' }}>—</span> : tr.firstBy}</td>
                      <td style={{ textAlign: 'right' }}><span className="mono" style={{ color: tr.time === 'timeout' ? 'var(--danger)' : 'var(--fg-1)' }}>{tr.time}</span></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function FriendsView() {
  const friends = [
    { name: 'Anaïs Lefebvre', user: 'anais.l', tone: 5, online: true, last: 'In session' },
    { name: 'Yacine B.', user: 'yacineb', tone: 4, online: true, last: 'Online now' },
    { name: 'Chloé V.', user: 'chloe', tone: 6, online: false, last: '2 hours ago' },
    { name: 'Hugo M.', user: 'hugo', tone: 1, online: true, last: 'Online now' },
    { name: 'Maxime D.', user: 'max', tone: 2, online: false, last: 'Yesterday' },
    { name: 'Sofia P.', user: 'sofiap', tone: 6, online: true, last: 'Online now' },
    { name: 'Jordan K.', user: 'jordan', tone: 4, online: false, last: '4 days ago' },
  ];
  return (
    <div className="bt-screen">
      <TopBar active="friends" />
      <div className="bt-shell" style={{ flex: 1, minHeight: 0 }}>
        <ProfileSide active="friends" />
        <div className="bt-main">
          <h1 className="bt-h1">Friends</h1>
          <p className="bt-sub">Invite them straight from your lobby without sharing the code.</p>

          <div style={{ display: 'flex', gap: 10, marginBottom: 18 }}>
            <div style={{ position: 'relative', flex: 1, maxWidth: 360 }}>
              <input className="bt-input" placeholder="Search players to add..." style={{ paddingLeft: 36 }} />
              <div style={{ position: 'absolute', left: 12, top: 13, color: 'var(--fg-3)' }}>{Icon.search}</div>
            </div>
            <button className="bt-btn bt-btn-primary">{Icon.plus} Add friend</button>
          </div>

          <div className="bt-card" style={{ padding: 0 }}>
            <table className="bt-table">
              <thead>
                <tr>
                  <th>Player</th>
                  <th>Status</th>
                  <th>Last seen</th>
                  <th style={{ textAlign: 'right' }}></th>
                </tr>
              </thead>
              <tbody>
                {friends.map(f => (
                  <tr key={f.user}>
                    <td>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                        <div style={{ position: 'relative' }}>
                          <Avatar name={f.name} tone={f.tone} size={36} />
                          {f.online && <div style={{ position: 'absolute', bottom: 0, right: 0, width: 10, height: 10, borderRadius: '50%', background: 'var(--accent)', border: '2px solid var(--bg-1)' }}></div>}
                        </div>
                        <div>
                          <div style={{ fontSize: 13.5, fontWeight: 600 }}>{f.name}</div>
                          <div className="mono" style={{ fontSize: 11.5, color: 'var(--fg-3)' }}>@{f.user}</div>
                        </div>
                      </div>
                    </td>
                    <td>{f.online ? <span className="bt-chip bt-chip-accent"><span className="bt-chip-dot"></span> Online</span> : <span className="bt-chip">Offline</span>}</td>
                    <td style={{ color: 'var(--fg-2)' }}>{f.last}</td>
                    <td style={{ textAlign: 'right' }}>
                      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 6 }}>
                        <button className="bt-btn bt-btn-ghost bt-btn-sm" disabled={!f.online}>{Icon.send} Invite</button>
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

function FriendRequestsView() {
  const received = [
    { name: 'Camille R.', user: 'camille', tone: 5, when: '4 minutes ago' },
    { name: 'Sami', user: 'sami', tone: 4, when: '2 hours ago' },
    { name: 'Eloïse', user: 'eloise', tone: 3, when: 'Yesterday' },
  ];
  const sent = [
    { name: 'Bruno A.', user: 'brunoA', tone: 2, when: '3 days ago' },
    { name: 'Maëlle', user: 'mae', tone: 6, when: 'Last week' },
  ];
  return (
    <div className="bt-screen">
      <TopBar active="friends" />
      <div className="bt-shell" style={{ flex: 1, minHeight: 0 }}>
        <ProfileSide active="requests" />
        <div className="bt-main">
          <h1 className="bt-h1">Friend requests</h1>
          <p className="bt-sub">Accept new friends or cancel outgoing requests.</p>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 18 }}>
            <div className="bt-card">
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
                <h2 className="bt-h2">Received</h2>
                <span className="bt-chip bt-chip-accent" style={{ height: 20, fontSize: 11 }}>{received.length}</span>
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {received.map(r => (
                  <div key={r.user} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: 12, background: 'var(--bg-2)', borderRadius: 'var(--r-md)' }}>
                    <Avatar name={r.name} tone={r.tone} size={40} />
                    <div style={{ flex: 1 }}>
                      <div style={{ fontSize: 13.5, fontWeight: 600 }}>{r.name}</div>
                      <div style={{ fontSize: 11.5, color: 'var(--fg-3)' }}>@{r.user} · {r.when}</div>
                    </div>
                    <button className="bt-btn bt-btn-primary bt-btn-sm">{Icon.check}</button>
                    <button className="bt-btn bt-btn-ghost bt-btn-sm">{Icon.x}</button>
                  </div>
                ))}
              </div>
            </div>

            <div className="bt-card">
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
                <h2 className="bt-h2">Sent</h2>
                <span className="bt-chip" style={{ height: 20, fontSize: 11 }}>{sent.length}</span>
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {sent.map(r => (
                  <div key={r.user} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: 12, background: 'var(--bg-2)', borderRadius: 'var(--r-md)' }}>
                    <Avatar name={r.name} tone={r.tone} size={40} />
                    <div style={{ flex: 1 }}>
                      <div style={{ fontSize: 13.5, fontWeight: 600 }}>{r.name}</div>
                      <div style={{ fontSize: 11.5, color: 'var(--fg-3)' }}>@{r.user} · Sent {r.when}</div>
                    </div>
                    <span className="bt-chip bt-chip-warn" style={{ marginRight: 6 }}>Pending</span>
                    <button className="bt-btn bt-btn-ghost bt-btn-sm">Cancel</button>
                  </div>
                ))}
              </div>
            </div>
          </div>

          {/* Notification toasts preview */}
          <div style={{ marginTop: 22 }}>
            <div className="bt-side-heading" style={{ padding: '0 0 8px' }}>Notification toasts (preview)</div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: 12 }}>
              <Toast kind="invite" />
              <Toast kind="friend" />
              <Toast kind="accepted" />
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function Toast({ kind }) {
  if (kind === 'invite') {
    return (
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, padding: 14, background: 'var(--bg-2)', border: '1px solid var(--line)', borderLeft: '3px solid var(--accent)', borderRadius: 'var(--r-md)' }}>
        <Avatar name="Maxime D." tone={1} />
        <div style={{ flex: 1 }}>
          <div style={{ fontSize: 13.5, fontWeight: 600 }}>Maxime invited you</div>
          <div style={{ fontSize: 12, color: 'var(--fg-2)' }}>Session <span className="mono">XK9F2A</span> · in lobby</div>
        </div>
        <button className="bt-btn bt-btn-primary bt-btn-sm">Join</button>
      </div>
    );
  }
  if (kind === 'friend') {
    return (
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, padding: 14, background: 'var(--bg-2)', border: '1px solid var(--line)', borderLeft: '3px solid var(--info)', borderRadius: 'var(--r-md)' }}>
        <Avatar name="Sami" tone={4} />
        <div style={{ flex: 1 }}>
          <div style={{ fontSize: 13.5, fontWeight: 600 }}>Sami wants to be your friend</div>
          <div style={{ fontSize: 12, color: 'var(--fg-2)' }}>@sami · just now</div>
        </div>
        <button className="bt-btn bt-btn-ghost bt-btn-sm">{Icon.check}</button>
        <button className="bt-btn bt-btn-ghost bt-btn-sm">{Icon.x}</button>
      </div>
    );
  }
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 12, padding: 14, background: 'var(--bg-2)', border: '1px solid var(--line)', borderLeft: '3px solid var(--accent)', borderRadius: 'var(--r-md)' }}>
      <div style={{ width: 36, height: 36, borderRadius: '50%', background: 'oklch(0.32 0.10 130)', color: 'var(--accent)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>{Icon.check}</div>
      <div style={{ flex: 1 }}>
        <div style={{ fontSize: 13.5, fontWeight: 600 }}>Bruno accepted your request</div>
        <div style={{ fontSize: 12, color: 'var(--fg-2)' }}>You're now friends</div>
      </div>
    </div>
  );
}

Object.assign(window, { MyProfileView, GameHistoryView, GameDetailView, FriendsView, FriendRequestsView });
