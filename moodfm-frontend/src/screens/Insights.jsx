import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { MoodBlob, Icon, Logo } from '../components/atoms';
import { insightsApi } from '../api/insights';
import { reportsApi } from '../api/reports';

// ─── SVG Charts ───────────────────────────────────────────────

function MoodCurveSVG({ trend }) {
  const W = 1100, H = 220;

  if (!trend) {
    return (
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%', fontFamily: 'var(--serif-cn)', fontSize: 14, color: 'var(--ink-3)' }}>
        暂无数据
      </div>
    );
  }

  const { labels = [], userMood = [], songMood = [], lowPointIndex = -1 } = trend;
  const n = labels.length;
  const px = (i) => (i / Math.max(n - 1, 1)) * (W - 60) + 30;
  const py = (v) => H - 30 - (v ?? 0.5) * (H - 60);
  const path = (pts) => pts.map((v, i) => `${i === 0 ? 'M' : 'L'} ${px(i)} ${py(v)}`).join(' ');

  return (
    <svg viewBox={`0 0 ${W} ${H}`} width="100%" height="100%" style={{ overflow: 'visible' }}>
      {[0, 0.25, 0.5, 0.75, 1].map((v, i) => (
        <line key={i} x1="30" y1={py(v)} x2={W - 30} y2={py(v)} stroke="var(--rule)" strokeDasharray="2 4" />
      ))}
      {labels.map((d, i) => (
        <text key={d} x={px(i)} y={H - 8} textAnchor="middle"
          style={{ fontFamily: 'var(--mono)', fontSize: 10, letterSpacing: '.18em', fill: 'var(--ink-3)' }}>{d}</text>
      ))}
      {songMood.length > 0 && (
        <path d={path(songMood)} fill="none" stroke="var(--mood-c)" strokeWidth="1.5" strokeDasharray="4 4" />
      )}
      {userMood.length > 0 && (
        <>
          <path d={path(userMood)} fill="none" stroke="var(--mood-b)" strokeWidth="2" />
          {userMood.map((v, i) => (
            <circle key={i} cx={px(i)} cy={py(v)} r="3" fill="var(--mood-b)" />
          ))}
          {lowPointIndex >= 0 && lowPointIndex < userMood.length && (
            <>
              <circle cx={px(lowPointIndex)} cy={py(userMood[lowPointIndex])} r="7"
                fill="none" stroke="var(--mood-b)" opacity="0.6" />
              <text x={px(lowPointIndex)} y={py(userMood[lowPointIndex]) - 14}
                textAnchor="middle"
                style={{ fontFamily: 'var(--serif-en)', fontStyle: 'italic', fontSize: 13, fill: 'var(--ink)' }}>
                low point
              </text>
            </>
          )}
        </>
      )}
    </svg>
  );
}

function RadarSVG({ genres }) {
  const data = genres?.length
    ? genres
    : [
        { genre: 'Ambient',    genreCn: '环境', value: 0 },
        { genre: 'Classical',  genreCn: '古典', value: 0 },
        { genre: 'Folk',       genreCn: '民谣', value: 0 },
        { genre: 'Indie',      genreCn: '独立', value: 0 },
        { genre: 'Electronic', genreCn: '电子', value: 0 },
        { genre: 'Jazz',       genreCn: '爵士', value: 0 },
        { genre: 'Pop',        genreCn: '流行', value: 0 },
        { genre: 'Rock',       genreCn: '摇滚', value: 0 },
      ];

  const cx = 180, cy = 180, R = 130;
  const point = (i, r) => {
    const a = (i / data.length) * Math.PI * 2 - Math.PI / 2;
    return [cx + Math.cos(a) * r, cy + Math.sin(a) * r];
  };
  const poly = data.map((d, i) => point(i, R * (d.value ?? 0)).join(',')).join(' ');
  const empty = data.every(d => !d.value);

  return (
    <svg viewBox="0 0 360 360" width="100%" height="320" style={{ maxWidth: 360 }}>
      <defs>
        <radialGradient id="radarFill">
          <stop offset="0%" stopColor="var(--mood-a)" stopOpacity="0.6" />
          <stop offset="100%" stopColor="var(--mood-c)" stopOpacity="0.3" />
        </radialGradient>
      </defs>
      {[0.25, 0.5, 0.75, 1].map((s, i) => (
        <polygon key={i}
          points={data.map((_, j) => point(j, R * s).join(',')).join(' ')}
          fill="none" stroke="var(--rule)" />
      ))}
      {data.map((_, i) => {
        const [x, y] = point(i, R);
        return <line key={i} x1={cx} y1={cy} x2={x} y2={y} stroke="var(--rule)" />;
      })}
      {!empty && (
        <polygon points={poly} fill="url(#radarFill)" stroke="var(--mood-b)" strokeWidth="1.5" />
      )}
      {data.map((d, i) => {
        const [x, y] = point(i, R + 22);
        return (
          <g key={d.genre}>
            <text x={x} y={y} textAnchor="middle"
              style={{ fontFamily: 'var(--serif-en)', fontStyle: 'italic', fontSize: 13, fill: 'var(--ink)' }}>
              {d.genre}
            </text>
            <text x={x} y={y + 14} textAnchor="middle"
              style={{ fontFamily: 'var(--serif-cn)', fontSize: 10, fill: 'var(--ink-3)' }}>
              {d.genreCn}
            </text>
          </g>
        );
      })}
      {empty && (
        <text x={cx} y={cy + 5} textAnchor="middle"
          style={{ fontFamily: 'var(--serif-cn)', fontSize: 13, fill: 'var(--ink-3)' }}>
          暂无数据
        </text>
      )}
    </svg>
  );
}

