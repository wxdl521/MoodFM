import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Icon, Logo } from '../components/atoms';
import { insightsApi } from '../api/insights';

// ─── Constants ────────────────────────────────────────────────

const MOOD_PALETTE = {
  calm:   ['#e9f0e6', '#9bb1a4'],
  dusk:   ['#f3d7c6', '#d28a9a'],
  blue:   ['#cbd6e8', '#6b7c9a'],
  energy: ['#f8c87b', '#d96343'],
  focus:  ['#cfe1d8', '#6f9a87'],
  empty:  ['transparent', 'transparent'],
};
const MOOD_LABELS = { calm:'平静', dusk:'温柔', blue:'忧郁', energy:'高能', focus:'专注' };
const WEEKDAYS = ['SUN','MON','TUE','WED','THU','FRI','SAT'];
const CN_WEEKDAYS = ['日','一','二','三','四','五','六'];

// ─── Day Cell ────────────────────────────────────────────────

function DayCell({ cell, viewMode, isSelected, onClick, today }) {
  if (!cell) return <div style={{ aspectRatio: '1', minHeight: 56 }} />;

  const [bg, border] = MOOD_PALETTE[cell.mood] || ['transparent','transparent'];
  const intensity = Math.min(1, (cell.minutes ?? 0) / 80);
  const isFuture = cell.future;
  const isEmpty  = cell.empty || isFuture;

  const showBg = viewMode === 'mood'
    ? (isEmpty ? 'transparent' : bg)
    : (isEmpty ? 'transparent' : `rgba(26,23,20,${0.1 + intensity * 0.75})`);
  const isToday = cell.day === today;
  const borderStyle = isToday || isSelected
    ? '2px solid var(--ink)'
    : viewMode === 'mood' && !isEmpty
    ? `1.5px solid ${border}`
    : '1px solid var(--rule)';
  const txtColor = viewMode === 'volume' && intensity > 0.6 ? 'var(--bg)' : 'var(--ink)';

  return (
    <button
      onClick={() => !isFuture && !isEmpty && onClick(cell.day)}
      disabled={isFuture}
      style={{
        aspectRatio: '1', minHeight: 56,
        borderRadius: 6,
        background: showBg,
        border: borderStyle,
        color: isEmpty ? 'var(--ink-3)' : txtColor,
        cursor: isFuture ? 'default' : isEmpty ? 'default' : 'pointer',
        opacity: isFuture ? 0.3 : 1,
        padding: 6,
        display: 'flex', flexDirection: 'column', justifyContent: 'space-between', alignItems: 'flex-start',
        fontFamily: 'var(--mono)',
        transition: 'transform .12s',
        transform: isSelected ? 'scale(1.06)' : 'scale(1)',
      }}
    >
      <span style={{ fontSize: 13, fontWeight: isToday ? 700 : 400 }}>{cell.day}</span>
      {!isEmpty && <span style={{ fontSize: 9, opacity: 0.65 }}>{cell.tracks ?? 0}♪</span>}
    </button>
  );
}

// ─── Day Detail Panel ────────────────────────────────────────

