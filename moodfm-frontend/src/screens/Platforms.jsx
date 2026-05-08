import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Icon } from '../components/atoms';
import { platformApi } from '../api/platform';

const PLATFORM_META = {
  netease: { name: '网易云音乐', en: 'NETEASE CLOUD MUSIC', logo: '网' },
  qqmusic: { name: 'QQ 音乐',   en: 'QQ MUSIC',           logo: 'Q'  },
};

function Stat({ label, value, warn }) {
  return (
    <div>
      <div style={{ fontFamily: 'var(--mono)', fontSize: 10, letterSpacing: '.12em', opacity: warn ? 1 : 0.45, color: warn ? 'var(--mood-b, #e07a30)' : 'inherit' }}>
        {label}
      </div>
      <div style={{ fontFamily: 'var(--mono)', marginTop: 4, fontSize: 13, color: warn ? 'var(--mood-b, #e07a30)' : 'var(--ink)' }}>
        {value}
      </div>
    </div>
  );
}

function PlatformCard({ binding, meta, onUnbind, onSetDefault, onRebind }) {
  const [confirming, setConfirming] = useState(false);
  const [loading,    setLoading]    = useState(false);

  const isExpiringSoon = binding?.isValid && binding?.expiresAt
    ? (new Date(binding.expiresAt) - Date.now()) < 3 * 24 * 3600 * 1000
    : false;

  const statusLabel = !binding ? '未绑定'
    : !binding.isValid       ? '已失效'
    : isExpiringSoon         ? '即将过期'
    : '正常';

  const statusWarn = !binding?.isValid || isExpiringSoon;

  const handleUnbind = async () => {
    if (!confirming) { setConfirming(true); return; }
    setLoading(true);
    try { await onUnbind(); } finally { setLoading(false); setConfirming(false); }
  };

  const handleSetDefault = async () => {
    setLoading(true);
    try { await onSetDefault(); } finally { setLoading(false); }
  };

  return (
    <div style={{ padding: 22, border: '1px solid var(--rule)', borderRadius: 16, background: binding ? 'var(--paper, var(--surface, #fafafa))' : 'transparent' }}>
      {/* Header row */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
        {/* Logo */}
        <div style={{
          width: 56, height: 56, borderRadius: '50%', flexShrink: 0,
          background: binding ? 'linear-gradient(135deg, var(--mood-a), var(--mood-c))' : 'var(--rule)',
          color: binding ? '#fff' : 'var(--ink)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          fontFamily: 'var(--serif-cn)', fontSize: 22, fontWeight: 600,
        }}>
          {meta.logo}
        </div>

        {/* Name + status */}
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ display: 'flex', gap: 10, alignItems: 'baseline', flexWrap: 'wrap' }}>
            <span style={{ fontFamily: 'var(--serif-cn)', fontSize: 18, fontWeight: 500 }}>{meta.name}</span>
            {binding && (
              <span style={{
                fontFamily: 'var(--mono)', fontSize: 11, padding: '2px 10px', borderRadius: 999,
                background: statusWarn ? 'var(--mood-b, #e07a30)' : 'var(--ink)',
                color: 'var(--bg)',
              }}>
                {statusLabel}
              </span>
            )}
            {binding?.isDefault && (
              <span style={{ fontFamily: 'var(--mono)', fontSize: 10, opacity: 0.5 }}>· 默认</span>
            )}
          </div>
          <div style={{ fontFamily: 'var(--mono)', fontSize: 11, opacity: 0.4, marginTop: 2 }}>{meta.en}</div>
        </div>

        {/* Actions */}
        {binding ? (
          <div style={{ display: 'flex', gap: 8, flexShrink: 0 }}>
            {!binding.isDefault && (
              <button onClick={handleSetDefault} disabled={loading} style={{ padding: '6px 14px', borderRadius: 20, border: '1px solid var(--rule)', background: 'transparent', color: 'var(--ink)', cursor: 'pointer', fontFamily: 'var(--serif-cn)', fontSize: 13 }}>
                设为默认
              </button>
            )}
            <button onClick={() => onRebind()} style={{ padding: '6px 14px', borderRadius: 20, border: '1px solid var(--rule)', background: 'transparent', color: 'var(--ink)', cursor: 'pointer', fontFamily: 'var(--serif-cn)', fontSize: 13 }}>
              续期
            </button>
            <button onClick={handleUnbind} disabled={loading} style={{ padding: '6px 14px', borderRadius: 20, border: '1px solid var(--mood-b, #e07a30)', background: 'transparent', color: 'var(--mood-b, #e07a30)', cursor: 'pointer', fontFamily: 'var(--serif-cn)', fontSize: 13 }}>
              {confirming ? '确认解绑？' : '解绑'}
            </button>
          </div>
        ) : (
          <button onClick={() => onRebind()} style={{ padding: '8px 20px', borderRadius: 20, border: 'none', background: 'var(--ink)', color: 'var(--bg)', cursor: 'pointer', fontFamily: 'var(--serif-cn)', fontSize: 14 }}>
            绑定 →
          </button>
        )}
      </div>

      {/* Stats row (bound only) */}
      {binding && (
        <div style={{ display: 'flex', gap: 28, marginTop: 18, paddingTop: 16, borderTop: '1px dashed var(--rule)', flexWrap: 'wrap' }}>
          <Stat label="账号" value={binding.platformUsername || '—'} />
          <Stat label="Cookie" value={binding.isValid ? '有效' : '已失效'} warn={!binding.isValid} />
          <Stat label="上次验证" value={binding.lastValidatedAt ? new Date(binding.lastValidatedAt).toLocaleDateString('zh-CN') : '—'} warn={isExpiringSoon} />
        </div>
      )}
    </div>
  );
}

