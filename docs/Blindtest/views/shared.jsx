/* Shared atoms: Topbar, Sidebar, Icons (minimal stroke), Avatar */

const I = ({ d, size = 16, fill, stroke = "currentColor", sw = 1.6 }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill={fill || "none"} stroke={stroke} strokeWidth={sw} strokeLinecap="round" strokeLinejoin="round">
    {typeof d === 'string' ? <path d={d} /> : d}
  </svg>
);

const Icon = {
  home:     <I d="M3 11.5 12 4l9 7.5V20a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1Z" />,
  play:     <I d={<polygon points="6 4 20 12 6 20 6 4" fill="currentColor" stroke="none" />} />,
  trophy:   <I d="M8 4h8v4a4 4 0 0 1-8 0V4Zm-3 1H3v2a3 3 0 0 0 3 3M16 5h2v2a3 3 0 0 1-3 3M9 14h6v3H9zM7 21h10" />,
  user:     <I d="M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm-7 9a7 7 0 0 1 14 0" />,
  users:    <I d="M16 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm6 9a7 7 0 0 0-12-5M10 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm-8 9a7 7 0 0 1 14 0" />,
  music:    <I d="M9 18V5l12-2v13M9 18a3 3 0 1 1-3-3M21 16a3 3 0 1 1-3-3" />,
  bell:     <I d="M6 8a6 6 0 0 1 12 0c0 7 3 8 3 8H3s3-1 3-8M10 21a2 2 0 0 0 4 0" />,
  search:   <I d="M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16Zm10 2-4.3-4.3" />,
  plus:     <I d="M12 5v14M5 12h14" />,
  check:    <I d="m5 13 4 4L19 7" />,
  x:        <I d="M6 6l12 12M18 6 6 18" />,
  arrow:    <I d="M5 12h14M13 6l6 6-6 6" />,
  back:     <I d="M19 12H5M11 6l-6 6 6 6" />,
  copy:     <I d="M9 9h10v10H9zM5 15H4a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1v1" />,
  cog:      <I d="M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6Zm9-3a8.5 8.5 0 0 0-.15-1.6l2-1.5-2-3.4-2.3.9a8.5 8.5 0 0 0-2.8-1.6l-.35-2.4h-4l-.35 2.4a8.5 8.5 0 0 0-2.8 1.6L4 5.5l-2 3.4 2 1.5A8.5 8.5 0 0 0 3.85 12c0 .55.05 1.08.15 1.6l-2 1.5 2 3.4 2.3-.9a8.5 8.5 0 0 0 2.8 1.6l.35 2.4h4l.35-2.4a8.5 8.5 0 0 0 2.8-1.6l2.3.9 2-3.4-2-1.5c.1-.52.15-1.05.15-1.6Z" />,
  log:      <I d="M14 4h5v16h-5M10 8l-4 4 4 4M6 12h11" />,
  list:     <I d="M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01" />,
  disc:     <I d="M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20Zm0-7a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z" />,
  flag:     <I d="M4 21V4l8 2 8-2v11l-8 2-8-2Z" />,
  download: <I d="M12 4v12m0 0 5-5m-5 5-5-5M4 20h16" />,
  link:     <I d="M10 14a5 5 0 0 0 7 0l3-3a5 5 0 0 0-7-7l-1 1M14 10a5 5 0 0 0-7 0l-3 3a5 5 0 0 0 7 7l1-1" />,
  skip:     <I d="M5 4v16l11-8L5 4Zm14 0v16" />,
  send:     <I d="M22 2 11 13M22 2l-7 20-4-9-9-4 20-7Z" />,
  shield:   <I d="M12 3 4 6v6c0 5 3.5 8 8 9 4.5-1 8-4 8-9V6l-8-3Z" />,
  upload:   <I d="M12 16V4m0 0-5 5m5-5 5 5M4 20h16" />,
  filter:   <I d="M3 4h18l-7 9v6l-4-2v-4L3 4Z" />,
  edit:     <I d="m4 20 4-1 11-11-3-3L5 16l-1 4ZM14 6l3 3" />,
  trash:    <I d="M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13M10 11v6M14 11v6" />,
  history:  <I d="M3 12a9 9 0 1 0 3-6.7L3 8M3 3v5h5M12 7v5l3 2" />,
  star:     <I d="m12 3 2.7 6.2 6.7.6-5 4.5 1.5 6.6L12 17.5 6.1 21l1.5-6.6-5-4.5 6.7-.6L12 3Z" />,
  mail:     <I d="M3 6h18v12H3zM3 6l9 7 9-7" />,
  lock:     <I d="M5 11h14v10H5zM8 11V7a4 4 0 0 1 8 0v4" />,
  eye:      <I d="M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12Zm10 3a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z" />,
  dots:     <I d="M5 12h.01M12 12h.01M19 12h.01" sw={3} />,
};

