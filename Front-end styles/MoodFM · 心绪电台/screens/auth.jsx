/* global React, ScreenFrame, MoodBlob, Icon, Logo */
// P2 · Signup  |  P3 · Login
// Shared editorial split layout: left = mood blob art + manifesto · right = form

function AuthFrame({ device, theme, mood, kind = 'login', onSubmit, onSwitch }) {
  const isMobile = device === 'mobile';
  const W = isMobile ? 390 : 1440;
  const H = isMobile ? 844 : 900;
  const isSignup = kind === 'signup';

  return (
    <ScreenFrame theme={theme} mood={mood} width={W} height={H} label={isSignup ? '02a Signup' : '02b Login'} scroll={isMobile}>
      <div style={{ display: 'grid', gridTemplateColumns: isMobile ? '1fr' : '1fr 1fr', minHeight: H }}>
        {/* LEFT — editorial art (cream paper + gradient blob, matches design language) */}
        {!isMobile && (
          <div style={{ position:'relative', overflow:'hidden', background:'var(--bg)', color:'var(--ink)', borderRight:'1px solid var(--rule)' }}>
            {/* Soft gradient blob, not a full dark wash */}
            <div className="mood-blob drift" style={{ width: 720, height: 720, left: -120, top: -160, opacity: 0.55 }}/>
            <div className="mood-blob drift" style={{ width: 480, height: 480, right: -100, bottom: -120, opacity: 0.4 }}/>

            <div style={{ position:'absolute', inset: 0, padding: 56, display:'flex', flexDirection:'column', justifyContent:'space-between', zIndex: 2 }}>
              <div className="row" style={{ gap: 10 }}>
                <Logo size={28}/>
                <div style={{ fontFamily:'var(--serif-en)', fontSize: 22, fontStyle:'italic' }}>MoodFM</div>
              </div>

              <div>
                <div className="meta">
                  ISSUE №01 · {isSignup ? 'NEW LISTENERS' : 'WELCOME BACK'}
                </div>
                <h1 className="display" style={{ fontSize: 96, margin:'14px 0 0', lineHeight: 0.95 }}>
                  {isSignup ? <>Tune <em>in</em>.</> : <>Welcome <em>home</em>.</>}
                </h1>
                <div className="display-cn" style={{ fontSize: 32, marginTop: 16, color:'var(--ink-2)' }}>
                  {isSignup ? '今晚开始，你也有一座电台。' : '欢迎回来。电台已替你温着。'}
                </div>
                <p style={{ marginTop: 24, fontSize: 15, lineHeight: 1.7, color:'var(--ink-2)', maxWidth: 380, fontFamily:'var(--serif-cn)' }}>
                  {isSignup
                    ? '我们读取你的情绪，跨越平台编排音乐——你只需要说一句话，或者什么都别说。'
                    : '上一次你听到的是 Marconi Union 的《weightless》，进度 1:24。准备好继续了吗？'}
                </p>
              </div>

              <div className="between" style={{ fontFamily:'var(--mono)', fontSize: 11, letterSpacing:'.18em', textTransform:'uppercase', color:'var(--ink-3)' }}>
                <span>SPRING / 2026</span>
                <span>A PRIVATE STATION</span>
              </div>
            </div>
          </div>
        )}

        {/* RIGHT — form */}
        <div style={{ padding: isMobile ? '32px 22px 60px' : '64px 80px', position:'relative', display:'flex', flexDirection:'column', justifyContent: isMobile?'flex-start':'center' }}>
          {isMobile && (
            <div className="row" style={{ gap: 10, marginBottom: 36 }}>
              <Logo size={22}/>
              <div style={{ fontFamily:'var(--serif-en)', fontSize: 18, fontStyle:'italic' }}>MoodFM</div>
            </div>
          )}

          <div className="meta">{isSignup ? 'NEW ACCOUNT · 创建账号' : 'SIGN IN · 登录'}</div>
          <h2 className="display" style={{ fontSize: isMobile?52:72, margin:'10px 0 0' }}>
            {isSignup ? <>Begin.</> : <>Hi, again.</>}
          </h2>
          <div className="display-cn" style={{ fontSize: isMobile?22:28, marginTop: 6, color:'var(--ink-2)' }}>
            {isSignup ? '注册 · 用一句话描述你' : '登录 · 继续昨晚的电台'}
          </div>

          <div style={{ marginTop: isMobile?28:40 }}>
            {isSignup ? <SignupForm/> : <LoginForm/>}
          </div>

          <div className="between" style={{ marginTop: 28, fontFamily:'var(--serif-cn)', fontSize: 14, color:'var(--ink-2)' }}>
            <span>
              {isSignup ? '已有账号？' : '还没有账号？'}{' '}
              <a onClick={onSwitch} style={{ color:'var(--ink)', textDecoration:'underline', textUnderlineOffset:3, cursor:'pointer' }}>
                {isSignup ? '登录' : '注册'} →
              </a>
            </span>
            {!isSignup && <a style={{ color:'var(--ink-3)', cursor:'pointer' }}>忘记密码？</a>}
          </div>

          {/* Footer meta */}
          <div className="meta" style={{ marginTop: 'auto', paddingTop: 40, color:'var(--ink-3)' }}>
            BY CONTINUING YOU AGREE TO OUR <span style={{ color:'var(--ink-2)', textDecoration:'underline' }}>TERMS</span> · <span style={{ color:'var(--ink-2)', textDecoration:'underline' }}>PRIVACY</span>
          </div>
        </div>
      </div>
    </ScreenFrame>
  );
}

