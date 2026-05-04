import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Icon, Logo } from '../components/atoms';
import { authApi } from '../api/auth';
import { useAuthStore } from '../store/authStore';

function Field({ label, hint, type='text', value, onChange, placeholder, suffix, error }) {
  return (
    <div style={{ marginBottom:16 }}>
      <div className="between" style={{ marginBottom:8 }}>
        <label className="meta" style={{ color:error?'var(--mood-b)':'var(--ink-2)' }}>{label}</label>
        {hint && <span className="meta" style={{ color:'var(--ink-3)' }}>{hint}</span>}
      </div>
      <div style={{ position:'relative' }}>
        <input type={type} value={value} onChange={onChange} placeholder={placeholder} className="field"
          style={{ paddingRight:suffix?130:16, borderColor:error?'var(--mood-b)':'var(--rule)' }}/>
        {suffix && <div style={{ position:'absolute', right:6, top:6, bottom:6 }}>{suffix}</div>}
      </div>
      {error && <div className="meta" style={{ marginTop:6, color:'var(--mood-b)' }}>· {error}</div>}
    </div>
  );
}

function LoginForm({ onSuccess }) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const login = useAuthStore(s => s.login);
  const navigate = useNavigate();

  const submit = async () => {
    if (!email || !password) { setError('请填写邮箱和密码'); return; }
    setLoading(true); setError('');
    try {
      const res = await authApi.login({ account: email, password });
      if (res.code === 200) {
        login(res.data.user, res.data.accessToken);
        navigate('/home');
      } else {
        setError(res.message || '登录失败');
      }
    } catch (e) {
      setError(e?.message || '登录失败，请检查网络');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      {error && <div style={{ padding:'12px 16px', background:'rgba(232,90,138,0.08)', border:'1px solid var(--mood-b)', borderRadius:12, marginBottom:16, fontFamily:'var(--serif-cn)', fontSize:14, color:'var(--mood-b)' }}>{error}</div>}
      <Field label="邮箱 · EMAIL" value={email} onChange={e=>setEmail(e.target.value)} placeholder="you@somewhere.com"/>
      <Field label="密码 · PASSWORD" hint="≥ 8 位" type="password" value={password} onChange={e=>setPassword(e.target.value)} placeholder="·······"/>
      <button className="btn" onClick={submit} disabled={loading} style={{ width:'100%', height:50, opacity:loading?0.7:1 }}>
        {loading ? '登录中…' : <>登录 · SIGN IN <Icon.arrow/></>}
      </button>
    </div>
  );
}

