import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Icon } from '../components/atoms';
import { blacklistApi } from '../api/blacklist';

const TABS = ['歌手', '歌曲', '关键词'];
const TYPE_MAP = { '歌手': 'artist', '歌曲': 'song', '关键词': 'keyword' };

export default function Blacklist() {
  const navigate = useNavigate();
  const [tab, setTab]         = useState('歌手');
  const [items, setItems]     = useState([]);
  const [input, setInput]     = useState('');
  const [loading, setLoading] = useState(true);
  const [adding, setAdding]   = useState(false);
  const [error, setError]     = useState(null);

  useEffect(() => {
    blacklistApi.getAll()
      .then(d => { setItems(Array.isArray(d) ? d : []); setLoading(false); })
      .catch(() => { setError('加载失败'); setLoading(false); });
  }, []);

  const filtered = items.filter(i => i.type === TYPE_MAP[tab]);

  const handleAdd = async () => {
    const val = input.trim();
    if (!val || adding) return;
    setAdding(true);
    try {
      const entry = await blacklistApi.add({ type: TYPE_MAP[tab], value: val, label: val });
      setItems(prev => [...prev, entry]);
      setInput('');
    } catch {
      // silently ignore — item may already exist
    } finally {
      setAdding(false);
    }
  };

  const handleRemove = async (id) => {
    await blacklistApi.remove(id).catch(() => {});
    setItems(prev => prev.filter(i => i.id !== id));
  };

  return (
    <div style={{ minHeight: '100vh', background: 'var(--bg)', color: 'var(--ink)' }}>
      <div style={{
        position: 'sticky', top: 0, zIndex: 5,
        background: 'var(--bg)', borderBottom: '1px solid var(--rule)',
        padding: '14px 20px', display: 'flex', alignItems: 'center', gap: 12,
      }}>
        <button onClick={() => navigate(-1)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--ink)', display: 'flex', alignItems: 'center' }}>
          <Icon.chevL/>
        </button>
        <h1 style={{ fontFamily: 'var(--serif-cn)', fontSize: 18, margin: 0, flex: 1 }}>黑名单管理</h1>
        <span className="mono" style={{ fontSize: 11, opacity: 0.45 }}>{items.length} 条</span>
      </div>

      <div style={{ padding: '24px 20px 80px', maxWidth: 560, margin: '0 auto' }}>
        <div className="meta" style={{ marginBottom: 6 }}>BLOCKLIST · 屏蔽管理</div>
        <h2 className="display" style={{ fontSize: 52, margin: '6px 0 24px', lineHeight: 1 }}>Blocklist.</h2>

        {/* Tab switcher */}
        <div style={{ display: 'flex', gap: 8, marginBottom: 20 }}>
          {TABS.map(t => (
            <button key={t} onClick={() => setTab(t)} style={{
              padding: '6px 20px', borderRadius: 999,
              border: '1px solid var(--rule)',
              background: tab === t ? 'var(--ink)' : 'transparent',
              color: tab === t ? 'var(--bg)' : 'var(--ink)',
              cursor: 'pointer', fontFamily: 'var(--serif-cn)', fontSize: 13,
              transition: 'background .15s',
            }}>{t}</button>
          ))}
        </div>

        {/* Add row */}
        <div style={{ display: 'flex', gap: 8, marginBottom: 24 }}>
          <input
            value={input}
            onChange={e => setInput(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && handleAdd()}
            placeholder={`添加${tab}到黑名单…`}
            className="field"
            style={{ flex: 1 }}
          />
          <button
            onClick={handleAdd}
            disabled={adding || !input.trim()}
            className="btn"
            style={{ padding: '0 18px', height: 44, opacity: adding || !input.trim() ? 0.5 : 1 }}
          >添加</button>
        </div>

        {loading && <div className="mono" style={{ opacity: 0.5 }}>加载中…</div>}
        {error   && <div style={{ color: 'var(--mood-b, #e05)', fontFamily: 'var(--serif-cn)' }}>{error}</div>}

        {!loading && !error && filtered.length === 0 && (
          <div style={{ textAlign: 'center', paddingTop: 40, opacity: 0.35, fontFamily: 'var(--serif-cn)', fontSize: 14 }}>
            {tab}黑名单为空
          </div>
        )}

        {!loading && !error && filtered.map(item => (
          <div key={item.id} style={{
            display: 'flex', justifyContent: 'space-between', alignItems: 'center',
            padding: '13px 0', borderBottom: '1px solid var(--rule)',
          }}>
            <div>
              <span style={{ fontFamily: 'var(--serif-cn)', fontSize: 14 }}>{item.label || item.value}</span>
              {item.label && item.label !== item.value && (
                <span className="mono" style={{ fontSize: 11, opacity: 0.4, marginLeft: 8 }}>{item.value}</span>
              )}
            </div>
            <button
              onClick={() => handleRemove(item.id)}
              style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--mood-b, #e05)', fontFamily: 'var(--serif-cn)', fontSize: 13 }}
            >移除</button>
          </div>
        ))}
      </div>
    </div>
  );
}
