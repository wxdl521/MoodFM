import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Icon } from '../components/atoms';
import { useAuthStore } from '../store/authStore';

function Toggle({ on, onChange }) {
  return (
    <button
      onClick={() => onChange(!on)}
      style={{
        width: 42, height: 24, borderRadius: 999, padding: 2, border: 'none',
        background: on ? 'var(--ink)' : 'var(--rule)', cursor: 'pointer',
        position: 'relative', flexShrink: 0, transition: 'background .2s',
      }}
    >
      <span style={{
        display: 'block', width: 20, height: 20, borderRadius: '50%',
        background: 'var(--paper)',
        transform: on ? 'translateX(18px)' : 'translateX(0)',
        transition: 'transform .2s',
      }}/>
    </button>
  );
}

function Segment({ options, value, onChange }) {
  return (
    <div style={{
      display: 'flex', gap: 0,
      border: '1px solid var(--rule)', borderRadius: 999, padding: 2, flexShrink: 0,
    }}>
      {options.map((opt, i) => (
        <button key={opt} onClick={() => onChange(i)} style={{
          padding: '4px 12px', borderRadius: 999, border: 'none', cursor: 'pointer',
          background: value === i ? 'var(--ink)' : 'transparent',
          color: value === i ? 'var(--bg)' : 'var(--ink-2)',
          fontFamily: 'var(--mono)', fontSize: 11, letterSpacing: '.1em',
          whiteSpace: 'nowrap', transition: 'background .15s',
        }}>{opt}</button>
      ))}
    </div>
  );
}

function SettingRow({ label, sub, value, kind, danger, onClick, children }) {
  return (
    <div
      onClick={kind === 'link' && onClick ? onClick : undefined}
      style={{
        padding: '14px 18px',
        borderBottom: '1px solid var(--rule)',
        display: 'flex', alignItems: 'center', gap: 14,
        cursor: kind === 'link' && onClick ? 'pointer' : 'default',
      }}
    >
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{
          fontFamily: 'var(--serif-cn)', fontSize: 15,
          color: danger ? 'var(--mood-b, #e05)' : 'var(--ink)',
        }}>{label}</div>
        {sub && <div className="meta" style={{ marginTop: 2, color: 'var(--ink-3)' }}>· {sub}</div>}
      </div>
      {kind === 'link' && (
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, color: 'var(--ink-3)', flexShrink: 0 }}>
          {value && <span className="meta" style={{ color: 'var(--ink-2)' }}>{value}</span>}
          <span style={{ fontSize: 16 }}>›</span>
        </div>
      )}
      {kind === 'value' && (
        <span className="mono" style={{ fontSize: 12, color: 'var(--ink-3)', flexShrink: 0 }}>{value}</span>
      )}
      {children}
    </div>
  );
}

function GroupHeader({ num, en, cn }) {
  return (
    <div className="row" style={{ gap: 12, alignItems: 'baseline', marginBottom: 14 }}>
      <span className="meta">№ {String(num).padStart(2, '0')}</span>
      <span className="serif-en" style={{ fontSize: 28 }}>
        {en.toLowerCase()}<span style={{ color: 'var(--ink-3)' }}>.</span>
      </span>
      <span style={{ fontFamily: 'var(--serif-cn)', fontSize: 15, color: 'var(--ink-2)' }}>{cn}</span>
    </div>
  );
}

