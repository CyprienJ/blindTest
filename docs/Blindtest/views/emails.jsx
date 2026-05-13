/* EMAIL DESIGNS — transactional templates rendered inside a faux mail-client frame */

/* Email styles are inline-friendly tokens; the dark aesthetic of the app
   inverts here to white-bodied transactional emails with a dark branded header. */

const EMAIL = {
  bg: '#f5f1ea',
  card: '#ffffff',
  ink: '#1a1814',
  ink2: '#4a4640',
  ink3: '#86807a',
  line: '#e9e3d8',
  brand: '#22201c',
  accent: '#c9f062',
  accentInk: '#1f2a08',
  danger: '#c84a3a',
  warn: '#d4a637',
  info: '#5b89d4',
  font: "'Manrope', -apple-system, system-ui, sans-serif",
  mono: "'JetBrains Mono', ui-monospace, monospace",
};

function MailClient({ from, fromAddr, subject, preview, when, children }) {
  return (
    <div style={{
      background: EMAIL.bg,
      width: '100%', height: '100%',
      fontFamily: EMAIL.font,
      color: EMAIL.ink,
      display: 'flex', flexDirection: 'column',
      overflow: 'hidden',
    }}>
      {/* Faux mail client chrome */}
      <div style={{
        padding: '14px 20px',
        background: '#fff',
        borderBottom: '1px solid ' + EMAIL.line,
        display: 'flex', alignItems: 'center', gap: 12,
        flexShrink: 0,
      }}>
        <div style={{ display: 'flex', gap: 6 }}>
          <div style={{ width: 10, height: 10, borderRadius: '50%', background: '#e9645d' }}></div>
          <div style={{ width: 10, height: 10, borderRadius: '50%', background: '#e9c14b' }}></div>
          <div style={{ width: 10, height: 10, borderRadius: '50%', background: '#5db86b' }}></div>
        </div>
        <div style={{ flex: 1, fontSize: 13, color: EMAIL.ink3, marginLeft: 8 }}>Inbox</div>
        <div style={{ fontSize: 12, color: EMAIL.ink3 }}>{when}</div>
      </div>

      {/* Email header */}
      <div style={{ padding: '18px 28px 14px', background: '#fff', borderBottom: '1px solid ' + EMAIL.line, flexShrink: 0 }}>
        <div style={{ fontSize: 18, fontWeight: 700, letterSpacing: '-0.01em', color: EMAIL.ink, marginBottom: 10 }}>
          {subject}
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <div style={{
            width: 36, height: 36, borderRadius: '50%',
            background: EMAIL.brand, color: EMAIL.accent,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            fontWeight: 700, fontSize: 13, fontFamily: EMAIL.mono,
          }}>BT</div>
          <div style={{ flex: 1 }}>
            <div style={{ fontSize: 13.5, fontWeight: 600 }}>{from} <span style={{ color: EMAIL.ink3, fontWeight: 400 }}>&lt;{fromAddr}&gt;</span></div>
            <div style={{ fontSize: 12, color: EMAIL.ink3 }}>to me</div>
          </div>
          <div style={{ display: 'flex', gap: 6 }}>
            {[1, 2, 3].map(i => <div key={i} style={{ width: 28, height: 28, borderRadius: 6, background: '#f2efe8' }}></div>)}
          </div>
        </div>
      </div>

      {/* Email body — scrollable */}
      <div style={{ flex: 1, overflow: 'auto', padding: '28px 28px 40px', display: 'flex', justifyContent: 'center' }}>
        <div style={{ width: '100%', maxWidth: 560 }}>
          {children}
        </div>
      </div>
    </div>
  );
}

/* Email-internal components */

function EmailCard({ children }) {
  return (
    <div style={{
      background: EMAIL.card,
      border: '1px solid ' + EMAIL.line,
      borderRadius: 14,
      overflow: 'hidden',
      boxShadow: '0 1px 0 rgba(0,0,0,0.02), 0 18px 40px -22px rgba(0,0,0,0.18)',
    }}>
      {children}
    </div>
  );
}