function DayDetail({ calCell, detail, loading, year, month, onReplay }) {
  const cell = detail ?? calCell;
  if (!cell) {
    return (
      <div className="meta" style={{ textAlign: 'center', padding: 60, color: 'var(--ink-3)' }}>
        · NO DATA · 这一天没有收听记录
      </div>
    );
  }

  const [moodBg, moodBorder] = MOOD_PALETTE[cell.mood] || ['var(--bg-2)', 'var(--rule)'];
  const moodLabel = MOOD_LABELS[cell.mood] ?? cell.mood ?? '—';
  const moodEn = (cell.mood ?? '').toUpperCase();
  const weekday = CN_WEEKDAYS[new Date(year, month - 1, cell.day).getDay()];
  const topTracks = detail?.topTracks ?? [];

  return (
    <div>
      <div className="meta">SELECTED · 选中</div>
      <div className="row" style={{ marginTop: 10, alignItems: 'baseline', gap: 14 }}>
        <h2 className="display" style={{ fontSize: 80, margin: 0 }}>{String(cell.day).padStart(2,'0')}</h2>
        <div>
          <div style={{ fontFamily: 'var(--serif-cn)', fontSize: 18 }}>
            {new Date(year, month - 1).toLocaleString('zh-CN', { month: 'long' })} · 周{weekday}
          </div>
          <div className="meta" style={{ marginTop: 4 }}>
            {new Date(year, month - 1).toLocaleString('en-US', { month: 'short' }).toUpperCase()} {String(cell.day).padStart(2,'0')} · {year}
          </div>
        </div>
      </div>

      {/* Mood card */}
      <div style={{ marginTop: 20, padding: 22, background: 'var(--paper)', borderRadius: 16, border: '1px solid var(--rule)' }}>
        <div className="row" style={{ gap: 16 }}>
          <div style={{
            width: 76, height: 76, borderRadius: '50%', flexShrink: 0,
            background: `radial-gradient(circle at 30% 30%, ${moodBg}, ${moodBorder})`,
            boxShadow: '0 6px 24px rgba(0,0,0,0.06)',
          }} />
          <div>
            <div className="meta">DOMINANT MOOD</div>
            <div style={{ fontFamily: 'var(--serif-en)', fontStyle: 'italic', fontSize: 28, marginTop: 4 }}>{moodEn}</div>
            <div style={{ fontFamily: 'var(--serif-cn)', fontSize: 16, color: 'var(--ink-2)' }}>{moodLabel}</div>
          </div>
        </div>
        <div className="row" style={{ marginTop: 18, paddingTop: 16, borderTop: '1px dashed var(--rule)', gap: 28 }}>
          <div><div className="meta">TRACKS</div><div className="mono" style={{ fontSize: 18, marginTop: 4 }}>{cell.tracks ?? 0}</div></div>
          <div><div className="meta">MINUTES</div><div className="mono" style={{ fontSize: 18, marginTop: 4 }}>{cell.minutes ?? 0}</div></div>
          <div><div className="meta">SESSIONS</div><div className="mono" style={{ fontSize: 18, marginTop: 4 }}>{cell.sessions ?? Math.max(1, Math.floor((cell.tracks ?? 0) / 4))}</div></div>
        </div>
      </div>

      {/* Top tracks */}
      {loading && (
        <div className="meta" style={{ marginTop: 20, color: 'var(--ink-3)' }}>加载曲目中…</div>
      )}
      {topTracks.length > 0 && (
        <div style={{ marginTop: 24 }}>
          <div className="meta" style={{ marginBottom: 12 }}>TOP TRACKS · 当天最常听</div>
          {topTracks.map((t, i) => (
            <div key={i} className="row" style={{ gap: 12, padding: '10px 0', borderBottom: i < topTracks.length - 1 ? '1px solid var(--rule)' : 'none' }}>
              <span className="mono" style={{ color: 'var(--ink-3)', width: 22, flexShrink: 0 }}>{String(i + 1).padStart(2, '0')}</span>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontFamily: 'var(--serif-en)', fontStyle: 'italic', fontSize: 17, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{t.title}</div>
                <div className="meta" style={{ marginTop: 2 }}>{t.artist}</div>
              </div>
              <div className="mono" style={{ color: 'var(--ink-3)', flexShrink: 0 }}>{t.duration ?? t.durationFormatted ?? ''}</div>
            </div>
          ))}
        </div>
      )}

      <button className="btn" style={{ marginTop: 24, width: '100%', height: 46 }} onClick={onReplay}>
        重听这一天 <Icon.play />
      </button>
    </div>
  );
}

// ─── Main Page ────────────────────────────────────────────────

