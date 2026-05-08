import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Icon } from '../components/atoms';
import { useUserStore } from '../store/userStore';
import { useAuthStore } from '../store/authStore';

function Field({ label, value, editing, onChange }) {
  return (
    <div style={{ marginBottom: 16 }}>
      <div style={{ fontFamily: 'var(--mono)', fontSize: 11, letterSpacing: '.14em', opacity: 0.45, marginBottom: 6 }}>
        {label}
      </div>
      {editing ? (
        <input
          value={value ?? ''}
          onChange={e => onChange(e.target.value)}
          style={{
            width: '100%', padding: '10px 0', background: 'transparent',
            border: 'none', borderBottom: '1px solid var(--ink)',
            color: 'var(--ink)', fontSize: 16, fontFamily: 'var(--serif-cn)',
            outline: 'none',
          }}
        />
      ) : (
        <div style={{ padding: '10px 0', borderBottom: '1px solid var(--rule)', fontSize: 16, fontFamily: 'var(--serif-cn)' }}>
          {value || <span style={{ opacity: 0.3 }}>未填写</span>}
        </div>
      )}
    </div>
  );
}

export default function Profile() {
  const navigate = useNavigate();
  const { profile, loading, fetchProfile, updateProfile, uploadAvatar } = useUserStore();
  const authUser = useAuthStore(s => s.user);
  const setAuthUser = useAuthStore(s => s.setUser);

  const [editing, setEditing]   = useState(false);
  const [saving,  setSaving]    = useState(false);
  const [toast,   setToast]     = useState(null);
  const [form,    setForm]      = useState({ username: '', email: '', phone: '' });

  useEffect(() => { fetchProfile(); }, []);

  useEffect(() => {
    if (profile) {
      setForm({ username: profile.username ?? '', email: profile.email ?? '', phone: profile.phone ?? '' });
    }
  }, [profile]);

  const showToast = (msg, ok = true) => {
    setToast({ msg, ok });
    setTimeout(() => setToast(null), 2500);
  };

  const handleSave = async () => {
    setSaving(true);
    const result = await updateProfile(form);
    setSaving(false);
    if (result.ok) {
      setEditing(false);
      setAuthUser({ ...authUser, username: form.username });
      showToast('保存成功');
    } else {
      showToast(result.error, false);
    }
  };

  const handleAvatarChange = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const result = await uploadAvatar(file);
    if (!result.ok) showToast(result.error, false);
  };

  const field = (key) => ({
    value: form[key],
    editing,
    onChange: (v) => setForm(f => ({ ...f, [key]: v })),
  });

  return (
    <div style={{ minHeight: '100vh', background: 'var(--bg)', color: 'var(--ink)' }}>
      {/* Sticky top bar */}
      <div style={{ position: 'sticky', top: 0, zIndex: 5, background: 'var(--bg)', padding: '18px 24px', borderBottom: '1px solid var(--rule)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <button onClick={() => navigate('/settings')} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--ink)', display: 'flex', alignItems: 'center', gap: 6 }}>
          <Icon.chevL /> <span style={{ fontFamily: 'var(--mono)', fontSize: 11, letterSpacing: '.12em' }}>设置</span>
        </button>
        <div style={{ fontFamily: 'var(--mono)', fontSize: 11, letterSpacing: '.14em', opacity: 0.5 }}>PROFILE · 个人资料</div>
        {editing ? (
          <button onClick={handleSave} disabled={saving} style={{ background: 'var(--ink)', color: 'var(--bg)', border: 'none', borderRadius: 20, padding: '7px 18px', cursor: 'pointer', fontFamily: 'var(--serif-cn)', fontSize: 13 }}>
            {saving ? '保存中…' : '保存'}
          </button>
        ) : (
          <button onClick={() => setEditing(true)} style={{ background: 'none', border: '1px solid var(--rule)', borderRadius: 20, padding: '7px 18px', cursor: 'pointer', color: 'var(--ink)', fontFamily: 'var(--serif-cn)', fontSize: 13 }}>
            编辑
          </button>
        )}
      </div>

      <div style={{ padding: '32px 24px 80px', maxWidth: 760, margin: '0 auto' }}>
        {/* Title */}
        <div style={{ fontFamily: 'var(--mono)', fontSize: 11, letterSpacing: '.14em', opacity: 0.4 }}>YOUR CARD · 你的卡片</div>
        <h1 style={{ fontFamily: 'var(--serif-en, serif)', fontSize: 'clamp(48px, 8vw, 96px)', margin: '8px 0 4px', lineHeight: 1, fontStyle: 'italic' }}>
          Profile<span style={{ opacity: 0.3 }}>.</span>
        </h1>
        <div style={{ fontFamily: 'var(--serif-cn)', fontSize: 18, color: 'var(--ink)', opacity: 0.5, marginBottom: 36 }}>这是你在电台里的样子</div>

        {loading && !profile ? (
          <div style={{ opacity: 0.4, fontFamily: 'var(--mono)', fontSize: 12 }}>加载中…</div>
        ) : (
          <>
            {/* Avatar + name/bio row */}
            <div style={{ display: 'flex', gap: 28, marginBottom: 36, alignItems: 'flex-start', flexWrap: 'wrap' }}>
              <div style={{ position: 'relative', flexShrink: 0 }}>
                <div style={{ width: 120, height: 120, borderRadius: '50%', background: 'var(--surface, #f0f0f0)', overflow: 'hidden', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 40 }}>
                  {profile?.avatarUrl
                    ? <img src={profile.avatarUrl} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                    : <span style={{ opacity: 0.3 }}>🎵</span>
                  }
                </div>
                <label style={{ position: 'absolute', bottom: 4, right: 4, background: 'var(--ink)', color: 'var(--bg)', borderRadius: 14, padding: '4px 10px', fontSize: 11, fontFamily: 'var(--mono)', cursor: 'pointer', letterSpacing: '.08em' }}>
                  更换
                  <input type="file" accept="image/*" style={{ display: 'none' }} onChange={handleAvatarChange} />
                </label>
              </div>
              <div style={{ flex: 1, minWidth: 220 }}>
                <Field label="昵称 · NAME" {...field('username')} />
              </div>
            </div>

            {/* Contact fields */}
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))', gap: '0 32px' }}>
              <Field label="邮箱 · EMAIL" {...field('email')} />
              <Field label="手机 · PHONE" {...field('phone')} />
            </div>

            {/* Divider */}
            <div style={{ borderTop: '1px dashed var(--rule)', margin: '32px 0 24px' }} />

            {/* Password change */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '14px 0', borderBottom: '1px solid var(--rule)', cursor: 'pointer' }}
              onClick={() => navigate('/settings')}>
              <div style={{ fontFamily: 'var(--serif-cn)', fontSize: 15 }}>修改密码</div>
              <span style={{ opacity: 0.4 }}>›</span>
            </div>

            {/* Delete account */}
            <div style={{ marginTop: 48, textAlign: 'center' }}>
              <button style={{ background: 'none', border: 'none', color: '#e05', fontSize: 13, fontFamily: 'var(--serif-cn)', cursor: 'pointer', opacity: 0.7 }}>
                注销账号
              </button>
            </div>
          </>
        )}
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