function EmailHeader({ tag, tagColor }) {
  return (
    <div style={{
      background: EMAIL.brand,
      color: '#fff',
      padding: '22px 28px',
      display: 'flex', alignItems: 'center', justifyContent: 'space-between',
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
        <div style={{
          width: 26, height: 26,
          background: EMAIL.accent,
          borderRadius: 7,
          position: 'relative',
        }}>
          <div style={{ position: 'absolute', inset: 7, border: '2px solid ' + EMAIL.accentInk, borderRadius: 3, borderTop: 'none', borderRight: 'none' }}></div>
        </div>
        <div style={{ fontWeight: 700, fontSize: 16, letterSpacing: '-0.005em' }}>Blindtest</div>
      </div>
      {tag && (
        <div style={{
          fontSize: 10.5, fontWeight: 700, letterSpacing: '0.08em',
          padding: '4px 8px', borderRadius: 4,
          background: tagColor || 'rgba(255,255,255,0.08)',
          color: tagColor === EMAIL.accent ? EMAIL.accentInk : '#fff',
          textTransform: 'uppercase',
        }}>{tag}</div>
      )}
    </div>
  );
}

function EmailBody({ children }) {
  return <div style={{ padding: '32px 28px 28px' }}>{children}</div>;
}

function EmailH1({ children }) {
  return <h1 style={{ fontSize: 24, fontWeight: 700, letterSpacing: '-0.015em', margin: '0 0 14px', lineHeight: 1.2 }}>{children}</h1>;
}
function EmailP({ children, muted }) {
  return <p style={{ fontSize: 14.5, lineHeight: 1.65, color: muted ? EMAIL.ink2 : EMAIL.ink, margin: '0 0 16px' }}>{children}</p>;
}
function EmailButton({ children, color = 'primary' }) {
  const styles = {
    primary: { bg: EMAIL.brand, fg: '#fff', border: EMAIL.brand },
    accent:  { bg: EMAIL.accent, fg: EMAIL.accentInk, border: EMAIL.accent },
    ghost:   { bg: '#fff', fg: EMAIL.ink, border: EMAIL.line },
  }[color];
  return (
    <a href="#" style={{
      display: 'inline-block',
      background: styles.bg, color: styles.fg,
      border: `1.5px solid ${styles.border}`,
      padding: '13px 22px',
      borderRadius: 8,
      fontSize: 14, fontWeight: 600, textDecoration: 'none',
    }}>{children}</a>
  );
}

function EmailFootnote({ children }) {
  return <div style={{ fontSize: 12, color: EMAIL.ink3, lineHeight: 1.6, marginTop: 18, paddingTop: 18, borderTop: '1px dashed ' + EMAIL.line }}>{children}</div>;
}

function EmailFooter() {
  return (
    <div style={{ marginTop: 18, textAlign: 'center', fontSize: 11.5, color: EMAIL.ink3, lineHeight: 1.7 }}>
      <div style={{ display: 'flex', gap: 14, justifyContent: 'center', marginBottom: 10 }}>
        <a href="#" style={{ color: EMAIL.ink2, textDecoration: 'none' }}>Settings</a>
        <a href="#" style={{ color: EMAIL.ink2, textDecoration: 'none' }}>Unsubscribe</a>
        <a href="#" style={{ color: EMAIL.ink2, textDecoration: 'none' }}>Help</a>
      </div>
      <div>Blindtest · A real-time music game · You're getting this because you have an account.</div>
      <div style={{ marginTop: 4 }}>© 2026 Blindtest</div>
    </div>
  );
}

/* 1. Email verification */
function EmailVerify() {
  return (
    <MailClient
      from="Blindtest"
      fromAddr="hello@blindtest.app"
      subject="Verify your email to start playing"
      when="just now"
    >
      <EmailCard>
        <EmailHeader tag="verify" tagColor={EMAIL.accent} />
        <EmailBody>
          <EmailH1>Welcome, Léa — one quick step</EmailH1>
          <EmailP>Confirm your email so you can host sessions and invite friends. The link below works for 24 hours.</EmailP>

          <div style={{ margin: '24px 0 12px' }}>
            <EmailButton color="accent">Verify my email →</EmailButton>
          </div>

          <EmailP muted>Or copy and paste this link into your browser:</EmailP>
          <div style={{
            padding: '12px 14px', background: '#faf7f1',
            border: '1px solid ' + EMAIL.line,
            borderRadius: 8,
            fontFamily: EMAIL.mono,
            fontSize: 11.5,
            color: EMAIL.ink2,
            wordBreak: 'break-all',
          }}>https://blindtest.app/verify?token=eyJhbGciOiJIUzI1NiJ9.fH3pE...kJ7</div>

          <EmailFootnote>
            Didn't sign up? You can ignore this email — no account will be created.
          </EmailFootnote>
        </EmailBody>
      </EmailCard>
      <EmailFooter />
    </MailClient>
  );
}

/* 2. Password reset */
function EmailPasswordReset() {
  return (
    <MailClient
      from="Blindtest Security"
      fromAddr="security@blindtest.app"
      subject="Reset your Blindtest password"
      when="1m ago"
    >
      <EmailCard>
        <EmailHeader tag="security" tagColor="rgba(255,255,255,0.12)" />
        <EmailBody>
          <EmailH1>Reset your password</EmailH1>
          <EmailP>We received a request to reset the password for <strong>lea@example.com</strong>. Click the button below to choose a new one — the link expires in 60 minutes.</EmailP>

          <div style={{ margin: '24px 0 16px' }}>
            <EmailButton color="primary">Choose new password</EmailButton>
          </div>

          <div style={{
            display: 'flex', gap: 12, padding: 14,
            background: '#fff7e6',
            border: '1px solid #f4dba0',
            borderRadius: 8,
            marginTop: 16,
          }}>
            <div style={{ width: 22, height: 22, borderRadius: '50%', background: EMAIL.warn, color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 700, fontSize: 13, flexShrink: 0 }}>!</div>
            <div style={{ fontSize: 13, color: EMAIL.ink, lineHeight: 1.55 }}>
              <strong style={{ display: 'block', marginBottom: 2 }}>Didn't request this?</strong>
              Your password is still safe — just ignore this email. If you keep getting these, <a href="#" style={{ color: EMAIL.ink }}>contact support</a>.
            </div>
          </div>

          <EmailFootnote>
            Request from <span style={{ fontFamily: EMAIL.mono }}>2a01:e0a:•••</span> · Firefox on macOS · 13 May 2026, 19:42 CEST
          </EmailFootnote>
        </EmailBody>
      </EmailCard>
      <EmailFooter />
    </MailClient>
  );
}

/* 3. Friend request */
function EmailFriendRequest() {
  return (
    <MailClient
      from="Blindtest"
      fromAddr="notifications@blindtest.app"
      subject="Maxime wants to be your friend on Blindtest"
      when="5m ago"
    >
      <EmailCard>
        <EmailHeader tag="friend request" />
        <EmailBody>
          <div style={{
            display: 'flex', alignItems: 'center', gap: 18,
            padding: 18,
            background: '#faf7f1',
            border: '1px solid ' + EMAIL.line,
            borderRadius: 12,
            marginBottom: 22,
          }}>
            <div style={{
              width: 60, height: 60, borderRadius: '50%',
              background: 'linear-gradient(135deg, oklch(0.78 0.16 130), oklch(0.62 0.14 160))',
              color: '#1a1814',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              fontSize: 22, fontWeight: 700,
            }}>MD</div>
            <div>
              <div style={{ fontSize: 16, fontWeight: 700 }}>Maxime D.</div>
              <div style={{ fontSize: 13, color: EMAIL.ink3, fontFamily: EMAIL.mono }}>@max · 28 games · joined Feb 2026</div>
              <div style={{ marginTop: 6, fontSize: 12, color: EMAIL.ink2 }}>3 mutual friends · Anaïs, Yacine, Hugo</div>
            </div>
          </div>

          <EmailH1>You've got a new friend request</EmailH1>
          <EmailP>Maxime sent you a friend request on Blindtest. Once you accept, they can invite you straight into their lobbies — no codes to share.</EmailP>

          <div style={{ display: 'flex', gap: 10, margin: '24px 0 8px' }}>
            <EmailButton color="accent">Accept</EmailButton>
            <EmailButton color="ghost">Decline</EmailButton>
          </div>

          <EmailFootnote>
            Don't want emails about friend requests? <a href="#" style={{ color: EMAIL.ink2 }}>Update preferences</a>.
          </EmailFootnote>
        </EmailBody>
      </EmailCard>
      <EmailFooter />
    </MailClient>
  );
}

/* 4. Session invite */
function EmailSessionInvite() {
  return (
    <MailClient
      from="Blindtest"
      fromAddr="notifications@blindtest.app"
      subject="Léa is starting a blindtest — join now"
      when="just now"
    >
      <EmailCard>
        <EmailHeader tag="LIVE NOW" tagColor={EMAIL.accent} />

        {/* Hero block */}
        <div style={{
          background: 'linear-gradient(135deg, #2a2620 0%, #1a1814 100%)',
          color: '#fff',
          padding: '28px',
          textAlign: 'center',
        }}>
          <div style={{ fontSize: 11.5, color: EMAIL.accent, fontWeight: 700, letterSpacing: '0.1em', textTransform: 'uppercase', marginBottom: 10 }}>
            ◌ Session open
          </div>
          <div style={{ fontFamily: EMAIL.mono, fontSize: 44, fontWeight: 700, letterSpacing: '0.1em', marginBottom: 6 }}>XK9F2A</div>
          <div style={{ fontSize: 13.5, color: 'rgba(255,255,255,0.7)' }}>4 players in the lobby · 80s Classics · 15 tracks</div>
        </div>

        <EmailBody>
          <EmailH1>Léa is hosting a blindtest</EmailH1>
          <EmailP>She invited you directly — you'll skip the code and drop straight into her lobby when you click below.</EmailP>

          <div style={{ display: 'flex', gap: 10, alignItems: 'center', padding: 14, background: '#faf7f1', border: '1px solid ' + EMAIL.line, borderRadius: 10, marginBottom: 16 }}>
            <div style={{
              width: 40, height: 40, borderRadius: '50%',
              background: 'linear-gradient(135deg, oklch(0.78 0.16 280), oklch(0.62 0.14 320))',
              color: '#1a1814',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              fontWeight: 700, fontSize: 14,
            }}>LM</div>
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: 13.5, fontWeight: 600 }}>Léa Marin <span style={{ color: EMAIL.ink3, fontWeight: 400 }}>· host</span></div>
              <div style={{ fontSize: 11.5, color: EMAIL.ink3 }}>"Pour fêter ton retour 🎉"</div>
            </div>
          </div>

          <div style={{ margin: '20px 0 8px' }}>
            <EmailButton color="accent">Join lobby →</EmailButton>
          </div>

          <EmailFootnote>
            This link is only useful while the session is open. Sessions usually finish within 15-20 minutes.
          </EmailFootnote>
        </EmailBody>
      </EmailCard>
      <EmailFooter />
    </MailClient>
  );
}