// ─── Sub-sections ─────────────────────────────────────────────

function StatCards({ stats }) {
  const items = stats
    ? [
        { l: '本周听歌时长', v: stats.totalListeningTime ?? '—',  en: 'TOTAL · LISTENING', d: stats.listeningVsLast ?? '' },
        { l: '最常听的流派', v: stats.topGenre ?? '—',            en: 'TOP · GENRE',       d: stats.topGenreRatio ?? '' },
        { l: 'AI 推荐准确率', v: stats.aiAccuracy ?? '—',         en: 'AI · ACCURACY',     d: '红心 ÷ 跳过' },
        { l: '心情主旋律',    v: stats.moodKey ?? '—',            en: 'MOOD · KEY',        d: stats.moodSub ?? '' },
      ]
    : Array(4).fill({ l: '—', v: '…', en: '· · ·', d: '' });

  return (
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 1, marginTop: 40, background: 'var(--rule)', border: '1px solid var(--rule)' }}>
      {items.map((s, i) => (
        <div key={i} style={{ background: 'var(--bg)', padding: '24px 24px' }}>
          <div className="meta">{s.en}</div>
          <div className="display" style={{ fontSize: 60, marginTop: 8 }}>{s.v}</div>
          <div style={{ fontFamily: 'var(--serif-cn)', fontSize: 14, color: 'var(--ink-2)', marginTop: 4 }}>{s.l}</div>
          {s.d && <div className="meta" style={{ marginTop: 6, color: 'var(--ink-3)' }}>{s.d}</div>}
        </div>
      ))}
    </div>
  );
}

function TopArtists({ artists }) {
  if (!artists?.length) {
    return (
      <div style={{ marginTop: 20, padding: '32px 0', textAlign: 'center', fontFamily: 'var(--serif-cn)', fontSize: 14, color: 'var(--ink-3)' }}>
        暂无数据
      </div>
    );
  }

  const maxTime = artists[0]?.proportion ?? 1;

  return (
    <div style={{ marginTop: 20 }}>
      {artists.map((a, i) => (
        <div key={i} style={{ padding: '14px 0', borderBottom: '1px solid var(--rule)' }}>
          <div className="between">
            <div className="row" style={{ gap: 12 }}>
              <div className="display" style={{ fontSize: 28, color: 'var(--ink-3)', width: 32, flexShrink: 0 }}>
                {String(i + 1).padStart(2, '0')}
              </div>
              <div>
                <div style={{ fontFamily: 'var(--serif-cn)', fontSize: 17, fontWeight: 500 }}>{a.name}</div>
                <div className="meta" style={{ marginTop: 2 }}>{a.genre ?? a.tag ?? ''}</div>
              </div>
            </div>
            <div className="meta" style={{ color: 'var(--ink-2)', flexShrink: 0 }}>{a.totalTime ?? a.playTime ?? ''}</div>
          </div>
          <div style={{ marginTop: 10, height: 2, background: 'var(--rule)', position: 'relative' }}>
            <div style={{ position: 'absolute', left: 0, top: 0, bottom: 0, width: `${(a.proportion ?? 0) * 100}%`, background: 'linear-gradient(90deg, var(--mood-a), var(--mood-c))', transition: 'width 0.6s ease' }} />
          </div>
        </div>
      ))}
    </div>
  );
}

