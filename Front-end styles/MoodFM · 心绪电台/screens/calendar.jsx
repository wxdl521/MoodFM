/* global React, ScreenFrame, MoodBlob, Icon, Logo */
// P15 · Mood Calendar — month view with mood cells

const MOOD_PALETTE = {
  calm:    ['#e9f0e6', '#9bb1a4'],
  dusk:    ['#f3d7c6', '#d28a9a'],
  blue:    ['#cbd6e8', '#6b7c9a'],
  energy:  ['#f8c87b', '#d96343'],
  focus:   ['#cfe1d8', '#6f9a87'],
  empty:   ['transparent', 'transparent'],
};

// Pre-seeded month data (35 cells)
function genMonth() {
  const rng = (seed) => { let s=seed; return ()=>{ s=(s*9301+49297)%233280; return s/233280; }; };
  const r = rng(31);
  const moods = ['calm','dusk','blue','energy','focus'];
  const today = 22;
  const out = [];
  for (let d=1; d<=31; d++) {
    const future = d > today;
    const skip = !future && r() < 0.06;
    out.push({
      day: d,
      future,
      empty: skip || future,
      mood: skip || future ? 'empty' : moods[Math.floor(r()*moods.length)],
      tracks: future||skip ? 0 : Math.floor(2 + r()*22),
      minutes: future||skip ? 0 : Math.floor(8 + r()*90),
      isToday: d === today,
    });
  }
  return out;
}

function MoodCalendar({ device='desktop', theme, mood, onBack }) {
  const isMobile = device === 'mobile';
  const W = isMobile ? 390 : 1440;
  const H = isMobile ? 844 : 900;
  const [days] = React.useState(genMonth);
  const [selected, setSelected] = React.useState(15);
  const [viewMode, setViewMode] = React.useState('mood');
  const sel = days[selected-1];

  // Pad to start on Sunday — May 1 2026 = Friday → 5 leading blanks
  const lead = 5;
  const cells = [...Array(lead).fill(null), ...days];
  while (cells.length % 7) cells.push(null);

  return (
    <ScreenFrame theme={theme} mood={mood} width={W} height={H} label="04 Calendar" scroll={isMobile}>
      {/* Top bar */}
      <div style={{ position:'sticky', top:0, zIndex:5, background:'var(--bg)', padding: isMobile?'14px 18px':'22px 56px', borderBottom:'1px solid var(--rule)', display:'flex', alignItems:'center', justifyContent:'space-between' }}>
        <button onClick={onBack} className="btn-pill"><Icon.chevL/> Home</button>
        {!isMobile && <div className="meta">SECTION V · MOOD CALENDAR · 心情日历</div>}
        <div className="row" style={{ gap: 6 }}>
          <button onClick={()=>setViewMode('mood')} className={'btn-pill' + (viewMode==='mood'?' active':'')} style={{ whiteSpace:'nowrap' }}>心情</button>
          <button onClick={()=>setViewMode('volume')} className={'btn-pill' + (viewMode==='volume'?' active':'')} style={{ whiteSpace:'nowrap' }}>时长</button>
        </div>
      </div>

      <div style={{ display: isMobile?'block':'grid', gridTemplateColumns: '1.4fr 1fr', gap: 0 }}>
        {/* LEFT — calendar grid */}
        <div style={{ padding: isMobile?'24px 18px':'40px 56px', borderRight: isMobile?'none':'1px solid var(--rule)' }}>
          <div className="meta">YOUR EMOTIONAL MONTH · 2026</div>
          <h1 className="display" style={{ fontSize: isMobile?60:120, margin: '8px 0 0', lineHeight: 0.95 }}>
            <em>May.</em>
          </h1>
          <div className="display-cn" style={{ fontSize: isMobile?20:28, marginTop: 4, color:'var(--ink-2)' }}>
            五月 · 五月让人想睡。
          </div>

          <div className="row" style={{ marginTop: 20, gap: 16, flexWrap:'wrap' }}>
            <button className="btn-pill"><Icon.chevL/></button>
            <div className="meta" style={{ minWidth: 120 }}>MAY 2026 · 五月</div>
            <button className="btn-pill">›</button>
          </div>

          {/* Weekday header */}
          <div style={{ display:'grid', gridTemplateColumns:'repeat(7, 1fr)', gap: isMobile?4:8, marginTop: 22 }}>
            {['SUN','MON','TUE','WED','THU','FRI','SAT'].map(d => (
              <div key={d} className="meta" style={{ textAlign:'center', color:'var(--ink-3)' }}>{d}</div>
            ))}
          </div>

          {/* Grid */}
          <div style={{ display:'grid', gridTemplateColumns:'repeat(7, 1fr)', gap: isMobile?4:8, marginTop: 8 }}>
            {cells.map((c, i) => (
              <DayCell key={i} cell={c} viewMode={viewMode} isSelected={c && c.day===selected} onClick={()=>c && !c.future && setSelected(c.day)} isMobile={isMobile}/>
            ))}
          </div>

          {/* Legend */}
          <div style={{ marginTop: 28 }}>
            <div className="meta" style={{ marginBottom: 10 }}>LEGEND · {viewMode==='mood'?'按心情上色':'按收听时长'}</div>
            {viewMode==='mood' ? (
              <div className="row" style={{ gap: 14, flexWrap:'wrap' }}>
                {[
                  {k:'calm', l:'平静'}, {k:'dusk', l:'温柔'}, {k:'blue', l:'忧郁'}, {k:'energy', l:'高能'}, {k:'focus', l:'专注'},
                ].map(m => (
                  <div key={m.k} className="row" style={{ gap: 8 }}>
                    <div style={{ width:14, height:14, borderRadius: 4, background: MOOD_PALETTE[m.k][0], border:`1.5px solid ${MOOD_PALETTE[m.k][1]}` }}/>
                    <span style={{ fontFamily:'var(--serif-cn)', fontSize: 13 }}>{m.l}</span>
                  </div>
                ))}
              </div>
            ) : (
              <div className="row" style={{ gap: 6 }}>
                <span className="meta">LESS</span>
                {[0.15, 0.35, 0.55, 0.75, 0.95].map((o, i) => (
                  <div key={i} style={{ width:18, height:18, borderRadius: 4, background:`rgba(33,30,28,${o})` }}/>
                ))}
                <span className="meta">MORE</span>
              </div>
            )}
          </div>
        </div>

        {/* RIGHT — selected day detail */}
        <div style={{ padding: isMobile?'28px 18px 80px':'40px 40px', background:'var(--bg-2)' }}>
          {sel && !sel.empty ? (
            <DayDetail day={sel} isMobile={isMobile}/>
          ) : (
            <div className="meta" style={{ textAlign:'center', padding: 60, color:'var(--ink-3)' }}>
              · NO DATA · 这一天没有听
            </div>
          )}
        </div>
      </div>
    </ScreenFrame>
  );
}