/* 5. Friend accepted */
function EmailFriendAccepted() {
  return (
    <MailClient
      from="Blindtest"
      fromAddr="notifications@blindtest.app"
      subject="Bruno accepted your friend request"
      when="2h ago"
    >
      <EmailCard>
        <EmailHeader tag="friends" />
        <EmailBody>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 16, margin: '8px 0 22px' }}>
            <div style={{
              width: 56, height: 56, borderRadius: '50%',
              background: 'linear-gradient(135deg, oklch(0.78 0.16 280), oklch(0.62 0.14 320))',
              color: '#1a1814',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              fontWeight: 700, fontSize: 18,
            }}>LM</div>
            <div style={{ fontSize: 28, color: EMAIL.accent, fontWeight: 700 }}>+</div>
            <div style={{
              width: 56, height: 56, borderRadius: '50%',
              background: 'linear-gradient(135deg, oklch(0.78 0.16 30), oklch(0.62 0.14 60))',
              color: '#1a1814',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              fontWeight: 700, fontSize: 18,
            }}>BA</div>
          </div>

          <EmailH1>You're now friends with Bruno</EmailH1>
          <EmailP>Bruno A. accepted your friend request. You can now invite each other to private lobbies directly from your friend list.</EmailP>

          <div style={{ margin: '20px 0 8px', display: 'flex', gap: 10 }}>
            <EmailButton color="accent">Start a session →</EmailButton>
            <EmailButton color="ghost">View profile</EmailButton>
          </div>
        </EmailBody>
      </EmailCard>
      <EmailFooter />
    </MailClient>
  );
}

