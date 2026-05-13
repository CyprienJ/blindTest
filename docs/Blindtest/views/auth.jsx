/* AUTH VIEWS — Login, Register (Turnstile), Email Verify, Forgot Password */

function LoginView() {
  const [email, setEmail] = React.useState('lea@example.com');
  const [pwd, setPwd] = React.useState('••••••••');
  const [show, setShow] = React.useState(false);
  return (
    <AuthFrame
      title="Welcome back"
      subtitle="Sign in to host a session or join your friends."
      footer={<>New here? <a href="#" style={{ color: 'var(--accent)' }}>Create an account</a></>}
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        <div className="bt-field">
          <label className="bt-label">Email</label>
          <input className="bt-input" value={email} onChange={e => setEmail(e.target.value)} placeholder="you@example.com" />
        </div>
        <div className="bt-field">
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <label className="bt-label">Password</label>
            <a href="#" style={{ fontSize: 12, color: 'var(--fg-2)' }}>Forgot?</a>
          </div>
          <div style={{ position: 'relative' }}>
            <input type={show ? 'text' : 'password'} className="bt-input" value={pwd} onChange={e => setPwd(e.target.value)} />
            <button onClick={() => setShow(s => !s)} style={{ position: 'absolute', right: 8, top: 6, background: 'transparent', border: 'none', color: 'var(--fg-2)', cursor: 'pointer', padding: 6 }}>
              {Icon.eye}
            </button>
          </div>
        </div>
        <button className="bt-btn bt-btn-primary bt-btn-lg" style={{ marginTop: 6 }}>Sign in →</button>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, color: 'var(--fg-3)', fontSize: 12, margin: '8px 0' }}>
          <div style={{ flex: 1, height: 1, background: 'var(--line-soft)' }}></div>
          <span>OR</span>
          <div style={{ flex: 1, height: 1, background: 'var(--line-soft)' }}></div>
        </div>
        <button className="bt-btn bt-btn-ghost">
          {Icon.users}
          Continue as guest
        </button>
      </div>
    </AuthFrame>
  );
}

function RegisterView() {
  const [verified, setVerified] = React.useState(false);
  React.useEffect(() => {
    const t = setTimeout(() => setVerified(true), 1800);
    return () => clearTimeout(t);
  }, []);
  return (
    <AuthFrame
      title="Create your account"
      subtitle="A verified email is required before you can host sessions."
      footer={<>Already have one? <a href="#" style={{ color: 'var(--accent)' }}>Sign in</a></>}
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
        <div className="bt-field">
          <label className="bt-label">Username</label>
          <input className="bt-input" defaultValue="leamusic" />
          <span style={{ fontSize: 11.5, color: 'var(--fg-3)' }}>Visible to other players · 3-20 chars</span>
        </div>
        <div className="bt-field">
          <label className="bt-label">Email</label>
          <input className="bt-input" defaultValue="lea@example.com" />
        </div>
        <div className="bt-field">
          <label className="bt-label">Password</label>
          <input type="password" className="bt-input" defaultValue="ssssssss" />
          <div style={{ display: 'flex', gap: 4, marginTop: 4 }}>
            {['var(--accent)','var(--accent)','var(--warn)','var(--bg-3)'].map((c, i) => (
              <div key={i} style={{ flex: 1, height: 3, borderRadius: 2, background: c }}></div>
            ))}
          </div>
          <span style={{ fontSize: 11.5, color: 'var(--fg-2)' }}>Add a number for stronger password</span>
        </div>

        {/* Cloudflare Turnstile widget mock */}
        <div style={{
          marginTop: 4,
          padding: 14,
          border: '1px solid var(--line)',
          borderRadius: 'var(--r-md)',
          background: 'var(--bg-1)',
          display: 'flex',
          alignItems: 'center',
          gap: 12,
        }}>
          {verified ? (
            <div style={{ width: 22, height: 22, borderRadius: 4, background: 'var(--accent)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--accent-ink)' }}>
              {Icon.check}
            </div>
          ) : (
            <div style={{ width: 22, height: 22, border: '2px solid var(--line)', borderTopColor: 'var(--accent)', borderRadius: '50%', animation: 'wave 1s linear infinite' }}></div>
          )}
          <div style={{ flex: 1 }}>
            <div style={{ fontSize: 13, fontWeight: 600 }}>{verified ? 'Success!' : 'Verifying...'}</div>
            <div style={{ fontSize: 11.5, color: 'var(--fg-3)' }}>I'm not a robot · Cloudflare Turnstile</div>
          </div>
          <div className="mono" style={{ fontSize: 10, color: 'var(--fg-3)' }}>CF</div>
        </div>

        <button className="bt-btn bt-btn-primary bt-btn-lg" disabled={!verified}>Create account</button>
        <p style={{ fontSize: 11.5, color: 'var(--fg-3)', textAlign: 'center', margin: 0 }}>
          By signing up you agree to our terms. We'll send a verification email.
        </p>
      </div>
    </AuthFrame>
  );
}

function VerifyEmailView() {
  return (
    <AuthFrame
      title="Check your inbox"
      subtitle={<>We sent a verification link to <span style={{ color: 'var(--fg-0)', fontWeight: 600 }}>lea@example.com</span>. The link expires in 24 hours.</>}
      footer={<>Wrong address? <a href="#" style={{ color: 'var(--accent)' }}>Change email</a></>}
    >
      <div style={{
        margin: '8px 0 24px',
        padding: 20,
        background: 'var(--bg-2)',
        border: '1px dashed var(--line)',
        borderRadius: 'var(--r-md)',
        display: 'flex', gap: 16, alignItems: 'flex-start'
      }}>
        <div style={{ width: 36, height: 36, borderRadius: 8, background: 'var(--bg-3)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--accent)' }}>
          {Icon.mail}
        </div>
        <div>
          <div style={{ fontSize: 13.5, fontWeight: 600, marginBottom: 4 }}>What happens next?</div>
          <ol style={{ margin: 0, paddingLeft: 18, color: 'var(--fg-1)', fontSize: 13, lineHeight: 1.7 }}>
            <li>Open the email from Blindtest</li>
            <li>Click "Verify my email"</li>
            <li>You'll be redirected to host your first session</li>
          </ol>
        </div>
      </div>
      <button className="bt-btn bt-btn-ghost" style={{ width: '100%' }}>Resend email <span style={{ color: 'var(--fg-3)', fontSize: 12, marginLeft: 6 }}>(in 28s)</span></button>
    </AuthFrame>
  );
}

function ForgotPasswordView() {
  return (
    <AuthFrame
      title="Reset your password"
      subtitle="Enter your email and we'll send a reset link."
      footer={<><a href="#" style={{ color: 'var(--accent)' }}>← Back to sign in</a></>}
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        <div className="bt-field">
          <label className="bt-label">Email</label>
          <input className="bt-input" defaultValue="lea@example.com" />
        </div>
        <button className="bt-btn bt-btn-primary bt-btn-lg">Send reset link</button>
      </div>
    </AuthFrame>
  );
}

Object.assign(window, { LoginView, RegisterView, VerifyEmailView, ForgotPasswordView });