function DayCell({ cell, viewMode, isSelected, onClick, isMobile }) {
  if (!cell) return <div style={{ aspectRatio:'1', minHeight: isMobile?42:56 }}/>;
  const [bg, border] = MOOD_PALETTE[cell.mood] || ['transparent', 'transparent'];
  const intensity = Math.min(1, cell.minutes / 80);
  const showBg = viewMode === 'mood' ? bg : `rgba(33,30,28,${cell.empty?0:0.15 + intensity*0.7})`;
  const txt = viewMode === 'volume' && intensity > 0.6 ? 'var(--bg)' : 'var(--ink)';

  return (
    <button onClick={onClick} disabled={cell.future} style={{
      aspectRatio: '1',
      minHeight: isMobile?42:56,
      borderRadius: 6,
      background: showBg,
      border: cell.isToday ? '2px solid var(--ink)' : isSelected ? '2px solid var(--ink)' : viewMode==='mood' && !cell.empty ? `1.5px solid ${border}` : '1px solid var(--rule)',
      color: cell.empty ? 'var(--ink-3)' : txt,
      cursor: cell.future ? 'default' : 'pointer',
      opacity: cell.future ? 0.35 : 1,
      padding: 6,
      display:'flex', flexDirection:'column', justifyContent:'space-between', alignItems:'flex-start',
      fontFamily: 'var(--mono)',
      transition: 'transform .15s',
      transform: isSelected ? 'scale(1.06)' : 'scale(1)',
    }}>
      <span style={{ fontSize: isMobile?12:14, fontWeight: cell.isToday?700:500 }}>{cell.day}</span>
      {!isMobile && !cell.empty && <span style={{ fontSize: 9, opacity: 0.7 }}>{cell.tracks}♪</span>}
    </button>
  );
}