export default function Settings() {
  const navigate = useNavigate();
  const logout   = useAuthStore(s => s.logout);

  const [radioToggle, setRadioToggle] = useState({ rare: true, autoplay: true });
  const [theme, setTheme]    = useState(1);
  const [motionOff, setMotion] = useState(false);
  const [quality, setQuality] = useState(1);
  const [eqTime, setEqTime]   = useState(true);
  const [shareWeekly, setShareWeekly] = useState(false);
  const [anonData, setAnonData]       = useState(true);

  const handleLogout = () => {
    logout();
    navigate('/');
  };

  return (
    <div style={{ minHeight: '100vh', background: 'var(--bg)', color: 'var(--ink)' }}>
      {/* Sticky top bar */}
      <div style={{
        position: 'sticky', top: 0, zIndex: 5,
        background: 'var(--bg)', borderBottom: '1px solid var(--rule)',
        padding: '14px 20px',
        display: 'flex', justifyContent: 'space-between', alignItems: 'center',
      }}>
        <button
          onClick={() => navigate(-1)}
          style={{ background: 'none', border: '1px solid var(--rule)', borderRadius: 999, padding: '6px 14px', cursor: 'pointer', color: 'var(--ink)', display: 'flex', alignItems: 'center', gap: 6, fontFamily: 'var(--mono)', fontSize: 11, letterSpacing: '.12em' }}
        >
          <Icon.chevL/> Home
        </button>
        <div className="meta">SECTION VIII · SETTINGS · 设置</div>
        <div style={{ width: 80 }}/>
      </div>

      <div style={{ padding: '40px 20px 80px', maxWidth: 720, margin: '0 auto' }}>
        <div className="meta">CONTROL ROOM · 调台室</div>
        <h1 className="display" style={{ fontSize: 72, margin: '10px 0 0', lineHeight: 1 }}>Settings.</h1>
        <div style={{ fontFamily: 'var(--serif-cn)', fontSize: 20, color: 'var(--ink-2)', marginBottom: 40 }}>
          所有的旋钮 · 都在这里
        </div>

        {/* 01 账号 */}
        <div style={{ marginBottom: 36 }}>
          <GroupHeader num={1} en="ACCOUNT" cn="账号"/>
          <div style={{ border: '1px solid var(--rule)', borderRadius: 14, background: 'var(--paper)', overflow: 'hidden' }}>
            <SettingRow label="个人资料" value="江野 · jiangye@gmail.com" kind="link" onClick={() => navigate('/profile')}/>
            <SettingRow label="平台管理" value="网易云 · QQ 音乐" kind="link" onClick={() => navigate('/settings/platforms')}/>
            <div style={{ padding: '14px 18px', display: 'flex', alignItems: 'center', gap: 14, borderBottom: 'none' }}>
              <div style={{ flex: 1, fontFamily: 'var(--serif-cn)', fontSize: 15 }}>修改密码</div>
              <span style={{ fontSize: 16, color: 'var(--ink-3)' }}>›</span>
            </div>
          </div>
        </div>

        {/* 02 电台偏好 */}
        <div style={{ marginBottom: 36 }}>
          <GroupHeader num={2} en="RADIO" cn="电台偏好"/>
          <div style={{ border: '1px solid var(--rule)', borderRadius: 14, background: 'var(--paper)', overflow: 'hidden' }}>
            <SettingRow label="流派偏好" value="Ambient · Folk · Indie · +5" kind="link" onClick={() => {}}/>
            <SettingRow label="语言偏好" value="中文 · English · 日本語" kind="link" onClick={() => {}}/>
            <SettingRow label="默认场景" value="深夜" kind="link" onClick={() => {}}/>
            <div style={{ padding: '14px 18px', borderBottom: '1px solid var(--rule)', display: 'flex', alignItems: 'center', gap: 14 }}>
              <div style={{ flex: 1 }}>
                <div style={{ fontFamily: 'var(--serif-cn)', fontSize: 15 }}>露出冷门曲目</div>
                <div className="meta" style={{ marginTop: 2, color: 'var(--ink-3)' }}>· 让 AI 偶尔推荐你曲库外的歌</div>
              </div>
              <Toggle on={radioToggle.rare} onChange={v => setRadioToggle(s => ({ ...s, rare: v }))}/>
            </div>
            <div style={{ padding: '14px 18px', display: 'flex', alignItems: 'center', gap: 14 }}>
              <div style={{ flex: 1 }}>
                <div style={{ fontFamily: 'var(--serif-cn)', fontSize: 15 }}>自动续播</div>
                <div className="meta" style={{ marginTop: 2, color: 'var(--ink-3)' }}>· 听完一档自动开下一档</div>
              </div>
              <Toggle on={radioToggle.autoplay} onChange={v => setRadioToggle(s => ({ ...s, autoplay: v }))}/>
            </div>
          </div>
        </div>

        {/* 03 外观 */}
        <div style={{ marginBottom: 36 }}>
          <GroupHeader num={3} en="APPEARANCE" cn="外观"/>
          <div style={{ border: '1px solid var(--rule)', borderRadius: 14, background: 'var(--paper)', overflow: 'hidden' }}>
            <div style={{ padding: '14px 18px', borderBottom: '1px solid var(--rule)', display: 'flex', alignItems: 'center', gap: 14 }}>
              <div style={{ flex: 1, fontFamily: 'var(--serif-cn)', fontSize: 15 }}>主题</div>
              <Segment options={['明', '暗', '跟随系统']} value={theme} onChange={setTheme}/>
            </div>
            <div style={{ padding: '14px 18px', display: 'flex', alignItems: 'center', gap: 14 }}>
              <div style={{ flex: 1 }}>
                <div style={{ fontFamily: 'var(--serif-cn)', fontSize: 15 }}>减弱动效</div>
                <div className="meta" style={{ marginTop: 2, color: 'var(--ink-3)' }}>· 流体 blob、过渡都收敛</div>
              </div>
              <Toggle on={motionOff} onChange={setMotion}/>
            </div>
          </div>
        </div>

        {/* 04 声音 */}
        <div style={{ marginBottom: 36 }}>
          <GroupHeader num={4} en="AUDIO" cn="声音"/>
          <div style={{ border: '1px solid var(--rule)', borderRadius: 14, background: 'var(--paper)', overflow: 'hidden' }}>
            <div style={{ padding: '14px 18px', borderBottom: '1px solid var(--rule)', display: 'flex', alignItems: 'center', gap: 14 }}>
              <div style={{ flex: 1, fontFamily: 'var(--serif-cn)', fontSize: 15 }}>音质</div>
              <Segment options={['标准', '高品', '无损']} value={quality} onChange={setQuality}/>
            </div>
            <SettingRow label="淡入淡出" value="4s" kind="link" onClick={() => {}}/>
            <div style={{ padding: '14px 18px', display: 'flex', alignItems: 'center', gap: 14 }}>
              <div style={{ flex: 1 }}>
                <div style={{ fontFamily: 'var(--serif-cn)', fontSize: 15 }}>按时间均衡</div>
                <div className="meta" style={{ marginTop: 2, color: 'var(--ink-3)' }}>· 夜深时自动收紧低音</div>
              </div>
              <Toggle on={eqTime} onChange={setEqTime}/>
            </div>
          </div>
        </div>

        {/* 05 隐私 */}
        <div style={{ marginBottom: 36 }}>
          <GroupHeader num={5} en="PRIVACY" cn="隐私"/>
          <div style={{ border: '1px solid var(--rule)', borderRadius: 14, background: 'var(--paper)', overflow: 'hidden' }}>
            <div style={{ padding: '14px 18px', borderBottom: '1px solid var(--rule)', display: 'flex', alignItems: 'center', gap: 14 }}>
              <div style={{ flex: 1, fontFamily: 'var(--serif-cn)', fontSize: 15 }}>分享情绪周报到公开主页</div>
              <Toggle on={shareWeekly} onChange={setShareWeekly}/>
            </div>
            <div style={{ padding: '14px 18px', borderBottom: '1px solid var(--rule)', display: 'flex', alignItems: 'center', gap: 14 }}>
              <div style={{ flex: 1 }}>
                <div style={{ fontFamily: 'var(--serif-cn)', fontSize: 15 }}>匿名贡献心情数据</div>
                <div className="meta" style={{ marginTop: 2, color: 'var(--ink-3)' }}>· 帮助改善推荐 · 永不识别个人</div>
              </div>
              <Toggle on={anonData} onChange={setAnonData}/>
            </div>
            <SettingRow label="黑名单管理" value="12 首 · 3 位艺人" kind="link" onClick={() => navigate('/settings/blacklist')}/>
            <div style={{ padding: '14px 18px', display: 'flex', alignItems: 'center', gap: 14, cursor: 'pointer' }} onClick={() => {}}>
              <div style={{ flex: 1, fontFamily: 'var(--serif-cn)', fontSize: 15, color: 'var(--mood-b, #e05)' }}>清除播放历史</div>
              <span style={{ fontSize: 16, color: 'var(--ink-3)' }}>›</span>
            </div>
          </div>
        </div>

        {/* 06 关于 */}
        <div style={{ marginBottom: 36 }}>
          <GroupHeader num={6} en="ABOUT" cn="关于"/>
          <div style={{ border: '1px solid var(--rule)', borderRadius: 14, background: 'var(--paper)', overflow: 'hidden' }}>
            <SettingRow label="版本" value="1.4.2 (2026.05)" kind="value"/>
            <SettingRow label="服务条款 · 隐私政策" kind="link" onClick={() => {}}/>
            <SettingRow label="反馈与建议" kind="link" onClick={() => {}}/>
            <div style={{ padding: '14px 18px', display: 'flex', alignItems: 'center', gap: 14, cursor: 'pointer' }} onClick={handleLogout}>
              <div style={{ flex: 1, fontFamily: 'var(--serif-cn)', fontSize: 15, color: 'var(--mood-b, #e05)' }}>退出登录</div>
              <span style={{ fontSize: 16, color: 'var(--ink-3)' }}>›</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