function TopSongs({ songs }) {
  if (!songs?.length) return null;

  return (
    <div style={{ marginTop: 48 }}>
      <div className="meta">FIG. 04 · TOP SONGS</div>
      <div className="display-cn" style={{ fontSize: 30, marginTop: 4 }}>本周 TOP 曲目</div>
      <div style={{ marginTop: 20 }}>
        {songs.map((s, i) => (
          <div key={i} style={{ padding: '12px 0', borderBottom: '1px solid var(--rule)' }}>
            <div className="between">
              <div className="row" style={{ gap: 12 }}>
                <div className="display" style={{ fontSize: 26, color: 'var(--ink-3)', width: 32, flexShrink: 0 }}>
                  {String(i + 1).padStart(2, '0')}
                </div>
                <div>
                  <div style={{ fontFamily: 'var(--serif-en)', fontStyle: 'italic', fontSize: 16 }}>{s.title}</div>
                  <div className="meta" style={{ marginTop: 2 }}>{s.artist}</div>
                </div>
              </div>
              <div className="meta" style={{ color: 'var(--ink-2)', flexShrink: 0 }}>{s.playCount ? `${s.playCount} 次` : ''}</div>
            </div>
            <div style={{ marginTop: 8, height: 2, background: 'var(--rule)', position: 'relative' }}>
              <div style={{ position: 'absolute', left: 0, top: 0, bottom: 0, width: `${(s.proportion ?? 0) * 100}%`, background: 'linear-gradient(90deg, var(--mood-b), var(--mood-c))', transition: 'width 0.6s ease' }} />
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

function AISummaryCard({ summary, navigate, onReportGenerated }) {
  const [generating, setGenerating] = useState(false);
  const [genError, setGenError]     = useState('');

  if (!summary) return null;

  const { essayTitle, essayBody, weeklyReportId } = summary;
  const hasReport = !!weeklyReportId;

  const handleGenerate = async () => {
    setGenerating(true);
    setGenError('');
    try {
      const res = await reportsApi.generate();
      if (res.code === 200 && res.data?.id) {
        onReportGenerated(res.data.id);
        navigate(`/weekly/${res.data.id}`);
      } else {
        setGenError(res.message || '生成失败，请稍后重试');
      }
    } catch {
      setGenError('网络错误，请重试');
    } finally {
      setGenerating(false);
    }
  };

  return (
    <div style={{ marginTop: 56, position: 'relative', overflow: 'hidden', borderRadius: 24, padding: 40, color: '#fff' }}>
      <div style={{
        position: 'absolute', inset: 0,
        background: `
          radial-gradient(circle at 15% 20%, var(--mood-a) 0%, transparent 55%),
          radial-gradient(circle at 85% 30%, var(--mood-b) 0%, transparent 55%),
          radial-gradient(circle at 70% 90%, var(--mood-c) 0%, transparent 60%),
          radial-gradient(circle at 5%  80%, var(--mood-d) 0%, transparent 60%)`,
        backgroundColor: 'var(--mood-d)',
      }} />
      <div style={{ position: 'relative' }}>
        <div className="mono" style={{ fontSize: 10, letterSpacing: '.2em', opacity: .85 }}>AI · WEEKLY ESSAY · 周报节选</div>
        {hasReport ? (
          <>
            {essayTitle && (
              <h2 className="display" style={{ fontSize: 60, margin: '12px 0 16px', textShadow: '0 2px 30px rgba(0,0,0,0.2)' }}>
                {essayTitle}
              </h2>
            )}
            {essayBody && (
              <p style={{ fontFamily: 'var(--serif-cn)', fontSize: 18, lineHeight: 1.7, maxWidth: 720 }}>
                {essayBody}
              </p>
            )}
            <div className="row" style={{ marginTop: 24, gap: 10 }}>
              <button className="btn"
                onClick={() => navigate(`/weekly/${weeklyReportId}`)}
                style={{ background: '#fff', color: 'var(--ink)', borderColor: '#fff' }}>
                查看完整周报 →
              </button>
            </div>
          </>
        ) : (
          <>
            <h2 className="display" style={{ fontSize: 60, margin: '12px 0 16px', opacity: 0.8 }}>
              This week,<br/><em>untold.</em>
            </h2>
            <p style={{ fontFamily: 'var(--serif-cn)', fontSize: 16, lineHeight: 1.7, maxWidth: 560, opacity: 0.85 }}>
              AI 将分析你过去一周的收听记录，生成专属周报。
            </p>
            {genError && (
              <p style={{ fontFamily: 'var(--serif-cn)', fontSize: 13, marginTop: 8, opacity: 0.8 }}>{genError}</p>
            )}
            <div className="row" style={{ marginTop: 24, gap: 10 }}>
              <button className="btn"
                onClick={handleGenerate}
                disabled={generating}
                style={{ background: '#fff', color: 'var(--ink)', borderColor: '#fff', opacity: generating ? 0.7 : 1 }}>
                {generating ? 'AI 生成中…' : '生成本周周报 →'}
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  );
}

function PreviousReports({ reports, navigate }) {
  if (!reports?.length) return null;

  const moodPalettes = ['energetic', 'focused', 'melancholy', 'dusk', 'calm'];

  return (
    <div style={{ marginTop: 56 }}>
      <div className="meta">PREVIOUS ISSUES · 历史报告</div>
      <div className="display-cn" style={{ fontSize: 30, marginTop: 4, marginBottom: 20 }}>过往周报</div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 16 }}>
        {reports.map((r, i) => (
          <div key={r.id ?? i}
            onClick={() => r.id && navigate(`/weekly/${r.id}`)}
            style={{ padding: 14, border: '1px solid var(--rule)', borderRadius: 16, background: 'var(--paper)', cursor: r.id ? 'pointer' : 'default' }}>
            <div data-mood={r.mood ?? moodPalettes[i % moodPalettes.length]}>
              <MoodBlob size={240} drift={false} geometry="blob" />
            </div>
            <div className="meta" style={{ marginTop: 12 }}>WEEK {r.week} · {r.year ?? new Date().getFullYear()}</div>
            <div style={{ fontFamily: 'var(--serif-cn)', fontSize: 17, fontWeight: 500, marginTop: 4 }}>{r.title}</div>
          </div>
        ))}
      </div>
    </div>
  );
}

// ─── Main Page ─────────────────────────────────────────────────

export default function Insights() {
  const navigate = useNavigate();
  const [days, setDays] = useState(7);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [summary, setSummary] = useState(null);
  const [trend, setTrend] = useState(null);
  const [genres, setGenres] = useState(null);
  const [topItems, setTopItems] = useState(null);

  useEffect(() => {
    setLoading(true);
    setError('');

    Promise.all([
      insightsApi.getSummary(days),
      insightsApi.getMoodTrend(days),
      insightsApi.getGenreRadar(days),
      insightsApi.getTopItems(days),
    ])
      .then(([sumRes, trendRes, genreRes, topRes]) => {
        if (sumRes.code === 200)   setSummary(sumRes.data);
        if (trendRes.code === 200) setTrend(trendRes.data);
        if (genreRes.code === 200) setGenres(genreRes.data);
        if (topRes.code === 200)   setTopItems(topRes.data);
      })
      .catch(() => setError('数据加载失败，请刷新重试'))
      .finally(() => setLoading(false));
  }, [days]);

  const stats     = summary?.stats;
  const headline  = summary?.headline  ?? '—';
  const headlineAlt = summary?.headlineAlt ?? '—';
  const headlineCn  = summary?.headlineCn  ?? '';
  const weekLabel   = stats?.weekLabel   ?? '';
  const weekRange   = stats?.weekRange   ?? '';

  return (
    <div data-mood="dusk" className="mfm" style={{ minHeight: '100vh' }}>
      <div className="mood-blob drift" style={{ width: 680, height: 680, left: -180, top: -200, opacity: 0.35 }} />

      {/* Sticky top bar */}
      <div style={{
        position: 'sticky', top: 0, zIndex: 5,
        background: 'var(--bg)', padding: '22px 56px',
        borderBottom: '1px solid var(--rule)',
        display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 10,
      }}>
        <div className="row" style={{ gap: 10 }}>
          <button onClick={() => navigate('/home')} className="btn-pill"><Icon.chevL /> Home</button>
          <div className="meta" style={{ marginLeft: 8 }}>SECTION IV · INSIGHTS</div>
        </div>
        <div className="row" style={{ gap: 6 }}>
          <button onClick={() => navigate('/calendar')} className="btn-pill">日历 →</button>
          <div style={{ width: 1, height: 18, background: 'var(--rule)', margin: '0 2px' }} />
          {[7, 30, 90].map(d => (
            <button key={d} onClick={() => setDays(d)} className={`btn-pill${days === d ? ' active' : ''}`}>
              {d}天
            </button>
          ))}
        </div>
      </div>

      <div style={{ position: 'relative', zIndex: 2, padding: '40px 56px 100px' }}>

        {/* Error */}
        {error && (
          <div style={{ padding: '14px 20px', background: 'rgba(232,90,138,0.08)', border: '1px solid var(--mood-b)', borderRadius: 12, fontFamily: 'var(--serif-cn)', fontSize: 14, color: 'var(--mood-b)', marginBottom: 32 }}>
            {error}
          </div>
        )}

        {/* Editorial heading */}
        <div className="meta">YOUR EMOTIONAL ATLAS · 个人情绪图鉴{weekLabel ? ` · ${weekLabel}` : ''}</div>
        <h1 className="display" style={{ fontSize: 144, margin: '12px 0 0' }}>You felt</h1>
        <h1 className="display" style={{ fontSize: 144, margin: 0 }}>
          <em style={{ background: 'linear-gradient(120deg, var(--mood-a), var(--mood-b), var(--mood-c))', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent', backgroundClip: 'text' }}>
            {headline}
          </em>
          <span style={{ color: 'var(--ink)' }}> &amp; </span>
          <em style={{ background: 'linear-gradient(120deg, var(--mood-c), var(--mood-d))', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent', backgroundClip: 'text' }}>
            {headlineAlt}
          </em>.
        </h1>
        {headlineCn && (
          <div className="display-cn" style={{ fontSize: 32, marginTop: 16, color: 'var(--ink-2)' }}>
            这{days === 7 ? '一周' : days === 30 ? '个月' : '三个月'}，你的心情主旋律是
            <span style={{ color: 'var(--ink)' }}>「{headlineCn}」</span>。
          </div>
        )}

        {/* Stat cards */}
        <StatCards stats={stats} />

        {/* FIG.01 Mood curve */}
        <div style={{ marginTop: 56 }}>
          <div className="between">
            <div>
              <div className="meta">FIG. 01 · MOOD CURVE</div>
              <div className="display-cn" style={{ fontSize: 30, marginTop: 4 }}>心情曲线 vs 听歌情绪</div>
            </div>
            {weekRange && <div className="meta">{weekRange}</div>}
          </div>
          <div style={{ marginTop: 20, height: 280, position: 'relative', border: '1px solid var(--rule)', borderRadius: 18, padding: 24, background: 'var(--paper)' }}>
            {loading
              ? <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%', fontFamily: 'var(--serif-cn)', fontSize: 14, color: 'var(--ink-3)' }}>加载中…</div>
              : <MoodCurveSVG trend={trend} />
            }
          </div>
          <div className="row" style={{ gap: 18, marginTop: 12, fontFamily: 'var(--serif-cn)', fontSize: 13, color: 'var(--ink-2)' }}>
            <div className="row" style={{ gap: 6 }}>
              <span style={{ width: 18, height: 2, background: 'var(--mood-b)', display: 'inline-block' }} />
              用户心情
            </div>
            <div className="row" style={{ gap: 6 }}>
              <span style={{ width: 18, height: 0, borderTop: '2px dashed var(--mood-c)', display: 'inline-block' }} />
              听歌情绪
            </div>
          </div>
        </div>

        {/* FIG.02 + FIG.03 two columns */}
        <div style={{ marginTop: 56, display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 48 }}>
          {/* Genre radar */}
          <div>
            <div className="meta">FIG. 02 · GENRE</div>
            <div className="display-cn" style={{ fontSize: 30, marginTop: 4 }}>流派分布</div>
            <div style={{ marginTop: 20, display: 'flex', justifyContent: 'center' }}>
              {loading
                ? <div style={{ width: 320, height: 320, display: 'flex', alignItems: 'center', justifyContent: 'center', fontFamily: 'var(--serif-cn)', fontSize: 14, color: 'var(--ink-3)' }}>加载中…</div>
                : <RadarSVG genres={genres} />
              }
            </div>
          </div>

          {/* Top artists */}
          <div>
            <div className="meta">FIG. 03 · TOP ARTISTS</div>
            <div className="display-cn" style={{ fontSize: 30, marginTop: 4 }}>
              {days === 7 ? '本周' : days === 30 ? '本月' : '近三月'} TOP 艺人
            </div>
            {loading
              ? <div style={{ marginTop: 20, fontFamily: 'var(--serif-cn)', fontSize: 14, color: 'var(--ink-3)' }}>加载中…</div>
              : <TopArtists artists={topItems?.artists} />
            }
          </div>
        </div>

        {/* Top songs */}
        {!loading && topItems?.songs?.length > 0 && (
          <TopSongs songs={topItems.songs} />
        )}

        {/* AI Summary */}
        {!loading && (
          <AISummaryCard
            summary={summary}
            navigate={navigate}
            onReportGenerated={(id) => setSummary(s => s ? { ...s, weeklyReportId: id } : s)}
          />
        )}

        {/* Previous reports */}
        {!loading && (
          <PreviousReports reports={summary?.previousReports} navigate={navigate} />
        )}
      </div>
    </div>
  );
}