function DayDetail({ day, isMobile }) {
  const moodLabels = { calm:'平静 · CALM', dusk:'温柔 · TENDER', blue:'忧郁 · MELANCHOLY', energy:'高能 · ENERGETIC', focus:'专注 · FOCUSED' };
  const tracks = [
    { t:'weightless', a:'Marconi Union', m:'8:09' },
    { t:'晚安', a:'Mong Tong', m:'4:21' },
    { t:'Re: Stacks', a:'Bon Iver', m:'6:42' },
    { t:'夜空中最闪亮的星', a:'逃跑计划', m:'4:38' },
  ];
  return (
    <div>
      <div className="meta">SELECTED · 选中</div>
      <div className="row" style={{ marginTop: 10, alignItems:'baseline', gap: 14 }}>
        <h2 className="display" style={{ fontSize: 80, margin: 0 }}>{day.day}</h2>
        <div>
          <div style={{ fontFamily:'var(--serif-cn)', fontSize: 18 }}>五月 · 周{['日','一','二','三','四','五','六'][(day.day+4)%7]}</div>
          <div className="meta" style={{ marginTop: 4 }}>MAY {String(day.day).padStart(2,'0')} · 2026</div>
        </div>
      </div>

      {/* Mood blob preview */}
      <div style={{ marginTop: 20, padding: 22, background:'var(--paper)', borderRadius: 16, border:'1px solid var(--rule)' }}>
        <div className="row" style={{ gap: 16 }}>
          <div style={{
            width: 76, height: 76, borderRadius:'50%', flexShrink: 0,
            background: `radial-gradient(circle at 30% 30%, ${MOOD_PALETTE[day.mood][0]}, ${MOOD_PALETTE[day.mood][1]})`,
            boxShadow: '0 6px 24px rgba(0,0,0,0.06)',
          }}/>
          <div>
            <div className="meta">DOMINANT MOOD</div>
            <div className="serif-en" style={{ fontSize: 28, marginTop: 4 }}>{moodLabels[day.mood].split(' · ')[1]}</div>
            <div style={{ fontFamily:'var(--serif-cn)', fontSize: 16, color:'var(--ink-2)' }}>{moodLabels[day.mood].split(' · ')[0]}</div>
          </div>
        </div>

        <div className="row" style={{ marginTop: 18, paddingTop: 16, borderTop: '1px dashed var(--rule)', gap: 28 }}>
          <div><div className="meta">TRACKS</div><div className="mono" style={{ fontSize: 18, marginTop: 4 }}>{day.tracks}</div></div>
          <div><div className="meta">MINUTES</div><div className="mono" style={{ fontSize: 18, marginTop: 4 }}>{day.minutes}</div></div>
          <div><div className="meta">SESSIONS</div><div className="mono" style={{ fontSize: 18, marginTop: 4 }}>{Math.max(1, Math.floor(day.tracks/4))}</div></div>
        </div>
      </div>

      {/* Top tracks */}
      <div style={{ marginTop: 24 }}>
        <div className="meta" style={{ marginBottom: 12 }}>TOP TRACKS · 当天最常听</div>
        {tracks.map((t,i) => (
          <div key={i} className="row" style={{ gap: 12, padding: '10px 0', borderBottom: i<tracks.length-1?'1px solid var(--rule)':'none' }}>
            <span className="mono" style={{ color:'var(--ink-3)', width: 22 }}>0{i+1}</span>
            <div style={{ flex:1, minWidth: 0 }}>
              <div className="serif-en" style={{ fontSize: 18, fontStyle:'italic' }}>{t.t}</div>
              <div className="meta" style={{ marginTop: 2 }}>{t.a}</div>
            </div>
            <div className="mono" style={{ color:'var(--ink-3)' }}>{t.m}</div>
          </div>
        ))}
      </div>

      <button className="btn" style={{ marginTop: 24, width:'100%', height: 46 }}>重听这一天 <Icon.play/></button>
    </div>
  );
}

window.MoodCalendar = MoodCalendar;
