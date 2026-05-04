/* global React, ScreenFrame, MoodBlob, Icon, Logo */
// P10 · Playlist Detail

function PlaylistDetail({ device='desktop', theme, mood, onBack, onPlay }) {
  const isMobile = device === 'mobile';
  const W = isMobile ? 390 : 1440;
  const H = isMobile ? 844 : 900;

  const tracks = [
    { t:'weightless', a:'Marconi Union', al:'Ambient Transmissions', m:'8:09', mood:'CALM', liked:true },
    { t:'晚安', a:'Mong Tong', al:'桃源乡', m:'4:21', mood:'TENDER', liked:true },
    { t:'Re: Stacks', a:'Bon Iver', al:'For Emma, Forever Ago', m:'6:42', mood:'BLUE' },
    { t:'Petrichor', a:'Ólafur Arnalds', al:'re:member', m:'5:18', mood:'FOCUS', liked:true },
    { t:'Ferry', a:'Cigarettes After Sex', al:'X\'s', m:'4:55', mood:'BLUE' },
    { t:'Saman', a:'Ólafur Arnalds', al:'Island Songs', m:'5:23', mood:'CALM' },
    { t:'夜空中最闪亮的星', a:'逃跑计划', al:'世界', m:'4:38', mood:'TENDER', liked:true },
    { t:'Holocene', a:'Bon Iver', al:'Bon Iver, Bon Iver', m:'5:36', mood:'BLUE' },
    { t:'秋意上心头', a:'丝芭乐队', al:'秋意上心头', m:'3:48', mood:'TENDER' },
    { t:'Lost in Yesterday', a:'Tame Impala', al:'The Slow Rush', m:'4:15', mood:'FOCUS' },
  ];

  return (
    <ScreenFrame theme={theme} mood={mood} width={W} height={H} label="04d Playlist" scroll={true}>
      {/* Top bar */}
      <div style={{ position:'sticky', top:0, zIndex:5, background:'var(--bg)', padding: isMobile?'14px 18px':'18px 56px', borderBottom:'1px solid var(--rule)', display:'flex', alignItems:'center', justifyContent:'space-between' }}>
        <button onClick={onBack} className="btn-pill"><Icon.chevL/> 返回</button>
        {!isMobile && <div className="meta">PLAYLIST · 歌单详情</div>}
        <div className="row" style={{ gap: 8 }}>
          <button className="btn-pill">···</button>
        </div>
      </div>

      {/* Hero */}
      <div style={{ position:'relative', overflow:'hidden', padding: isMobile?'28px 22px 32px':'56px 56px 48px', borderBottom:'1px solid var(--rule)' }}>
        <div className="mood-blob drift" style={{ width: isMobile?500:800, height: isMobile?500:800, right: isMobile?-220:-150, top: isMobile?-200:-220, opacity: 0.45 }}/>

        <div style={{ position:'relative', zIndex: 2, display:'grid', gridTemplateColumns: isMobile?'1fr':'320px 1fr', gap: isMobile?28:48, alignItems:'end' }}>
          {/* Cover */}
          <div style={{ display:'flex', justifyContent: isMobile?'center':'flex-start' }}>
            <div style={{ position:'relative', width: isMobile?220:300, height: isMobile?220:300, borderRadius: 18, overflow:'hidden', boxShadow: '0 24px 60px rgba(0,0,0,0.18)' }}>
              <MoodBlob size={isMobile?220:300} drift={true} geometry="blob"/>
              <div style={{ position:'absolute', left: 14, bottom: 12, color:'#fff', textShadow:'0 1px 4px rgba(0,0,0,0.4)' }}>
                <div className="mono" style={{ fontSize: 10, letterSpacing:'.2em', opacity: 0.9 }}>NIGHT FLOAT · 010</div>
              </div>
            </div>
          </div>

          {/* Meta */}
          <div>
            <div className="meta">CURATED FOR YOU · 为你编排</div>
            <h1 className="display" style={{ fontSize: isMobile?56:108, margin: '12px 0 0', lineHeight: 0.95 }}>
              Night <em>float</em>.
            </h1>
            <div className="display-cn" style={{ fontSize: isMobile?22:34, marginTop: 4, color:'var(--ink-2)' }}>
              夜浮 · 给加完班的你
            </div>
            <p style={{ marginTop: 16, fontSize: 14, lineHeight: 1.7, color:'var(--ink-2)', maxWidth: 520, fontFamily:'var(--serif-cn)' }}>
              一份漂在凌晨水面上的 ambient 合集——人声极少、低频温和、不会打扰任何思绪。AI 根据你过去 30 天 23:00 后的收听偏好编排。
            </p>

            <div className="row" style={{ marginTop: 20, gap: 24, color:'var(--ink-3)' }}>
              <div><div className="meta">TRACKS</div><div className="mono" style={{ fontSize: 14, color:'var(--ink)', marginTop: 2 }}>{tracks.length}</div></div>
              <div><div className="meta">DURATION</div><div className="mono" style={{ fontSize: 14, color:'var(--ink)', marginTop: 2 }}>52 MIN</div></div>
              <div><div className="meta">UPDATED</div><div className="mono" style={{ fontSize: 14, color:'var(--ink)', marginTop: 2 }}>每日 23:00</div></div>
              <div><div className="meta">DOMINANT</div><div className="mono" style={{ fontSize: 14, color:'var(--ink)', marginTop: 2 }}>CALM · BLUE</div></div>
            </div>

            <div className="row" style={{ marginTop: 24, gap: 10, flexWrap:'wrap' }}>
              <button className="btn" onClick={onPlay} style={{ height: 48, padding:'0 22px', fontSize: 14 }}>
                <Icon.play/> 播放 · PLAY ALL
              </button>
              <button className="btn-pill" style={{ height: 48 }}>♡ 收藏</button>
              <button className="btn-pill" style={{ height: 48 }}>↓ 下载</button>
              <button className="btn-pill" style={{ height: 48 }}>↗ 分享</button>
            </div>
          </div>
        </div>
      </div>

      {/* Track list */}
      <div style={{ padding: isMobile?'24px 18px 80px':'40px 56px 80px' }}>
        <div className="between" style={{ marginBottom: 16 }}>
          <div className="meta">TRACK LIST · 曲目</div>
          <div className="row" style={{ gap: 8 }}>
            <button className="btn-pill" style={{ whiteSpace:'nowrap' }}>排序 ↓</button>
            <button className="btn-pill" style={{ whiteSpace:'nowrap' }}>筛选</button>
          </div>
        </div>

        {/* Header row */}
        {!isMobile && (
          <div className="row" style={{ padding:'10px 16px', color:'var(--ink-3)', borderBottom:'1px solid var(--rule)' }}>
            <div className="meta" style={{ width: 32 }}>#</div>
            <div className="meta" style={{ flex: 2.5 }}>TITLE · 标题</div>
            <div className="meta" style={{ flex: 1.5 }}>ALBUM · 专辑</div>
            <div className="meta" style={{ width: 100 }}>MOOD</div>
            <div className="meta" style={{ width: 40 }}></div>
            <div className="meta" style={{ width: 60, textAlign:'right' }}>TIME</div>
          </div>
        )}

        {tracks.map((t,i)=>(
          <Track key={i} t={t} i={i} isMobile={isMobile} active={i===0}/>
        ))}
      </div>
    </ScreenFrame>
  );
}