function Field({ label, hint, type='text', value, onChange, placeholder, suffix, error }) {
  return (
    <div style={{ marginBottom: 16 }}>
      <div className="between" style={{ marginBottom: 8 }}>
        <label className="meta" style={{ color: error ? 'var(--mood-b)' : 'var(--ink-2)' }}>{label}</label>
        {hint && <span className="meta" style={{ color:'var(--ink-3)' }}>{hint}</span>}
      </div>
      <div style={{ position:'relative' }}>
        <input
          type={type}
          value={value}
          onChange={onChange}
          placeholder={placeholder}
          className="field"
          style={{
            paddingRight: suffix ? 130 : 16,
            borderColor: error ? 'var(--mood-b)' : 'var(--rule)',
          }}
        />
        {suffix && <div style={{ position:'absolute', right:6, top:6, bottom:6 }}>{suffix}</div>}
      </div>
      {error && <div className="meta" style={{ marginTop: 6, color:'var(--mood-b)' }}>· {error}</div>}
    </div>
  );
}

function SignupForm() {
  const [n, setN] = React.useState('江野');
  const [e, setE] = React.useState('jiangye@gmail.com');
  const [p, setP] = React.useState('');
  const [c, setC] = React.useState('');
  const [code, setCode] = React.useState('');
  const [count, setCount] = React.useState(0);
  const [agree, setAgree] = React.useState(true);

  const sendCode = () => { if (count===0) { setCount(60); const t=setInterval(()=>setCount(s=>{ if (s<=1){ clearInterval(t); return 0;} return s-1; }), 1000); } };

  return (
    <div>
      <Field label="昵称 · NAME" value={n} onChange={e=>setN(e.target.value)} placeholder="你叫什么"/>
      <Field label="邮箱 · EMAIL" value={e} onChange={ev=>setE(ev.target.value)} placeholder="you@somewhere.com"/>
      <Field label="密码 · PASSWORD" hint="≥ 8 位 · 字母+数字" type="password" value={p} onChange={ev=>setP(ev.target.value)} placeholder="至少 8 位"/>
      <Field label="确认密码 · CONFIRM" type="password" value={c} onChange={ev=>setC(ev.target.value)} placeholder="再输一次"/>
      <Field
        label="验证码 · ONE-TIME CODE"
        value={code}
        onChange={ev=>setCode(ev.target.value)}
        placeholder="6 位"
        suffix={
          <button onClick={sendCode} style={{
            height: 36, padding:'0 14px', borderRadius: 999,
            background: count>0 ? 'transparent' : 'var(--ink)', color: count>0 ? 'var(--ink-3)' : 'var(--bg)',
            border: count>0 ? '1px solid var(--rule)' : 'none',
            fontFamily:'var(--mono)', fontSize: 11, letterSpacing:'.14em', textTransform:'uppercase',
            cursor: count>0?'not-allowed':'pointer',
          }}>{count>0 ? `${count}s` : '获取验证码'}</button>
        }
      />
      <label className="row" style={{ gap: 10, fontFamily:'var(--serif-cn)', fontSize: 13, color:'var(--ink-2)', marginTop: 4, marginBottom: 16, cursor:'pointer' }} onClick={()=>setAgree(a=>!a)}>
        <span style={{ width:16, height:16, borderRadius: 4, border:'1px solid var(--ink)', background: agree?'var(--ink)':'transparent', display:'inline-flex', alignItems:'center', justifyContent:'center', flexShrink:0 }}>
          {agree && <span style={{ width:8, height:8, background:'var(--bg)', borderRadius: 2 }}/>}
        </span>
        <span>我已阅读并同意 <span style={{ color:'var(--ink)', textDecoration:'underline' }}>服务条款</span> 与 <span style={{ color:'var(--ink)', textDecoration:'underline' }}>隐私政策</span></span>
      </label>
      <button className="btn" style={{ width:'100%', height: 50 }}>注册并启动 · CREATE ACCOUNT <Icon.arrow/></button>
    </div>
  );
}

function LoginForm() {
  const [id, setId] = React.useState('jiangye@gmail.com');
  const [p, setP] = React.useState('');
  const [remember, setRemember] = React.useState(true);

  return (
    <div>
      <Field label="邮箱 / 手机 · EMAIL OR PHONE" value={id} onChange={ev=>setId(ev.target.value)}/>
      <Field label="密码 · PASSWORD" type="password" value={p} onChange={ev=>setP(ev.target.value)} placeholder="·······"/>
      <label className="row" style={{ gap: 10, fontFamily:'var(--serif-cn)', fontSize: 13, color:'var(--ink-2)', marginBottom: 18, cursor:'pointer' }} onClick={()=>setRemember(r=>!r)}>
        <span style={{ width:16, height:16, borderRadius: 4, border:'1px solid var(--ink)', background: remember?'var(--ink)':'transparent', display:'inline-flex', alignItems:'center', justifyContent:'center', flexShrink:0 }}>
          {remember && <span style={{ width:8, height:8, background:'var(--bg)', borderRadius: 2 }}/>}
        </span>
        <span>30 天内记住我 · Remember me</span>
      </label>
      <button className="btn" style={{ width:'100%', height: 50 }}>登录 · SIGN IN <Icon.arrow/></button>

      <div className="row" style={{ marginTop: 24, gap: 12, alignItems:'center' }}>
        <hr className="rule" style={{ flex:1, borderTopColor:'var(--rule)' }}/>
        <span className="meta">OR</span>
        <hr className="rule" style={{ flex:1, borderTopColor:'var(--rule)' }}/>
      </div>
      <div className="row" style={{ gap: 10, marginTop: 20 }}>
        <button className="btn-pill" style={{ flex:1, height: 44 }}>网易云账号</button>
        <button className="btn-pill" style={{ flex:1, height: 44 }}>QQ 音乐账号</button>
      </div>
    </div>
  );
}

window.AuthFrame = AuthFrame;
