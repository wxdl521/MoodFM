/* global React, ScreenFrame, MoodBlob, Icon, Logo */
// P13 · Insights

function Insights({ device='desktop', theme, mood, onBack }) {
  const isMobile = device === 'mobile';
  const W = isMobile ? 390 : 1440;
  const H = isMobile ? 844 : 900;

  return (
    <ScreenFrame theme={theme} mood={mood} width={W} height={H} label="04 Insights" scroll={true}>
      <div className="mood-blob drift" style={{ width: isMobile?420:680, height: isMobile?420:680, left: isMobile?-200:-180, top: -200, opacity: 0.35 }}/>

      {/* Top */}
      <div style={{ position:'sticky', top:0, zIndex:5, background:'var(--bg)', padding: isMobile?'14px 18px':'22px 56px', borderBottom:'1px solid var(--rule)', display:'flex', justifyContent:'space-between', alignItems:'center', gap: 10 }}>
        <div className="row" style={{ gap: 10, minWidth: 0 }}>
          <button onClick={onBack} className="btn-pill" style={{ whiteSpace:'nowrap', flexShrink: 0 }}><Icon.chevL/> Home</button>
          {!isMobile && <div className="meta" style={{ marginLeft: 8 }}>SECTION IV · INSIGHTS</div>}
        </div>
        <div className="row" style={{ gap: 6, flexShrink: 0 }}>
          <button className="btn-pill active" style={{ whiteSpace:'nowrap' }}>7天</button>
          <button className="btn-pill" style={{ whiteSpace:'nowrap' }}>30天</button>
          {!isMobile && <button className="btn-pill" style={{ whiteSpace:'nowrap' }}>90天</button>}
        </div>
      </div>

      <div style={{ padding: isMobile?'24px 22px 80px':'40px 56px 100px' }}>
        {/* Editorial title */}
        <div className="meta">YOUR EMOTIONAL ATLAS · 个人情绪图鉴 · WEEK 18 / 2026</div>
        <h1 className="display" style={{ fontSize: isMobile?64:144, margin:'12px 0 0' }}>
          You felt
        </h1>
        <h1 className="display" style={{ fontSize: isMobile?64:144, margin:0, color:'var(--ink-3)' }}>
          <em style={{ background:'linear-gradient(120deg, var(--mood-a), var(--mood-b), var(--mood-c))', WebkitBackgroundClip:'text', color:'transparent' }}>tender</em><span style={{ color:'var(--ink)' }}> &amp; </span><em style={{ background:'linear-gradient(120deg, var(--mood-c), var(--mood-d))', WebkitBackgroundClip:'text', color:'transparent' }}>awake</em>.
        </h1>
        <div className="display-cn" style={{ fontSize: isMobile?22:32, marginTop:16, color:'var(--ink-2)' }}>
          这一周，你的心情主旋律是<span style={{ color:'var(--ink)' }}>「温柔的清醒」</span>。
        </div>

        {/* Stat cards */}
        <div style={{ display:'grid', gridTemplateColumns: isMobile?'repeat(2, 1fr)':'repeat(4, 1fr)', gap: 1, marginTop: 40, background:'var(--rule)', border:'1px solid var(--rule)' }}>
          {[
            {l:'本周听歌时长', v:'14h 22m', en:'TOTAL · LISTENING', d:'+2h vs 上周'},
            {l:'最常听的流派', v:'Ambient', en:'TOP · GENRE', d:'37% · 环境'},
            {l:'AI 推荐准确率', v:'82%', en:'AI · ACCURACY', d:'红心 ÷ 跳过'},
            {l:'心情主旋律', v:'温柔', en:'MOOD · KEY', d:'· 中能量 · 正向'},
          ].map((s,i)=>(
            <div key={i} style={{ background:'var(--bg)', padding: isMobile?'18px 16px':'24px 24px' }}>
              <div className="meta">{s.en}</div>
              <div className="display" style={{ fontSize: isMobile?40:60, marginTop: 8 }}>{s.v}</div>
              <div style={{ fontFamily:'var(--serif-cn)', fontSize: 14, color:'var(--ink-2)', marginTop: 4 }}>{s.l}</div>
              <div className="meta" style={{ marginTop: 6, color:'var(--ink-3)' }}>{s.d}</div>
            </div>
          ))}
        </div>

        {/* Mood curve */}
        <div style={{ marginTop: 56 }}>
          <div className="between">
            <div>
              <div className="meta">FIG. 01 · MOOD CURVE</div>
              <div className="display-cn" style={{ fontSize: isMobile?22:30, marginTop: 4 }}>心情曲线 vs 听歌情绪</div>
            </div>
            {!isMobile && <div className="meta">WEEK 18 · MAY 12 → MAY 18</div>}
          </div>
          <div style={{ marginTop: 20, height: isMobile?220:280, position:'relative', border:'1px solid var(--rule)', borderRadius: 18, padding: 24, background:'var(--paper)' }}>
            <MoodCurveSVG isMobile={isMobile}/>
          </div>
          <div className="row" style={{ gap: 18, marginTop: 12, fontFamily:'var(--serif-cn)', fontSize: 13, color:'var(--ink-2)' }}>
            <div className="row" style={{ gap: 6 }}><span style={{ width:18, height:2, background:'var(--mood-b)' }}/>用户心情</div>
            <div className="row" style={{ gap: 6 }}><span style={{ width:18, height:0, borderTop:'2px dashed var(--mood-c)' }}/>听歌情绪</div>
          </div>
        </div>

        {/* Two columns: Genre radar + Top */}
        <div style={{ marginTop: 56, display:'grid', gridTemplateColumns: isMobile?'1fr':'1fr 1fr', gap: isMobile?40:48 }}>
          <div>
            <div className="meta">FIG. 02 · GENRE</div>
            <div className="display-cn" style={{ fontSize: isMobile?22:30, marginTop: 4 }}>流派分布</div>
            <div style={{ marginTop: 20, display:'flex', justifyContent:'center' }}>
              <RadarSVG/>
            </div>
          </div>
          <div>
            <div className="meta">FIG. 03 · TOP ARTISTS</div>
            <div className="display-cn" style={{ fontSize: isMobile?22:30, marginTop: 4 }}>本周 TOP 艺人</div>
            <div style={{ marginTop: 20 }}>
              {[
                {n:'Marconi Union', en:'AMBIENT · UK', t:'2h 12m', p:1},
                {n:'青葉市子', en:'FOLK · JP', t:'1h 48m', p:0.78},
                {n:'Arvo Pärt', en:'CLASSICAL · EE', t:'1h 22m', p:0.6},
                {n:'Bonobo', en:'DOWNTEMPO · UK', t:'58m', p:0.42},
                {n:'雷光夏', en:'INDIE · TW', t:'42m', p:0.3},
              ].map((a,i)=>(
                <div key={i} style={{ padding:'14px 0', borderBottom: '1px solid var(--rule)' }}>
                  <div className="between">
                    <div className="row" style={{ gap: 12 }}>
                      <div className="display" style={{ fontSize: 28, color:'var(--ink-3)', width: 32 }}>0{i+1}</div>
                      <div>
                        <div style={{ fontFamily:'var(--serif-cn)', fontSize: 17, fontWeight: 500 }}>{a.n}</div>
                        <div className="meta" style={{ marginTop: 2 }}>{a.en}</div>
                      </div>
                    </div>
                    <div className="meta" style={{ color:'var(--ink-2)' }}>{a.t}</div>
                  </div>
                  <div style={{ marginTop: 10, height: 2, background:'var(--rule)', position:'relative' }}>
                    <div style={{ position:'absolute', left:0, top:0, bottom:0, width: `${a.p*100}%`, background:'linear-gradient(90deg, var(--mood-a), var(--mood-c))' }}/>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* AI Summary card */}
        <div style={{
          marginTop: 56,
          position:'relative', overflow:'hidden',
          borderRadius: 24, padding: isMobile?28:40,
          color: '#fff',
        }}>
          <div style={{
            position:'absolute', inset:0,
            background: `
              radial-gradient(circle at 15% 20%, var(--mood-a) 0%, transparent 55%),
              radial-gradient(circle at 85% 30%, var(--mood-b) 0%, transparent 55%),
              radial-gradient(circle at 70% 90%, var(--mood-c) 0%, transparent 60%),
              radial-gradient(circle at 5% 80%, var(--mood-d) 0%, transparent 60%)`,
            backgroundColor:'var(--mood-d)'
          }}/>
          <div style={{ position:'relative' }}>
            <div className="mono" style={{ fontSize:10, letterSpacing:'.2em', opacity:.85 }}>AI · WEEKLY ESSAY · 周报节选</div>
            <h2 className="display" style={{ fontSize: isMobile?38:60, margin:'12px 0 16px', textShadow:'0 2px 30px rgba(0,0,0,0.2)' }}>
              "Tender, but never sleepy."
            </h2>
            <p style={{ fontFamily:'var(--serif-cn)', fontSize: isMobile?15:18, lineHeight: 1.7, maxWidth: 720 }}>
              这一周你听了 14 小时音乐，其中三个晚上都进入了深夜电台。环境与古典占了一半以上，你跳过的几乎都是带有强人声的曲目——
              你需要一种<em style={{ color:'var(--mood-a)', fontStyle:'normal' }}>不打扰你思考的陪伴</em>。
              周三下午心情曲线明显下沉，是那天连听了三遍《Spiegel im Spiegel》。
            </p>
            <div className="row" style={{ marginTop: 24, gap: 10 }}>
              <button className="btn" style={{ background:'#fff', color:'var(--ink)', borderColor:'#fff' }}>查看完整周报 →</button>
              <button className="btn btn-ghost" style={{ color:'#fff', borderColor:'rgba(255,255,255,0.4)' }}>生成分享长图</button>
            </div>
          </div>
        </div>

        {/* History reports */}
        <div style={{ marginTop: 56 }}>
          <div className="meta">PREVIOUS ISSUES · 历史报告</div>
          <div className="display-cn" style={{ fontSize: isMobile?22:30, marginTop: 4, marginBottom: 20 }}>过往周报</div>
          <div style={{ display:'grid', gridTemplateColumns: isMobile?'repeat(2, 1fr)':'repeat(4, 1fr)', gap: 16 }}>
            {['焦灼的尝试','缓慢上升','潮湿但安静','久违的明亮'].map((t,i)=>(
              <div key={i} style={{ padding: 14, border:'1px solid var(--rule)', borderRadius: 16, background:'var(--paper)' }}>
                <div data-mood={['energetic','focused','melancholy','dusk'][i]}>
                  <MoodBlob size={isMobile?160:240} drift={false} geometry="blob" style={{ marginBottom: 12 }}/>
                </div>
                <div className="meta">WEEK {17-i} · 2026</div>
                <div style={{ fontFamily:'var(--serif-cn)', fontSize: 17, fontWeight: 500, marginTop: 4 }}>{t}</div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </ScreenFrame>
  );
}

function MoodCurveSVG({ isMobile }) {
  const w = isMobile? 320 : 1100;
  const h = isMobile? 170 : 220;
  const days = ['MON','TUE','WED','THU','FRI','SAT','SUN'];
  const userPoints = [0.6, 0.5, 0.3, 0.4, 0.55, 0.7, 0.65];
  const songPoints = [0.55, 0.55, 0.4, 0.5, 0.6, 0.7, 0.62];
  const px = (i) => (i / (days.length-1)) * (w - 60) + 30;
  const py = (v) => h - 30 - v * (h - 60);
  const path = (pts) => pts.map((v,i) => `${i===0?'M':'L'} ${px(i)} ${py(v)}`).join(' ');

  return (
    <svg viewBox={`0 0 ${w} ${h}`} width="100%" height="100%" style={{ overflow:'visible' }}>
      {[0,0.25,0.5,0.75,1].map((v,i)=>(
        <line key={i} x1="30" y1={py(v)} x2={w-30} y2={py(v)} stroke="var(--rule)" strokeDasharray="2 4"/>
      ))}
      {days.map((d,i)=>(
        <text key={d} x={px(i)} y={h-8} textAnchor="middle" style={{ fontFamily:'var(--mono)', fontSize: 10, letterSpacing:'.18em', fill:'var(--ink-3)' }}>{d}</text>
      ))}
      <path d={path(songPoints)} fill="none" stroke="var(--mood-c)" strokeWidth="1.5" strokeDasharray="4 4"/>
      <path d={path(userPoints)} fill="none" stroke="var(--mood-b)" strokeWidth="2"/>
      {userPoints.map((v,i)=>(
        <circle key={i} cx={px(i)} cy={py(v)} r="3" fill="var(--mood-b)"/>
      ))}
      <circle cx={px(2)} cy={py(userPoints[2])} r="7" fill="none" stroke="var(--mood-b)" opacity="0.6"/>
      <text x={px(2)} y={py(userPoints[2])-14} textAnchor="middle" style={{ fontFamily:'var(--serif-en)', fontStyle:'italic', fontSize: 13, fill:'var(--ink)' }}>low point</text>
    </svg>
  );
}

function RadarSVG() {
  const labels = [
    {n:'Ambient', cn:'环境', v: 0.95},
    {n:'Classical', cn:'古典', v: 0.7},
    {n:'Folk', cn:'民谣', v: 0.6},
    {n:'Indie', cn:'独立', v: 0.5},
    {n:'Electronic', cn:'电子', v: 0.4},
    {n:'Jazz', cn:'爵士', v: 0.35},
    {n:'Pop', cn:'流行', v: 0.2},
    {n:'Rock', cn:'摇滚', v: 0.15},
  ];
  const cx = 180, cy = 180, R = 130;
  const point = (i, r) => {
    const a = (i/labels.length) * Math.PI*2 - Math.PI/2;
    return [cx + Math.cos(a)*r, cy + Math.sin(a)*r];
  };
  const poly = labels.map((l,i) => point(i, R*l.v).join(',')).join(' ');
  return (
    <svg viewBox="0 0 360 360" width="100%" height="320" style={{ maxWidth: 360 }}>
      {[0.25,0.5,0.75,1].map((s,i)=>(
        <polygon key={i}
          points={labels.map((_,j) => point(j, R*s).join(',')).join(' ')}
          fill="none" stroke="var(--rule)"
        />
      ))}
      {labels.map((_,i)=>{
        const [x,y] = point(i, R);
        return <line key={i} x1={cx} y1={cy} x2={x} y2={y} stroke="var(--rule)"/>;
      })}
      <polygon points={poly} fill="url(#radarFill)" stroke="var(--mood-b)" strokeWidth="1.5"/>
      <defs>
        <radialGradient id="radarFill">
          <stop offset="0%" stopColor="var(--mood-a)" stopOpacity="0.6"/>
          <stop offset="100%" stopColor="var(--mood-c)" stopOpacity="0.3"/>
        </radialGradient>
      </defs>
      {labels.map((l,i)=>{
        const [x,y] = point(i, R+22);
        return (
          <g key={l.n}>
            <text x={x} y={y} textAnchor="middle" style={{ fontFamily:'var(--serif-en)', fontStyle:'italic', fontSize: 13, fill:'var(--ink)' }}>{l.n}</text>
            <text x={x} y={y+14} textAnchor="middle" style={{ fontFamily:'var(--serif-cn)', fontSize: 10, fill:'var(--ink-3)' }}>{l.cn}</text>
          </g>
        );
      })}
    </svg>
  );
}

window.Insights = Insights;