function Avatar({ name, tone = 1, size = 32 }) {
  const initials = (name || "?")
    .split(/\s+/).map(s => s[0]).slice(0, 2).join('').toUpperCase();
  return (
    <div className={`bt-avatar av-${tone}`} style={{ width: size, height: size, fontSize: size * 0.42 }}>
      {initials}
    </div>
  );
}

function TopBar({ active, role = "player" }) {
  const links = role === "admin"
    ? [['Catalogue', 'catalogue'], ['Imports', 'imports'], ['Playlists', 'playlists'], ['Users', 'users']]
    : [['Play', 'play'], ['Friends', 'friends'], ['History', 'history']];
  return (
    <div className="bt-topbar">
      <div style={{ display: 'flex', alignItems: 'center', gap: 28 }}>
        <div className="bt-logo">
          <div className="bt-logo-mark"></div>
          <span>Blindtest</span>
          {role === 'admin' && <span className="bt-chip bt-chip-warn" style={{ marginLeft: 6 }}>admin</span>}
        </div>
        <nav className="bt-nav">
          {links.map(([label, key]) => (
            <a key={key} href="#" className={active === key ? 'active' : ''}>{label}</a>
          ))}
        </nav>
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
        <button className="bt-btn bt-btn-ghost bt-btn-sm" style={{ position: 'relative' }}>
          {Icon.bell}
          <span style={{ position: 'absolute', top: 4, right: 4, width: 7, height: 7, borderRadius: '50%', background: 'var(--accent)' }}></span>
        </button>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <Avatar name="Léa Marin" tone={3} />
          <div style={{ fontSize: 13, fontWeight: 600 }}>Léa</div>
        </div>
      </div>
    </div>
  );
}

function CenterStage({ children, maxWidth = 440 }) {
  return (
    <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 40, overflow: 'auto' }}>
      <div style={{ width: '100%', maxWidth }}>{children}</div>
    </div>
  );
}

function AuthFrame({ children, title, subtitle, footer }) {
  return (
    <div className="bt-screen">
      <div style={{ height: 56, display: 'flex', alignItems: 'center', padding: '0 28px' }}>
        <div className="bt-logo"><div className="bt-logo-mark"></div><span>Blindtest</span></div>
      </div>
      <CenterStage>
        <div className="bt-card" style={{ padding: 36 }}>
          <h1 style={{ fontSize: 24, fontWeight: 700, letterSpacing: '-0.015em', margin: '0 0 6px' }}>{title}</h1>
          {subtitle && <p style={{ color: 'var(--fg-2)', fontSize: 14, margin: '0 0 24px' }}>{subtitle}</p>}
          {children}
        </div>
        {footer && <div style={{ textAlign: 'center', marginTop: 18, fontSize: 13.5, color: 'var(--fg-2)' }}>{footer}</div>}
      </CenterStage>
    </div>
  );
}

Object.assign(window, { Icon, I, Avatar, TopBar, CenterStage, AuthFrame });