export default function Platforms() {
  const navigate = useNavigate();
  const [bindings, setBindings] = useState([]);
  const [loading,  setLoading]  = useState(true);
  const [toast,    setToast]    = useState(null);

  useEffect(() => {
    platformApi.getBindings()
      .then(data => setBindings(Array.isArray(data) ? data : []))
      .catch(() => setBindings([]))
      .finally(() => setLoading(false));
  }, []);

  const showToast = (msg, ok = true) => {
    setToast({ msg, ok });
    setTimeout(() => setToast(null), 2500);
  };

  const getBinding = (platform) => bindings.find(b => b.platform === platform);

  const handleUnbind = async (platform) => {
    try {
      await platformApi.unbind(platform);
      setBindings(prev => prev.filter(b => b.platform !== platform));
      showToast('已解绑');
    } catch {
      showToast('解绑失败，请重试', false);
    }
  };

  const handleSetDefault = async (platform) => {
    try {
      await platformApi.setDefault(platform);
      setBindings(prev => prev.map(b => ({ ...b, isDefault: b.platform === platform })));
      showToast('已设为默认音源');
    } catch {
      showToast('操作失败，请重试', false);
    }
  };

  const handleRebind = (platform) => {
    navigate('/bind', { state: { platform } });
  };

  return (
    <div style={{ minHeight: '100vh', background: 'var(--bg)', color: 'var(--ink)' }}>
      {/* Sticky top bar */}
      <div style={{ position: 'sticky', top: 0, zIndex: 5, background: 'var(--bg)', padding: '18px 24px', borderBottom: '1px solid var(--rule)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <button onClick={() => navigate('/settings')} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--ink)', display: 'flex', alignItems: 'center', gap: 6 }}>
          <Icon.chevL /> <span style={{ fontFamily: 'var(--mono)', fontSize: 11, letterSpacing: '.12em' }}>设置</span>
        </button>
        <div style={{ fontFamily: 'var(--mono)', fontSize: 11, letterSpacing: '.14em', opacity: 0.5 }}>PLATFORMS · 平台管理</div>
        <div style={{ width: 60 }} />
      </div>

      <div style={{ padding: '32px 24px 80px', maxWidth: 860, margin: '0 auto' }}>
        {/* Title */}
        <div style={{ fontFamily: 'var(--mono)', fontSize: 11, letterSpacing: '.14em', opacity: 0.4 }}>CONNECTED · 已接入</div>
        <h1 style={{ fontFamily: 'var(--serif-en, serif)', fontSize: 'clamp(48px, 8vw, 96px)', margin: '8px 0 4px', lineHeight: 1, fontStyle: 'italic' }}>
          Sources<span style={{ opacity: 0.3 }}>.</span>
        </h1>
        <div style={{ fontFamily: 'var(--serif-cn)', fontSize: 18, opacity: 0.5, marginBottom: 32 }}>电台从这些地方取曲</div>

        {loading ? (
          <div style={{ opacity: 0.4, fontFamily: 'var(--mono)', fontSize: 12 }}>加载中…</div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            {Object.entries(PLATFORM_META).map(([key, meta]) => (
              <PlatformCard
                key={key}
                binding={getBinding(key)}
                meta={meta}
                onUnbind={() => handleUnbind(key)}
                onSetDefault={() => handleSetDefault(key)}
                onRebind={() => handleRebind(key)}
              />
            ))}
          </div>
        )}

        {/* Security note */}
        <details style={{ marginTop: 40, fontSize: 13, color: 'var(--ink)', opacity: 0.5 }}>
          <summary style={{ cursor: 'pointer', fontFamily: 'var(--serif-cn)' }}>你的账号信息如何被保护？</summary>
          <p style={{ marginTop: 10, lineHeight: 1.8, fontFamily: 'var(--serif-cn)' }}>
            你的 Cookie 使用 AES-256-GCM 加密后存储在本地服务器，不上传至任何第三方。
            前端仅展示绑定状态，不展示完整 Cookie。你可以随时解绑。
          </p>
        </details>
      </div>

      {/* Toast */}
      {toast && (
        <div style={{
          position: 'fixed', bottom: 32, left: '50%', transform: 'translateX(-50%)',
          background: toast.ok ? 'var(--ink)' : '#e05',
          color: '#fff', borderRadius: 24, padding: '10px 22px',
          fontFamily: 'var(--serif-cn)', fontSize: 14, zIndex: 100,
          boxShadow: '0 4px 20px rgba(0,0,0,0.2)',
        }}>
          {toast.msg}
        </div>
      )}
    </div>
  );
}