function SignupForm() {
  const [nickname, setNickname] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [agree, setAgree] = useState(true);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const login = useAuthStore(s => s.login);
  const navigate = useNavigate();

  const submit = async () => {
    if (!nickname || !email || !password) { setError('请填写所有必填项'); return; }
    if (password !== confirm) { setError('两次密码不一致'); return; }
    if (password.length < 8) { setError('密码至少 8 位'); return; }
    if (!agree) { setError('请同意服务条款'); return; }
    setLoading(true); setError('');
    try {
      const res = await authApi.register({ username: nickname, email, password });
      if (res.code === 200) {
        login(res.data.user, res.data.accessToken);
        navigate('/onboarding');
      } else {
        setError(res.message || '注册失败');
      }
    } catch (e) {
      setError(e?.message || '注册失败，请检查网络');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      {error && <div style={{ padding:'12px 16px', background:'rgba(232,90,138,0.08)', border:'1px solid var(--mood-b)', borderRadius:12, marginBottom:16, fontFamily:'var(--serif-cn)', fontSize:14, color:'var(--mood-b)' }}>{error}</div>}
      <Field label="昵称 · NAME" value={nickname} onChange={e=>setNickname(e.target.value)} placeholder="你叫什么"/>
      <Field label="邮箱 · EMAIL" value={email} onChange={e=>setEmail(e.target.value)} placeholder="you@somewhere.com"/>
      <Field label="密码 · PASSWORD" hint="≥ 8 位 · 字母+数字" type="password" value={password} onChange={e=>setPassword(e.target.value)} placeholder="至少 8 位"/>
      <Field label="确认密码 · CONFIRM" type="password" value={confirm} onChange={e=>setConfirm(e.target.value)} placeholder="再输一次"/>
      <label className="row" style={{ gap:10, fontFamily:'var(--serif-cn)', fontSize:13, color:'var(--ink-2)', marginTop:4, marginBottom:16, cursor:'pointer' }} onClick={() => setAgree(a=>!a)}>
        <span style={{ width:16, height:16, borderRadius:4, border:'1px solid var(--ink)', background:agree?'var(--ink)':'transparent', display:'inline-flex', alignItems:'center', justifyContent:'center', flexShrink:0 }}>
          {agree && <span style={{ width:8, height:8, background:'var(--bg)', borderRadius:2 }}/>}
        </span>
        <span>我已阅读并同意 <span style={{ color:'var(--ink)', textDecoration:'underline' }}>服务条款</span> 与 <span style={{ color:'var(--ink)', textDecoration:'underline' }}>隐私政策</span></span>
      </label>
      <button className="btn" onClick={submit} disabled={loading} style={{ width:'100%', height:50, opacity:loading?0.7:1 }}>
        {loading ? '注册中…' : <>注册并启动 · CREATE ACCOUNT <Icon.arrow/></>}
      </button>
    </div>
  );
}

export default function Auth() {
  const [params] = useSearchParams();
  const [mode, setMode] = useState(params.get('mode') === 'signup' ? 'signup' : 'login');
  const isSignup = mode === 'signup';
  const navigate = useNavigate();

  return (
    <div data-mood="dusk" className="mfm" style={{ minHeight:'100vh' }}>
      <div style={{ display:'grid', gridTemplateColumns:'1fr 1fr', minHeight:'100vh' }}>
        {/* LEFT */}
        <div style={{ position:'relative', overflow:'hidden', background:'var(--bg)', color:'var(--ink)', borderRight:'1px solid var(--rule)' }}>
          <div className="mood-blob drift" style={{ width:720, height:720, left:-120, top:-160, opacity:0.55 }}/>
          <div className="mood-blob drift" style={{ width:480, height:480, right:-100, bottom:-120, opacity:0.4 }}/>
          <div style={{ position:'absolute', inset:0, padding:56, display:'flex', flexDirection:'column', justifyContent:'space-between', zIndex:2 }}>
            <div className="row" style={{ gap:10 }}>
              <Logo size={28}/>
              <div style={{ fontFamily:'var(--serif-en)', fontSize:22, fontStyle:'italic' }}>MoodFM</div>
            </div>
            <div>
              <div className="meta">ISSUE №01 · {isSignup ? 'NEW LISTENERS' : 'WELCOME BACK'}</div>
              <h1 className="display" style={{ fontSize:96, margin:'14px 0 0', lineHeight:0.95 }}>
                {isSignup ? <>Tune <em>in</em>.</> : <>Welcome <em>home</em>.</>}
              </h1>
              <div className="display-cn" style={{ fontSize:32, marginTop:16, color:'var(--ink-2)' }}>
                {isSignup ? '今晚开始，你也有一座电台。' : '欢迎回来。电台已替你温着。'}
              </div>
              <p style={{ marginTop:24, fontSize:15, lineHeight:1.7, color:'var(--ink-2)', maxWidth:380, fontFamily:'var(--serif-cn)' }}>
                {isSignup
                  ? '我们读取你的情绪，跨越平台编排音乐——你只需要说一句话，或者什么都别说。'
                  : '上一次你听到的是 Marconi Union 的《weightless》，进度 1:24。准备好继续了吗？'}
              </p>
            </div>
            <div className="between" style={{ fontFamily:'var(--mono)', fontSize:11, letterSpacing:'.18em', textTransform:'uppercase', color:'var(--ink-3)' }}>
              <span>SPRING / 2026</span><span>A PRIVATE STATION</span>
            </div>
          </div>
        </div>

        {/* RIGHT */}
        <div style={{ padding:'64px 80px', display:'flex', flexDirection:'column', justifyContent:'center' }}>
          <div className="meta">{isSignup ? 'NEW ACCOUNT · 创建账号' : 'SIGN IN · 登录'}</div>
          <h2 className="display" style={{ fontSize:72, margin:'10px 0 0' }}>
            {isSignup ? <>Begin.</> : <>Hi, again.</>}
          </h2>
          <div className="display-cn" style={{ fontSize:28, marginTop:6, color:'var(--ink-2)' }}>
            {isSignup ? '注册 · 用一句话描述你' : '登录 · 继续昨晚的电台'}
          </div>

          <div style={{ marginTop:40 }}>
            {isSignup ? <SignupForm/> : <LoginForm/>}
          </div>

          <div className="between" style={{ marginTop:28, fontFamily:'var(--serif-cn)', fontSize:14, color:'var(--ink-2)' }}>
            <span>
              {isSignup ? '已有账号？' : '还没有账号？'}{' '}
              <a onClick={() => setMode(isSignup ? 'login' : 'signup')} style={{ color:'var(--ink)', textDecoration:'underline', textUnderlineOffset:3, cursor:'pointer' }}>
                {isSignup ? '登录' : '注册'} →
              </a>
            </span>
            <a onClick={() => navigate('/')} style={{ color:'var(--ink-3)', cursor:'pointer' }}>← 返回首页</a>
          </div>
        </div>
      </div>
    </div>
  );
}