/* 6. Welcome (after verify) */
function EmailWelcome() {
  return (
    <MailClient
      from="Cyprien from Blindtest"
      fromAddr="cyprien@blindtest.app"
      subject="You're in — let's start a blindtest"
      when="3m ago"
    >
      <EmailCard>
        <EmailHeader />
        <EmailBody>
          <EmailH1>Welcome to Blindtest 🎶</EmailH1>
          <EmailP>Your email is verified. You can now host sessions, add friends, and build a game history. Here's how players usually get rolling:</EmailP>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 12, margin: '20px 0' }}>
            {[
              ['01', 'Create your first session', 'Pick a playlist or a decade and share the 6-character code.'],
              ['02', 'Add a couple of friends', 'They can join your lobbies straight from a notification.'],
              ['03', 'Race the clock', 'Type the title or artist faster than everyone else.'],
            ].map(([n, t, d]) => (
              <div key={n} style={{ display: 'flex', gap: 14, padding: 14, background: '#faf7f1', border: '1px solid ' + EMAIL.line, borderRadius: 10 }}>
                <div style={{ fontFamily: EMAIL.mono, fontSize: 13, color: EMAIL.ink3, fontWeight: 700 }}>{n}</div>
                <div>
                  <div style={{ fontSize: 14, fontWeight: 700, marginBottom: 2 }}>{t}</div>
                  <div style={{ fontSize: 13, color: EMAIL.ink2, lineHeight: 1.55 }}>{d}</div>
                </div>
              </div>
            ))}
          </div>

          <div style={{ margin: '12px 0 8px' }}>
            <EmailButton color="accent">Host a session →</EmailButton>
          </div>

          <EmailFootnote>
            Reply to this email if you have any feedback — it goes directly to me. Have fun! — Cyprien
          </EmailFootnote>
        </EmailBody>
      </EmailCard>
      <EmailFooter />
    </MailClient>
  );
}

Object.assign(window, {
  EmailVerify, EmailPasswordReset, EmailFriendRequest,
  EmailSessionInvite, EmailFriendAccepted, EmailWelcome,
});
