/* global React, ScreenFrame, MoodBlob, Icon */
// P7 · Player

function Player({ device = 'desktop', theme, mood, wheelGeometry, onBack }) {
  const isMobile = device === 'mobile';
  const W = isMobile ? 390 : 1440;
  const H = isMobile ? 844 : 900;
  const [playing, setPlaying] = React.useState(true);
  const [progress, setProgress] = React.useState(0.18);
  const [liked, setLiked] = React.useState(false);

  React.useEffect(() => {
    if (!playing) return;
    const t = setInterval(() => setProgress(p => (p + 0.001) % 1), 200);
    return () => clearInterval(t);
  }, [playing]);

  return (
    <ScreenFrame theme={theme} mood={mood} width={W} height={H} label="03 Player" scroll={isMobile}>
      {/* Full-bleed mood backdrop */}
      <div style={{
        position:'absolute', inset:0,
        background: `
          radial-gradient(circle at 20% 25%, var(--mood-a) 0%, transparent 55%),
          radial-gradient(circle at 80% 20%, var(--mood-b) 0%, transparent 55%),
          radial-gradient(circle at 70% 90%, var(--mood-c) 0%, transparent 60%),
          radial-gradient(circle at 10% 80%, var(--mood-d) 0%, transparent 65%)`,
        backgroundColor:'var(--mood-d)',
        filter:'saturate(1.1)',
      }}/>
      <div style={{ position:'absolute', inset:0, background:'rgba(0,0,0,0.18)' }}/>

      {/* Foreground panel */}
      <div style={{ position:'relative', zIndex:2, color:'#fff', height:'100%', display:'flex', flexDirection:'column' }}>
        {/* Top */}
        <div style={{
          padding: isMobile?'18px 22px':'24px 56px',
          display:'flex', alignItems:'center', justifyContent:'space-between',
          borderBottom: '1px solid rgba(255,255,255,0.12)',
        }}>
          <button onClick={onBack} className="row" style={{ background:'transparent', border:'none', color:'#fff', cursor:'pointer', gap: 8 }}>
            <Icon.chevL/> <span className="mono" style={{ fontSize:11, letterSpacing:'.16em' }}>BACK</span>
          </button>
          <div style={{ textAlign:'center' }}>
            <div className="mono" style={{ fontSize:10, letterSpacing:'.2em', opacity:.7 }}>STATION №07 · NOW PLAYING</div>
            <div style={{ fontFamily:'var(--serif-en)', fontStyle:'italic', fontSize: isMobile?20:26, marginTop:2 }}>Late-night Reset</div>
            <div style={{ fontSize: 13, opacity:.85 }}>加班疲惫电台 · 自动重排第 2 次</div>
          </div>
          <button className="row" style={{ background:'transparent', border:'none', color:'#fff', cursor:'pointer', gap: 8 }}>
            <Icon.more/>
          </button>
        </div>

        {/* Main content */}
        <div style={{
          flex:1, display:'grid',
          gridTemplateColumns: isMobile ? '1fr' : '1fr 1.1fr',
          gap: isMobile?28:48,
          padding: isMobile?'24px 22px':'48px 56px',
          alignItems:'center',
        }}>
          {/* Cover */}
          <div style={{ display:'flex', justifyContent:'center', position:'relative' }}>
            {/* Pulse rings */}
            <div style={{ position:'absolute', width: isMobile?320:480, height: isMobile?320:480, borderRadius: wheelGeometry==='square'?20:'50%', border:'1px solid rgba(255,255,255,0.18)', animation:'pulse-ring 4s ease-out infinite' }}/>
            <MoodBlob size={isMobile?300:440} drift={true} geometry={wheelGeometry==='square'?'square':wheelGeometry==='blob'?'blob':'circle'}/>
          </div>

          {/* Track info */}
          <div>
            <div className="mono" style={{ fontSize:10, letterSpacing:'.2em', opacity:.7 }}>TRACK 03 / 14 · NETEASE CLOUD</div>
            <h1 className="display" style={{ fontSize: isMobile?52:96, margin: '10px 0 0' }}>
              weightless<em style={{ opacity:.5 }}>.</em>
            </h1>
            <div style={{ fontFamily:'var(--serif-cn)', fontSize: isMobile?20:24, marginTop: 8, opacity: .9 }}>
              Marconi Union · 失重
            </div>

            {/* Why this song */}
            <div style={{ marginTop: 24, padding: 18, background:'rgba(255,255,255,0.08)', backdropFilter:'blur(20px)', borderRadius: 18, border:'1px solid rgba(255,255,255,0.14)' }}>
              <div className="mono" style={{ fontSize:10, letterSpacing:'.18em', opacity:.7 }}>AI · WHY THIS SONG</div>
              <div style={{ marginTop: 8, fontFamily:'var(--serif-cn)', fontSize: 15, lineHeight: 1.6 }}>
                你说"加班到很晚，需要一点不打扰我的电流声"——所以从你的红心歌单里挑了这首 60bpm 的环境音乐，让神经系统先慢下来。
              </div>
              <div className="meta" style={{ marginTop: 10, color:'rgba(255,255,255,0.7)' }}>· 低唤醒 · 环境 · 同步 BPM 低于 65</div>
            </div>

            {/* Progress */}
            <div style={{ marginTop: 28 }}>
              <div style={{ height: 2, background:'rgba(255,255,255,0.18)', borderRadius:1, position:'relative' }}>
                <div style={{ position:'absolute', left:0, top:0, bottom:0, width: `${progress*100}%`, background:'#fff' }}/>
                <div style={{ position:'absolute', left: `${progress*100}%`, top: -3, width: 8, height: 8, borderRadius:'50%', background:'#fff', transform:'translateX(-50%)' }}/>
              </div>
              <div className="mono between" style={{ fontSize:11, marginTop:8, opacity:.8 }}>
                <span>1:28</span><span>8:09</span>
              </div>
            </div>

            {/* Controls */}
            <div className="row" style={{ marginTop: 24, gap: 14, justifyContent: isMobile?'center':'flex-start' }}>
              <button style={ctrlBtn}><Icon.prev/></button>
              <button onClick={()=>setPlaying(p=>!p)} style={{ ...ctrlBtn, width:64, height:64, background:'#fff', color: 'var(--ink)' }}>
                {playing ? <Icon.pause/> : <Icon.play/>}
              </button>
              <button style={ctrlBtn}><Icon.next/></button>
              <button style={{ ...ctrlBtn, marginLeft: 12 }}><Icon.skip/></button>
            </div>

            {/* Secondary actions */}
            <div className="row" style={{ marginTop: 22, gap: 8, flexWrap:'wrap' }}>
              <ChipDark active={liked} onClick={()=>setLiked(x=>!x)}>
                <Icon.heart style={{ fill: liked?'var(--mood-a)':'none', stroke: liked?'var(--mood-a)':'currentColor' }}/>
                {liked? '已红心' : '红心'}
              </ChipDark>
              <ChipDark>不喜欢</ChipDark>
              <ChipDark>切换音源 · QQ</ChipDark>
              <ChipDark>歌词</ChipDark>
              <ChipDark>队列 · 11</ChipDark>
              {!isMobile && <ChipDark><Icon.share/> 分享</ChipDark>}
            </div>
          </div>
        </div>

        {/* Queue strip — desktop */}
        {!isMobile && (
          <div style={{
            padding: '18px 56px 24px',
            borderTop: '1px solid rgba(255,255,255,0.12)',
            display:'flex', gap: 16, alignItems:'center', overflowX:'auto'
          }}>
            <div className="mono" style={{ fontSize:10, letterSpacing:'.18em', opacity:.7, whiteSpace:'nowrap' }}>UP NEXT →</div>
            {[
              {n:'spiegel im spiegel', a:'Arvo Pärt', t:'9:24'},
              {n:'an ending (ascent)', a:'Brian Eno', t:'4:18'},
              {n:'avril 14th', a:'Aphex Twin', t:'2:05'},
              {n:'横超', a:'青葉市子', t:'4:12'},
              {n:'cirrus', a:'Bonobo', t:'5:48'},
            ].map((s,i)=>(
              <div key={i} className="row" style={{ gap: 10, padding:'8px 12px', border:'1px solid rgba(255,255,255,0.18)', borderRadius: 999, whiteSpace:'nowrap' }}>
                <span className="mono" style={{ fontSize:10, opacity:.7 }}>0{i+4}</span>
                <span style={{ fontFamily:'var(--serif-cn)', fontSize: 13 }}>{s.n}</span>
                <span style={{ fontSize: 12, opacity:.7 }}>· {s.a}</span>
                <span className="mono" style={{ fontSize:10, opacity:.6 }}>{s.t}</span>
              </div>
            ))}
          </div>
        )}
      </div>
    </ScreenFrame>
  );
}

const ctrlBtn = { width: 48, height: 48, borderRadius:'50%', background:'rgba(255,255,255,0.12)', border:'1px solid rgba(255,255,255,0.2)', color:'#fff', cursor:'pointer', display:'inline-flex', alignItems:'center', justifyContent:'center' };

function ChipDark({ children, active, onClick }) {
  return (
    <button onClick={onClick} style={{
      height: 34, padding: '0 14px', borderRadius: 999,
      border: '1px solid rgba(255,255,255,0.22)',
      background: active ? 'rgba(255,255,255,0.12)' : 'transparent',
      color:'#fff', cursor:'pointer',
      fontFamily:'var(--mono)', fontSize:11, letterSpacing:'.12em', textTransform:'uppercase',
      display:'inline-flex', alignItems:'center', gap:6,
    }}>{children}</button>
  );
}

window.Player = Player;