export default function Calendar() {
  const navigate = useNavigate();
  const now = new Date();

  const [year, setYear]   = useState(now.getFullYear());
  const [month, setMonth] = useState(now.getMonth() + 1);
  const [viewMode, setViewMode] = useState('mood');
  const [calData, setCalData]   = useState(null);
  const [loading, setLoading]   = useState(true);
  const [error, setError]       = useState('');

  const [selectedDay, setSelectedDay]       = useState(now.getDate());
  const [dayDetail, setDayDetail]           = useState(null);
  const [detailLoading, setDetailLoading]   = useState(false);

  // Fetch month data
  useEffect(() => {
    setLoading(true);
    setError('');
    setCalData(null);
    insightsApi.getCalendar(year, month)
      .then(res => {
        if (res.code === 200) setCalData(res.data);
        else setError(res.message || '加载失败');
      })
      .catch(() => setError('网络错误，请刷新'))
      .finally(() => setLoading(false));
  }, [year, month]);

  // Fetch day detail when selection changes
  useEffect(() => {
    if (!selectedDay) return;
    const date = `${year}-${String(month).padStart(2,'0')}-${String(selectedDay).padStart(2,'0')}`;
    setDetailLoading(true);
    setDayDetail(null);
    insightsApi.getDayDetail(date)
      .then(res => { if (res.code === 200) setDayDetail(res.data); })
      .catch(() => {})
      .finally(() => setDetailLoading(false));
  }, [selectedDay, year, month]);

  const navigate_month = (delta) => {
    let m = month + delta, y = year;
    if (m > 12) { m = 1;  y++; }
    if (m < 1)  { m = 12; y--; }
    setMonth(m); setYear(y); setSelectedDay(null); setDayDetail(null);
  };

  // Build grid cells
  const days = calData?.days ?? [];
  const firstDOW = calData?.firstDayOfWeek ?? new Date(year, month - 1, 1).getDay();
  const daysInMonth = calData?.daysInMonth ?? new Date(year, month, 0).getDate();
  const today = (year === now.getFullYear() && month === now.getMonth() + 1) ? now.getDate() : -1;

  const dayMap = {};
  days.forEach(d => { dayMap[d.day] = d; });

  const cells = [
    ...Array(firstDOW).fill(null),
    ...Array.from({ length: daysInMonth }, (_, i) => {
      const d = i + 1;
      return dayMap[d] ?? {
        day: d,
        mood: 'empty',
        tracks: 0,
        minutes: 0,
        future: new Date(year, month - 1, d) > now,
        empty: true,
      };
    }),
  ];
  while (cells.length % 7) cells.push(null);

  const monthName   = new Date(year, month - 1).toLocaleString('en-US', { month: 'long' });
  const monthNameCn = new Date(year, month - 1).toLocaleString('zh-CN', { month: 'long' });
  const selectedCalCell = selectedDay ? (dayMap[selectedDay] ?? null) : null;

  return (
    <div data-mood="dusk" className="mfm" style={{ minHeight: '100vh' }}>
      {/* Top bar */}
      <div style={{ position: 'sticky', top: 0, zIndex: 5, background: 'var(--bg)', padding: '22px 56px', borderBottom: '1px solid var(--rule)', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <div className="row" style={{ gap: 10 }}>
          <button onClick={() => navigate('/insights')} className="btn-pill"><Icon.chevL /> Insights</button>
          <div className="meta" style={{ marginLeft: 8 }}>SECTION V · MOOD CALENDAR · 心情日历</div>
        </div>
        <div className="row" style={{ gap: 6 }}>
          <button onClick={() => setViewMode('mood')}   className={`btn-pill${viewMode === 'mood'   ? ' active' : ''}`}>心情</button>
          <button onClick={() => setViewMode('volume')} className={`btn-pill${viewMode === 'volume' ? ' active' : ''}`}>时长</button>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1.4fr 1fr' }}>
        {/* LEFT: calendar */}
        <div style={{ padding: '40px 56px', borderRight: '1px solid var(--rule)' }}>
          <div className="meta">YOUR EMOTIONAL MONTH · {year}</div>
          <h1 className="display" style={{ fontSize: 120, margin: '8px 0 0', lineHeight: 0.95 }}>
            <em>{monthName}.</em>
          </h1>
          <div className="display-cn" style={{ fontSize: 26, marginTop: 4, color: 'var(--ink-2)' }}>
            {monthNameCn}
          </div>

          {/* Month nav */}
          <div className="row" style={{ marginTop: 20, gap: 12 }}>
            <button className="btn-pill" onClick={() => navigate_month(-1)}><Icon.chevL /></button>
            <div className="meta" style={{ minWidth: 160 }}>
              {monthName.toUpperCase()} {year} · {monthNameCn}
            </div>
            <button className="btn-pill" onClick={() => navigate_month(1)}>›</button>
          </div>

          {error && <div style={{ margin: '16px 0', fontFamily: 'var(--serif-cn)', fontSize: 13, color: 'var(--mood-b)' }}>{error}</div>}

          {/* Weekday header */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', gap: 8, marginTop: 22 }}>
            {WEEKDAYS.map(d => <div key={d} className="meta" style={{ textAlign: 'center', color: 'var(--ink-3)' }}>{d}</div>)}
          </div>

          {/* Grid */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', gap: 8, marginTop: 8 }}>
            {loading
              ? Array(35).fill(null).map((_, i) => (
                <div key={i} style={{ aspectRatio: '1', minHeight: 56, borderRadius: 6, background: 'var(--rule)', opacity: 0.4 }} />
              ))
              : cells.map((c, i) => (
                <DayCell key={i} cell={c} viewMode={viewMode}
                  isSelected={c?.day === selectedDay}
                  today={today}
                  onClick={setSelectedDay} />
              ))
            }
          </div>

          {/* Legend */}
          <div style={{ marginTop: 28 }}>
            <div className="meta" style={{ marginBottom: 10 }}>
              LEGEND · {viewMode === 'mood' ? '按心情上色' : '按收听时长'}
            </div>
            {viewMode === 'mood' ? (
              <div className="row" style={{ gap: 14, flexWrap: 'wrap' }}>
                {Object.entries(MOOD_LABELS).map(([k, l]) => (
                  <div key={k} className="row" style={{ gap: 8 }}>
                    <div style={{ width: 14, height: 14, borderRadius: 4, background: MOOD_PALETTE[k][0], border: `1.5px solid ${MOOD_PALETTE[k][1]}` }} />
                    <span style={{ fontFamily: 'var(--serif-cn)', fontSize: 13 }}>{l}</span>
                  </div>
                ))}
              </div>
            ) : (
              <div className="row" style={{ gap: 6 }}>
                <span className="meta">LESS</span>
                {[0.15, 0.35, 0.55, 0.75, 0.95].map((o, i) => (
                  <div key={i} style={{ width: 18, height: 18, borderRadius: 4, background: `rgba(26,23,20,${o})` }} />
                ))}
                <span className="meta">MORE</span>
              </div>
            )}
          </div>
        </div>

        {/* RIGHT: day detail */}
        <div style={{ padding: '40px 40px', background: 'var(--bg-2)', minHeight: 'calc(100vh - 73px)' }}>
          {selectedDay
            ? <DayDetail
                calCell={selectedCalCell}
                detail={dayDetail}
                loading={detailLoading}
                year={year}
                month={month}
                onReplay={() => navigate('/home')}
              />
            : <div className="meta" style={{ textAlign: 'center', padding: 60, color: 'var(--ink-3)' }}>
                · 点击左侧日期查看详情
              </div>
          }
        </div>
      </div>
    </div>
  );
}
