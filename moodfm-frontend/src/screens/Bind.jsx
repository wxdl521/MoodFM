import { useState, useEffect, useRef } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Icon, Logo } from '../components/atoms';
import { platformApi } from '../api/platform';

// ---- 真实 QR 扫码组件 ----
function QRBlock({ platform, onSuccess }) {
  const [qrImg, setQrImg] = useState('');
  const [qrKey, setQrKey] = useState('');
  const [status, setStatus] = useState('loading'); // loading | waiting | scanned | success | expired | error
  const [countdown, setCountdown] = useState(90);
  const pollRef = useRef(null);
  const countRef = useRef(null);

  const startQR = async () => {
    setStatus('loading');
    setCountdown(90);
    clearInterval(pollRef.current);
    clearInterval(countRef.current);
    try {
      const res = await platformApi.generateQR(platform);
      if (res.code === 200) {
        setQrImg(res.data.qrimg);
        setQrKey(res.data.key);
        setStatus('waiting');
        startPolling(res.data.key);
        startCountdown();
      } else {
        setStatus('error');
      }
    } catch (e) {
      console.error('QR generate failed', e);
      setStatus('error');
    }
  };

  const startPolling = (key) => {
    pollRef.current = setInterval(async () => {
      try {
        const res = await platformApi.checkQRStatus(platform, key);
        if (res.code === 200) {
          const s = res.data.status;
          setStatus(s);
          if (s === 'success') {
            clearInterval(pollRef.current);
            clearInterval(countRef.current);
            onSuccess?.();
          } else if (s === 'expired' || s === 'error') {
            clearInterval(pollRef.current);
            clearInterval(countRef.current);
          }
        }
      } catch {}
    }, 2000);
  };

  const startCountdown = () => {
    countRef.current = setInterval(() => {
      setCountdown(c => {
        if (c <= 1) {
          clearInterval(countRef.current);
          return 0;
        }
        return c - 1;
      });
    }, 1000);
  };

  useEffect(() => {
    startQR();
    return () => { clearInterval(pollRef.current); clearInterval(countRef.current); };
  }, [platform]);

  const statusText = {
    loading: '正在生成二维码…',
    waiting: `等待扫码 · ${countdown}s`,
    scanned: '已扫码，请在手机上确认',
    success: '绑定成功！',
    expired: '二维码已过期',
    error: '扫码确认成功，但凭证获取失败——请切换至 Cookie 方式完成绑定',
  }[status] || '';

  return (
    <div style={{ textAlign: 'center' }}>
      <div style={{ display: 'inline-block', position: 'relative', padding: 24, border: '1px solid var(--rule)', borderRadius: 18, background: 'var(--bg)' }}>
        <div style={{ width: 200, height: 200, position: 'relative', background: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          {qrImg ? (
            <img src={qrImg} alt="QR Code" style={{ width: 184, height: 184 }} />
          ) : (
            <div style={{ fontFamily: 'var(--mono)', fontSize: 11, color: 'var(--ink-3)' }}>
              {status === 'loading' ? '加载中…' : '—'}
            </div>
          )}
          {(status === 'expired' || status === 'error') && (
            <div style={{ position: 'absolute', inset: 0, background: 'rgba(255,255,255,0.92)', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 8, padding: 8 }}>
              <div className="meta">{status === 'expired' ? 'QR EXPIRED' : '凭证获取失败'}</div>
              {status === 'expired'
                ? <button onClick={startQR} className="btn" style={{ height: 36 }}>刷新</button>
                : <div style={{ fontFamily: 'var(--serif-cn)', fontSize: 12, color: 'var(--ink-2)', textAlign: 'center', lineHeight: 1.5 }}>
                    扫码已确认，请切换<br/>至 <b>Cookie</b> 方式完成绑定
                  </div>
              }
            </div>
          )}
          {status === 'success' && (
            <div style={{ position: 'absolute', inset: 0, background: 'rgba(255,255,255,0.92)', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 8 }}>
              <div style={{ fontSize: 32 }}>✓</div>
              <div className="meta" style={{ color: 'var(--ink)' }}>绑定成功</div>
            </div>
          )}
        </div>
      </div>
      <div className="meta" style={{ marginTop: 18 }}>
        STATUS · <span style={{ color: status === 'expired' || status === 'error' ? 'var(--mood-b)' : status === 'success' ? 'var(--focused)' : 'var(--ink)' }}>
          {statusText}
        </span>
      </div>
      <div style={{ marginTop: 10, fontFamily: 'var(--serif-cn)', fontSize: 13, color: 'var(--ink-2)' }}>
        打开{platform === 'netease' ? '网易云' : 'QQ 音乐'} App → 我的 → 扫一扫
      </div>
    </div>
  );
}

function CookieBlock({ platform, onSuccess }) {
  const [cookie, setCookie] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const submit = async () => {
    if (!cookie.trim()) { setError('请填写 Cookie'); return; }
    setLoading(true); setError('');
    try {
      const res = await platformApi.bindCookie(platform, cookie.trim());
      if (res.code === 200) onSuccess?.();
      else setError(res.message || '绑定失败');
    } catch (e) {
      setError(e?.message || '绑定失败，请检查 Cookie 格式');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      {error && <div style={{ marginBottom: 12, padding: '10px 14px', background: 'rgba(232,90,138,0.08)', border: '1px solid var(--mood-b)', borderRadius: 10, fontFamily: 'var(--serif-cn)', fontSize: 13, color: 'var(--mood-b)' }}>{error}</div>}
      <div style={{ marginBottom: 14 }}>
        <div className="between" style={{ marginBottom: 8 }}>
          <div className="meta">COOKIE</div>
          <span className="meta" style={{ color: 'var(--ink-2)' }}>浏览器开发者工具中复制</span>
        </div>
        <textarea className="field" value={cookie} onChange={e => setCookie(e.target.value)}
          placeholder="MUSIC_U=...; __csrf=...; NMTID=...;"
          style={{ minHeight: 160, fontFamily: 'var(--mono)', fontSize: 12 }} />
      </div>
      <button className="btn" onClick={submit} disabled={loading} style={{ width: '100%', height: 48, opacity: loading ? 0.7 : 1 }}>
        {loading ? '验证中…' : <><span>验证并保存</span> <Icon.arrow /></>}
      </button>
      <div className="meta" style={{ marginTop: 14, color: 'var(--ink-3)', lineHeight: 1.6 }}>
        · 高级方式 · 适合扫码不便时使用<br />
        · Cookie 会本地加密 · 服务端不留存明文
      </div>
    </div>
  );
}

function PhoneBlock({ platform, onSuccess }) {
  const [phone, setPhone] = useState('');
  const [code, setCode] = useState('');
  const [sent, setSent] = useState(false);
  const [countdown, setCountdown] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const timerRef = useRef(null);

  const sendCode = async () => {
    if (!phone.trim()) { setError('请输入手机号'); return; }
    setLoading(true); setError('');
    try {
      const res = await platformApi.sendPhoneCode(platform, phone.trim());
      if (res.code === 200) {
        setSent(true);
        setCountdown(60);
        timerRef.current = setInterval(() => {
          setCountdown(c => {
            if (c <= 1) { clearInterval(timerRef.current); return 0; }
            return c - 1;
          });
        }, 1000);
      } else {
        setError(res.message || '发送失败');
      }
    } catch (e) {
      setError(e?.message || '发送失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => () => clearInterval(timerRef.current), []);

  const submit = async () => {
    if (!code.trim()) { setError('请输入验证码'); return; }
    setLoading(true); setError('');
    try {
      const res = await platformApi.bindPhone(platform, { phone: phone.trim(), captcha: code.trim() });
      if (res.code === 200) onSuccess?.();
      else setError(res.message || '绑定失败');
    } catch (e) {
      setError(e?.message || '绑定失败，请重试');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      {error && (
        <div style={{ marginBottom: 12, padding: '10px 14px', background: 'rgba(232,90,138,0.08)', border: '1px solid var(--mood-b)', borderRadius: 10, fontFamily: 'var(--serif-cn)', fontSize: 13, color: 'var(--mood-b)' }}>
          {error}
        </div>
      )}
      <div style={{ marginBottom: 14 }}>
        <div className="between" style={{ marginBottom: 8 }}>
          <div className="meta">手机号 · PHONE</div>
          <span className="meta" style={{ color: 'var(--ink-3)' }}>
            绑定{platform === 'netease' ? '网易云' : 'QQ 音乐'}的手机号
          </span>
        </div>
        <div className="row" style={{ gap: 10 }}>
          <input className="field" value={phone} onChange={e => setPhone(e.target.value)}
            placeholder="+86 13x xxxx xxxx" style={{ flex: 1 }} />
          <button className="btn btn-ghost" onClick={sendCode} disabled={loading || countdown > 0}
            style={{ height: 46, padding: '0 16px', whiteSpace: 'nowrap', flexShrink: 0, opacity: countdown > 0 ? 0.6 : 1 }}>
            {countdown > 0 ? `${countdown}s` : sent ? '重新发送' : '发送验证码'}
          </button>
        </div>
      </div>
      {sent && (
        <div style={{ marginBottom: 14 }}>
          <div className="meta" style={{ marginBottom: 8 }}>验证码 · CODE</div>
          <input className="field" value={code} onChange={e => setCode(e.target.value)}
            placeholder="6 位验证码" maxLength={6}
            style={{ letterSpacing: '0.3em', fontSize: 18 }} />
        </div>
      )}
      <button className="btn" onClick={submit} disabled={loading || !sent}
        style={{ width: '100%', height: 48, opacity: loading || !sent ? 0.7 : 1 }}>
        {loading ? '验证中…' : <><span>验证并绑定</span> <Icon.arrow /></>}
      </button>
      <div className="meta" style={{ marginTop: 14, color: 'var(--ink-3)', lineHeight: 1.6 }}>
        · 手机号登录 · 适合国内用户<br />
        · 验证码 60 秒内有效 · 仅用于身份核验
      </div>
    </div>
  );
}

function BindModal({ tab, setTab, platform, onClose, inline, onSuccess }) {
  const platformLabel = platform === 'netease' ? '网易云音乐' : 'QQ 音乐';
  return (
    <div style={{ background: 'var(--paper)', borderRadius: inline ? 20 : 0, border: inline ? '1px solid var(--rule)' : 'none', padding: 28 }}>
      <div className="between" style={{ marginBottom: 18 }}>
        <div>
          <div className="meta">PLATFORM</div>
          <div className="display" style={{ fontSize: 36, marginTop: 4 }}><em>{platformLabel}</em></div>
        </div>
        {onClose && <button onClick={onClose} className="btn-pill">×</button>}
      </div>
      <div className="row" style={{ gap: 0, borderBottom: '1px solid var(--rule)', marginBottom: 24 }}>
        {[{ k: 'qr', l: '扫码登录', e: 'QR' }, { k: 'phone', l: '手机号', e: 'SMS' }, { k: 'cookie', l: 'Cookie', e: 'ADV' }].map(t => (
          <button key={t.k} onClick={() => setTab(t.k)} style={{
            background: 'transparent', border: 'none', padding: '12px 14px', cursor: 'pointer', position: 'relative',
            fontFamily: 'var(--serif-cn)', fontSize: 14, color: tab === t.k ? 'var(--ink)' : 'var(--ink-3)',
          }}>
            <span className="mono" style={{ fontSize: 10, letterSpacing: '.16em', color: 'var(--ink-3)', marginRight: 6 }}>{t.e}</span>
            {t.l}
            {tab === t.k && <div style={{ position: 'absolute', left: 0, right: 0, bottom: -1, height: 2, background: 'var(--ink)' }} />}
          </button>
        ))}
      </div>
      {tab === 'qr' && <QRBlock platform={platform} onSuccess={onSuccess} />}
      {tab === 'phone' && <PhoneBlock platform={platform} onSuccess={onSuccess} />}
      {tab === 'cookie' && <CookieBlock platform={platform} onSuccess={onSuccess} />}
    </div>
  );
}

function BindCard({ logo, name, en, bound, disabled, onBind }) {
  return (
    <div style={{ padding: 20, border: '1px solid var(--rule)', borderRadius: 18, background: 'var(--paper)', opacity: disabled ? 0.5 : 1 }}>
      <div className="row" style={{ gap: 14 }}>
        <div style={{
          width: 48, height: 48, borderRadius: '50%',
          background: bound ? 'linear-gradient(135deg, var(--mood-a), var(--mood-c))' : 'var(--bg-2)',
          color: bound ? '#fff' : 'var(--ink-3)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          fontFamily: 'var(--serif-cn)', fontSize: 20, fontWeight: 600, flexShrink: 0,
        }}>{logo}</div>
        <div style={{ flex: 1 }}>
          <div className="row" style={{ gap: 8 }}>
            <div style={{ fontFamily: 'var(--serif-cn)', fontSize: 18, fontWeight: 500 }}>{name}</div>
            {bound && <span className="meta" style={{ padding: '2px 8px', border: '1px solid var(--ink)', borderRadius: 999 }}>BOUND</span>}
          </div>
          <div className="meta" style={{ marginTop: 2 }}>{en}</div>
        </div>
        {!disabled && (
          <button onClick={onBind} className={bound ? 'btn-pill' : 'btn'} style={{ height: 38, padding: '0 16px' }}>
            {bound ? '重新绑定' : '绑定 →'}
          </button>
        )}
      </div>
      <div style={{ marginTop: 14, paddingTop: 14, borderTop: '1px dashed var(--rule)' }}>
        <span className="meta">{bound ? '· 已绑定，可正常使用' : '· 未绑定'}</span>
      </div>
    </div>
  );
}

export default function Bind() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const returnTo = params.get('returnTo');
  const [tab, setTab] = useState('qr');
  const [activePlatform, setActivePlatform] = useState('netease');
  const [bindings, setBindings] = useState([]);
  const [toast, setToast] = useState('');

  const loadBindings = () => {
    platformApi.getBindings().then(res => {
      if (res.code === 200) setBindings(res.data || []);
    }).catch(() => {});
  };

  useEffect(() => { loadBindings(); }, []);

  const isBound = (platform) => bindings.some(b => b.platform.toUpperCase() === platform.toUpperCase());

  const handleSuccess = () => {
    loadBindings();
    setToast('绑定成功！');
    setTimeout(() => setToast(''), 3000);
  };

  return (
    <div data-mood="dusk" className="mfm" style={{ minHeight: '100vh' }}>
      <div className="mood-blob drift" style={{ width: 700, height: 700, right: -200, top: -200, opacity: 0.3 }} />

      {/* Toast */}
      {toast && (
        <div style={{
          position: 'fixed', top: 24, left: '50%', transform: 'translateX(-50%)',
          padding: '12px 24px', background: 'var(--ink)', color: 'var(--bg)',
          borderRadius: 999, fontFamily: 'var(--mono)', fontSize: 12,
          letterSpacing: '.14em', zIndex: 100, boxShadow: '0 8px 32px rgba(0,0,0,0.2)',
        }}>{toast}</div>
      )}

      {/* Top */}
      <div style={{ position: 'sticky', top: 0, zIndex: 5, background: 'var(--bg)', padding: '22px 56px', borderBottom: '1px solid var(--rule)', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <button onClick={() => navigate('/home')} className="btn-pill"><Icon.chevL /> 返回</button>
        <div className="meta">PLATFORM · 平台绑定</div>
        <button onClick={() => navigate(returnTo === 'onboarding' ? '/onboarding?step=3' : '/home')} className="btn">
          {returnTo === 'onboarding' ? '完成绑定 → 继续引导' : '完成 → 进入电台'}
        </button>
      </div>

      <div style={{ position: 'relative', zIndex: 2, padding: '48px 56px 80px', display: 'grid', gridTemplateColumns: '1.2fr 1fr', gap: 48 }}>
        {/* LEFT */}
        <div>
          <div className="meta">SECTION II · 接入</div>
          <h1 className="display" style={{ fontSize: 108, margin: '12px 0 0' }}>Plug in.</h1>
          <div className="display-cn" style={{ fontSize: 32, marginTop: 8, color: 'var(--ink-2)' }}>把你的曲库接进来。</div>
          <p style={{ marginTop: 18, fontSize: 14, lineHeight: 1.7, color: 'var(--ink-2)', maxWidth: 480, fontFamily: 'var(--serif-cn)' }}>
            支持网易云音乐与 QQ 音乐。我们读取你的曲库与红心，跨平台编排——所有凭证都本地加密，可随时解绑。
          </p>

          <div style={{ marginTop: 32, display: 'grid', gap: 14 }}>
            <BindCard logo="网" name="网易云音乐" en="NETEASE CLOUD MUSIC"
              bound={isBound('netease')}
              onBind={() => setActivePlatform('netease')} />
            <BindCard logo="Q" name="QQ 音乐" en="QQ MUSIC"
              bound={isBound('qqmusic')}
              onBind={() => setActivePlatform('qqmusic')} />
            <BindCard logo="B" name="Bilibili 音乐" en="BILIBILI MUSIC" disabled />
          </div>
        </div>

        {/* RIGHT: sticky modal */}
        <div style={{ position: 'sticky', top: 100, alignSelf: 'start' }}>
          <BindModal
            tab={tab} setTab={setTab}
            platform={activePlatform}
            inline
            onSuccess={handleSuccess} />
        </div>
      </div>
    </div>
  );
}
