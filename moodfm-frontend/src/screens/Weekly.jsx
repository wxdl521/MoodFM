import { useState, useEffect, useRef } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { MoodBlob, Icon, Logo } from '../components/atoms';
import { reportsApi } from '../api/reports';

// ─── Share Card (for both preview and capture) ───────────────

function ShareCard({ report, cardRef }) {
  const stats = report?.stats ?? {};
  const mostPlayed = report?.mostPlayedSong;

  return (
    <div ref={cardRef} style={{
      width: 390, background: 'var(--bg)', position: 'relative',
      overflow: 'hidden', padding: '36px 28px',
      display: 'flex', flexDirection: 'column', minHeight: 740,
      fontFamily: 'var(--serif-cn)', color: 'var(--ink)',
    }}>
      <div className="mood-blob" style={{ width: 500, height: 500, right: -160, top: -130, opacity: 0.6, position: 'absolute' }} />

      <div style={{ position: 'relative', zIndex: 2, display: 'flex', flexDirection: 'column', height: '100%' }}>
        {/* Brand */}
        <div className="between">
          <div className="row" style={{ gap: 8 }}>
            <Logo size={18} />
            <div style={{ fontFamily: 'var(--serif-en)', fontSize: 16, fontStyle: 'italic' }}>MoodFM</div>
          </div>
          <div className="meta" style={{ color: 'var(--ink-3)' }}>{report?.weekLabel ?? 'WEEK —'}</div>
        </div>

        {/* Hero */}
        <div style={{ marginTop: 28 }}>
          <div className="meta">A WEEK OF</div>
          <h1 className="display" style={{ fontSize: 80, margin: '10px 0 0', lineHeight: 0.92 }}>
            <em>{report?.headlineWord ?? '—'}</em>
          </h1>
          <h1 className="display" style={{ fontSize: 80, margin: 0, lineHeight: 0.92 }}>
            {report?.headlineWord2 ?? 'light.'}
          </h1>
          <div className="display-cn" style={{ fontSize: 20, marginTop: 12, color: 'var(--ink-2)' }}>
            {report?.titleCn ?? ''}
          </div>
        </div>

        {/* Blob */}
        <div style={{ marginTop: 24, display: 'flex', justifyContent: 'center' }}>
          <MoodBlob size={240} drift={false} geometry="blob" />
        </div>

        {/* Stats 2×2 */}
        <div style={{ marginTop: 20, display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 1, background: 'var(--rule)', border: '1px solid var(--rule)', borderRadius: 14, overflow: 'hidden' }}>
          {[
            { n: stats.tracks ?? '—',         l: '首 · TRACKS' },
            { n: stats.listenTime ?? '—',     l: '时长 · TIME' },
            { n: stats.newDiscovered ?? '—',  l: '新发现 · NEW' },
            { n: stats.moodMatch ?? '—',      l: '匹配 · MATCH' },
          ].map((s, i) => (
            <div key={i} style={{ background: 'var(--paper)', padding: '14px 12px' }}>
              <div className="display" style={{ fontSize: 28 }}>{s.n}</div>
              <div className="meta" style={{ marginTop: 2 }}>{s.l}</div>
            </div>
          ))}
        </div>

        {/* Most played */}
        {mostPlayed && (
          <div style={{ marginTop: 20, padding: 16, background: 'var(--paper)', border: '1px solid var(--rule)', borderRadius: 14 }}>
            <div className="meta">MOST PLAYED · 最常听</div>
            <div style={{ fontFamily: 'var(--serif-en)', fontStyle: 'italic', fontSize: 24, marginTop: 6 }}>{mostPlayed.title}</div>
            <div style={{ fontFamily: 'var(--serif-cn)', fontSize: 13, color: 'var(--ink-2)' }}>
              {mostPlayed.artist} · {mostPlayed.playCount} 次
            </div>
          </div>
        )}

        {/* Footer */}
        <div style={{ marginTop: 'auto', paddingTop: 24, borderTop: '1px solid var(--rule)', display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end' }}>
          <div>
            <div className="meta">{report?.userNickname ?? 'MoodFM User'}</div>
            <div className="meta" style={{ marginTop: 4, color: 'var(--ink-3)' }}>moodfm.app</div>
          </div>
          <div style={{ width: 52, height: 52, background: '#fff', border: '1px solid var(--rule)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <div className="meta" style={{ fontSize: 7, color: 'var(--ink-3)' }}>QR CODE</div>
          </div>
        </div>
      </div>
    </div>
  );
}

// ─── Share Modal ──────────────────────────────────────────────

function ShareModal({ report, onClose }) {
  const cardRef = useRef(null);
  const [capturing, setCapturing] = useState(false);

  const download = async () => {
    if (!cardRef.current) return;
    setCapturing(true);
    try {
      const html2canvas = (await import('html2canvas')).default;
      const canvas = await html2canvas(cardRef.current, {
        scale: 2,
        useCORS: true,
        backgroundColor: null,
      });
      const link = document.createElement('a');
      link.download = `moodfm-week-${report?.weekLabel ?? 'report'}.png`.replace(/\s/g, '-').replace(/\//g, '-');
      link.href = canvas.toDataURL('image/png');
      link.click();
    } catch (e) {
      console.error('Capture failed', e);
      alert('截图失败，请尝试手动截图');
    } finally {
      setCapturing(false);
    }
  };

  return (
    <div style={{ position: 'fixed', inset: 0, zIndex: 100, background: 'rgba(0,0,0,0.6)', backdropFilter: 'blur(8px)', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 20, padding: 24 }}
      onClick={e => e.target === e.currentTarget && onClose()}>
      <div className="meta" style={{ color: '#fff' }}>SHARE CARD PREVIEW · 预览</div>

      {/* Card preview (scrollable on small screens) */}
      <div style={{ maxHeight: '70vh', overflowY: 'auto', borderRadius: 20, boxShadow: '0 32px 80px rgba(0,0,0,0.5)' }}>
        <div data-mood="dusk">
          <ShareCard report={report} cardRef={cardRef} />
        </div>
      </div>

      <div className="row" style={{ gap: 10 }}>
        <button className="btn" onClick={download} disabled={capturing}
          style={{ background: '#fff', color: 'var(--ink)', borderColor: '#fff', opacity: capturing ? 0.7 : 1 }}>
          {capturing ? '生成中…' : <>下载图片 <Icon.arrow /></>}
        </button>
        <button className="btn-pill" onClick={onClose} style={{ color: '#fff', borderColor: 'rgba(255,255,255,0.4)', background: 'transparent' }}>
          关闭
        </button>
      </div>
    </div>
  );
}

// ─── Discovery Card ──────────────────────────────────────────

function DiscoveryCard({ track }) {
  return (
    <div style={{ padding: 16, border: '1px solid var(--rule)', borderRadius: 14, background: 'var(--paper)' }}>
      <MoodBlob size={180} drift={false} geometry="blob" />
      <div className="meta" style={{ marginTop: 10 }}>· {track.mood ?? '—'}</div>
      <div style={{ fontFamily: 'var(--serif-en)', fontStyle: 'italic', fontSize: 17, marginTop: 4 }}>{track.title}</div>
      <div className="meta" style={{ marginTop: 2 }}>{track.artist} · {track.genre ?? ''}</div>
    </div>
  );
}

// ─── Main Page ────────────────────────────────────────────────

export default function Weekly() {
  const { id } = useParams();
  const navigate = useNavigate();

  const [report, setReport]   = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError]     = useState('');
  const [showShare, setShowShare] = useState(false);

  useEffect(() => {
    reportsApi.getWeeklyReport(id)
      .then(res => {
        if (res.code === 200) setReport(res.data);
        else setError(res.message || '周报加载失败');
      })
      .catch(() => setError('网络错误，请刷新重试'))
      .finally(() => setLoading(false));
  }, [id]);

  if (loading) {
    return (
      <div data-mood="dusk" className="mfm" style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <div style={{ fontFamily: 'var(--serif-cn)', fontSize: 18, color: 'var(--ink-2)' }}>加载中…</div>
      </div>
    );
  }

  if (error || !report) {
    return (
      <div data-mood="dusk" className="mfm" style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 20 }}>
        <div style={{ fontFamily: 'var(--serif-cn)', fontSize: 18, color: 'var(--ink-2)' }}>{error || '周报不存在'}</div>
        <button className="btn" onClick={() => navigate('/insights')}><Icon.chevL /> 返回洞察</button>
      </div>
    );
  }

  const stats = report.stats ?? {};
  const discoveries = report.topDiscoveries ?? [];

  return (
    <div data-mood="dusk" className="mfm" style={{ minHeight: '100vh' }}>
      {/* Share modal */}
      {showShare && <ShareModal report={report} onClose={() => setShowShare(false)} />}

      {/* Top bar */}
      <div style={{ position: 'sticky', top: 0, zIndex: 5, background: 'var(--bg)', padding: '22px 56px', borderBottom: '1px solid var(--rule)', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <button onClick={() => navigate('/insights')} className="btn-pill"><Icon.chevL /> Insights</button>
        <div className="meta">WEEKLY REPORT · 周报</div>
        <button className="btn" style={{ height: 36, padding: '0 14px' }} onClick={() => setShowShare(true)}>
          分享长图 <Icon.arrow />
        </button>
      </div>

      {/* Hero */}
      <div style={{ position: 'relative', overflow: 'hidden', padding: '80px 56px 100px', borderBottom: '1px solid var(--rule)' }}>
        <div className="mood-blob drift" style={{ width: 900, height: 900, right: -200, top: -200, opacity: 0.5 }} />
        <div style={{ position: 'relative', zIndex: 2, maxWidth: 920 }}>
          <div className="meta">
            A WEEKLY DISPATCH · {report.weekLabel ?? ''}{report.dateRange ? ` · ${report.dateRange}` : ''}
          </div>
          <h1 className="display" style={{ fontSize: 160, margin: '20px 0 0', lineHeight: 0.92 }}>
            {report.titleEn1 ?? 'A week of'}
          </h1>
          <h1 className="display" style={{ fontSize: 160, margin: 0, lineHeight: 0.92 }}>
            <em>{report.headlineWord ?? 'quiet'}</em> {report.headlineWord2 ?? 'light.'}
          </h1>
          <div className="display-cn" style={{ fontSize: 48, marginTop: 24, color: 'var(--ink-2)' }}>
            {report.titleCn ?? ''}
          </div>
          {report.summary && (
            <p style={{ marginTop: 28, fontSize: 18, lineHeight: 1.7, color: 'var(--ink-2)', maxWidth: 640, fontFamily: 'var(--serif-cn)' }}
              dangerouslySetInnerHTML={{ __html: report.summary }} />
          )}
        </div>
      </div>

      {/* Stats bar */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', borderBottom: '1px solid var(--rule)' }}>
        {[
          { n: stats.tracks ?? '—',        l: 'TRACKS',         cn: '首' },
          { n: stats.listenTime ?? '—',    l: 'LISTEN TIME',    cn: '收听时长' },
          { n: stats.newDiscovered ?? '—', l: 'NEW DISCOVERED', cn: '新发现' },
          { n: stats.moodMatch ?? '—',     l: 'MOOD MATCH',     cn: '匹配度' },
        ].map((s, i) => (
          <div key={i} style={{ padding: '40px 32px', borderRight: i < 3 ? '1px solid var(--rule)' : 'none' }}>
            <div className="meta">{s.l}</div>
            <div className="display" style={{ fontSize: 64, marginTop: 8 }}>{s.n}</div>
            <div style={{ fontFamily: 'var(--serif-cn)', fontSize: 13, color: 'var(--ink-2)', marginTop: 2 }}>{s.cn}</div>
          </div>
        ))}
      </div>

      {/* Chapter One: shape */}
      <div style={{ padding: '80px 56px', display: 'grid', gridTemplateColumns: '1fr 2fr', gap: 56, borderBottom: '1px solid var(--rule)' }}>
        <div>
          <div className="meta">CHAPTER ONE</div>
          <h2 className="display" style={{ fontSize: 56, margin: '8px 0 0', lineHeight: 1.05 }}>
            The week, in one <em>shape</em>.
          </h2>
          <div style={{ fontFamily: 'var(--serif-cn)', fontSize: 16, color: 'var(--ink-2)', marginTop: 6 }}>一周一形</div>
        </div>
        <div>
          <div style={{ display: 'flex', justifyContent: 'center' }}>
            <MoodBlob size={320} drift={true} geometry="blob" />
          </div>
          {report.essayBody && (
            <p style={{ marginTop: 24, fontSize: 17, lineHeight: 1.8, color: 'var(--ink-2)', fontFamily: 'var(--serif-cn)' }}>
              {report.essayBody}
            </p>
          )}
        </div>
      </div>

      {/* Pull quote */}
      {report.quote && (
        <div style={{ padding: '80px 56px', textAlign: 'center', borderBottom: '1px solid var(--rule)', background: 'var(--bg-2)' }}>
          <div className="display" style={{ fontSize: 48, fontStyle: 'italic', lineHeight: 1.3, maxWidth: 880, margin: '0 auto' }}>
            "{report.quote}"
          </div>
          <div className="meta" style={{ marginTop: 24, color: 'var(--ink-3)' }}>— AI · MOODFM EDITORIAL</div>
        </div>
      )}

      {/* Chapter Two: discoveries */}
      {discoveries.length > 0 && (
        <div style={{ padding: '72px 56px', borderBottom: '1px solid var(--rule)' }}>
          <div className="between" style={{ marginBottom: 24 }}>
            <div>
              <div className="meta">CHAPTER TWO · 本周新发现</div>
              <h2 className="display" style={{ fontSize: 56, margin: '8px 0 0' }}>
                {stats.newDiscovered ?? discoveries.length} <em>discoveries</em>.
              </h2>
            </div>
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 16 }}>
            {discoveries.slice(0, 4).map((tk, i) => (
              <DiscoveryCard key={i} track={tk} />
            ))}
          </div>
        </div>
      )}

      {/* Footer CTA */}
      <div style={{ padding: '72px 56px 80px', textAlign: 'center' }}>
        <div className="meta">SHARE · 分享</div>
        <h2 className="display" style={{ fontSize: 56, margin: '8px 0 16px' }}>
          A week worth <em>keeping</em>.
        </h2>
        <div style={{ fontFamily: 'var(--serif-cn)', fontSize: 15, color: 'var(--ink-2)', maxWidth: 480, margin: '0 auto 24px' }}>
          导出一张可分享的长图，把这一周的"形状"留在朋友圈。
        </div>
        <div className="row" style={{ gap: 10, justifyContent: 'center' }}>
          <button className="btn" onClick={() => setShowShare(true)}>生成分享长图 <Icon.arrow /></button>
        </div>
      </div>
    </div>
  );
}