function Track({ t, i, isMobile, active }) {
  const [hover, setHover] = React.useState(false);
  const [liked, setLiked] = React.useState(t.liked);

  if (isMobile) {
    return (
      <div className="row" style={{ gap: 12, padding:'12px 6px', borderBottom: '1px solid var(--rule)' }}>
        <span className="mono" style={{ color: active?'var(--ink)':'var(--ink-3)', width: 22, fontSize: 12 }}>
          {active ? '▸' : String(i+1).padStart(2,'0')}
        </span>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div className="serif-en" style={{ fontSize: 16, fontStyle:'italic', whiteSpace:'nowrap', overflow:'hidden', textOverflow:'ellipsis' }}>{t.t}</div>
          <div className="meta" style={{ marginTop: 2, whiteSpace:'nowrap', overflow:'hidden', textOverflow:'ellipsis' }}>{t.a} · {t.mood}</div>
        </div>
        <button onClick={()=>setLiked(!liked)} style={{ background:'none', border:'none', color: liked?'var(--mood-b)':'var(--ink-3)', cursor:'pointer', fontSize: 18, lineHeight: 1 }}>{liked?'♥':'♡'}</button>
        <div className="mono" style={{ color:'var(--ink-3)', fontSize: 12, width: 36, textAlign:'right' }}>{t.m}</div>
      </div>
    );
  }

  return (
    <div
      onMouseEnter={()=>setHover(true)}
      onMouseLeave={()=>setHover(false)}
      className="row"
      style={{
        padding: '12px 16px', borderRadius: 8,
        background: active ? 'var(--bg-2)' : hover ? 'var(--bg-2)' : 'transparent',
        cursor:'pointer',
      }}
    >
      <div style={{ width: 32, color: active?'var(--ink)':'var(--ink-3)', fontFamily:'var(--mono)', fontSize: 13 }}>
        {active ? '▸' : hover ? <Icon.play/> : String(i+1).padStart(2,'0')}
      </div>
      <div style={{ flex: 2.5, minWidth: 0 }}>
        <div className="serif-en" style={{ fontSize: 19, fontStyle:'italic', color: active?'var(--ink)':'var(--ink)' }}>{t.t}</div>
        <div className="meta" style={{ marginTop: 2 }}>{t.a}</div>
      </div>
      <div style={{ flex: 1.5, minWidth: 0, fontFamily:'var(--serif-cn)', fontSize: 13, color:'var(--ink-2)', whiteSpace:'nowrap', overflow:'hidden', textOverflow:'ellipsis' }}>{t.al}</div>
      <div style={{ width: 100 }}>
        <span className="meta" style={{ padding:'3px 8px', border:'1px solid var(--rule)', borderRadius: 999, color:'var(--ink-2)' }}>{t.mood}</span>
      </div>
      <div style={{ width: 40, textAlign:'center' }}>
        <button onClick={(e)=>{e.stopPropagation(); setLiked(!liked);}} style={{ background:'none', border:'none', color: liked?'var(--mood-b)':'var(--ink-3)', cursor:'pointer', fontSize: 18, lineHeight: 1 }}>{liked?'♥':'♡'}</button>
      </div>
      <div style={{ width: 60, textAlign:'right', fontFamily:'var(--mono)', fontSize: 12, color:'var(--ink-3)' }}>{t.m}</div>
    </div>
  );
}

window.PlaylistDetail = PlaylistDetail;
