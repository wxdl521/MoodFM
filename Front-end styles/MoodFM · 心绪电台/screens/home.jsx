/* global React, ScreenFrame, MoodBlob, MoodWheel, Icon, Logo */
// P6 · Home / Mood input

const { useState: useStateH } = React;

function Home({ device = 'desktop', theme, mood, wheelGeometry = 'circle', onPlay }) {
  const isMobile = device === 'mobile';
  const W = isMobile ? 390 : 1440;
  const H = isMobile ? 844 : 900;

  const [input, setInput] = useStateH('');
  const [scene, setScene] = useStateH('通勤');
  const scenes = ['通勤', '学习', '跑步', '写作', '睡前', '派对', '深夜', '+ 自定义'];

  const hour = new Date().getHours();
  const greet = hour < 11 ? '早安' : hour < 18 ? '下午好' : hour < 23 ? '晚上好' : '深夜好';
  const greetEn = hour < 11 ? 'Good morning' : hour < 18 ? 'Good afternoon' : hour < 23 ? 'Good evening' : 'Late night';

  return (
    <ScreenFrame theme={theme} mood={mood} width={W} height={H} label="02 Home" scroll={true} overlay={<MiniPlayer isMobile={isMobile} onClick={onPlay}/>}>
      <div className="mood-blob drift" style={{ width: isMobile?420:760, height: isMobile?420:760, right: isMobile?-180:-220, top: isMobile?-220:-260, opacity: 0.4 }}/>

      {/* Top nav */}
      <div style={{
        position: 'sticky', top: 0, zIndex: 5, background: 'var(--bg)',
        padding: isMobile ? '16px 22px' : '22px 56px',
        borderBottom: '1px solid var(--rule)',
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
      }}>
        <div className="row" style={{ gap: 10 }}>
          <Logo size={isMobile?20:24}/>
          <div style={{ fontFamily:'var(--serif-en)', fontSize:isMobile?16:20, fontStyle:'italic' }}>MoodFM</div>
          {!isMobile && (
            <div className="row mono" style={{ marginLeft: 32, gap: 22, fontSize:11, letterSpacing:'.16em', textTransform:'uppercase', color:'var(--ink-2)' }}>
              <span style={{ color:'var(--ink)' }}>· 电台 Radio</span>
              <span>歌单 Library</span>
              <span>历史 History</span>
              <span>画像 Insights</span>
            </div>
          )}
        </div>
        <div className="row" style={{ gap: 14 }}>
          <span className="mono" style={{ fontSize:10, letterSpacing:'.14em', color:'var(--ink-3)' }}>NETEASE · 默认</span>
          <Icon.search/>
          <div style={{ position:'relative' }}>
            <Icon.bell/>
            <span style={{ position:'absolute', top:-3, right:-3, width:6, height:6, borderRadius:'50%', background:'var(--mood-b)' }}/>
          </div>
          <div style={{ width:30, height:30, borderRadius:'50%', background:'var(--bg-2)', border:'1px solid var(--rule)', fontSize:12, display:'flex', alignItems:'center', justifyContent:'center', fontFamily:'var(--serif-cn)' }}>江</div>
        </div>
      </div>

      <div style={{
        position:'relative', zIndex:2,
        padding: isMobile ? '24px 22px 120px' : '40px 56px 80px',
        display:'grid',
        gridTemplateColumns: isMobile ? '1fr' : '1.3fr 1fr',
        gap: isMobile ? 28 : 56,
      }}>
        {/* LEFT — input */}
        <div>
          <div className="meta">{greetEn.toUpperCase()} · {new Date().toLocaleDateString('zh-CN',{month:'long',day:'numeric'})}</div>
          <h1 className="display" style={{ fontSize: isMobile?52:96, margin: '8px 0 0' }}>
            {greetEn}<em style={{ color:'var(--ink-3)' }}>,</em>
          </h1>
          <div className="display-cn" style={{ fontSize: isMobile?28:40, marginTop: 4 }}>
            {greet}，江野。<span style={{ color:'var(--ink-3)' }}>今晚想听点什么？</span>
          </div>

          {/* Method A — text input */}
          <div style={{ marginTop: isMobile?28:36 }}>
            <div className="meta" style={{ marginBottom: 10 }}>方式 A · IN YOUR OWN WORDS</div>
            {isMobile ? (
              <div>
                <textarea
                  className="field"
                  placeholder="此刻你在哪里、心里是什么颜色？比如：加班到很晚，需要一点不打扰我的电流声…"
                  value={input}
                  onChange={e=>setInput(e.target.value)}
                  style={{ fontSize: 15, minHeight: 110 }}
                />
                <button className="btn" onClick={onPlay} style={{ marginTop: 10, width:'100%', height: 46 }}>
                  调台 <Icon.arrow/>
                </button>
              </div>
            ) : (
              <div style={{ position:'relative' }}>
                <textarea
                  className="field"
                  placeholder="此刻你在哪里、心里是什么颜色？比如：加班到很晚，需要一点不打扰我的电流声…"
                  value={input}
                  onChange={e=>setInput(e.target.value)}
                  style={{ paddingRight: 120, fontSize: 16, minHeight: 120 }}
                />
                <button className="btn" onClick={onPlay} style={{ position:'absolute', right:10, bottom:10, height:40, padding:'0 16px' }}>
                  调台 <Icon.arrow/>
                </button>
              </div>
            )}
          </div>

          {/* Method B — scenes */}
          <div style={{ marginTop: 28 }}>
            <div className="meta" style={{ marginBottom: 10 }}>方式 B · PRESET SCENES</div>
            <div style={{ display:'flex', flexWrap:'wrap', gap: 8 }}>
              {scenes.map(s => (
                <button key={s} className={'btn-pill' + (s===scene?' active':'')} onClick={()=>setScene(s)}>
                  {s}
                </button>
              ))}
            </div>
          </div>

          {/* Method D — silent start */}
          <div style={{ marginTop: 28, padding: isMobile?'18px':'22px 26px', border:'1px solid var(--rule)', borderRadius: 18, display:'flex', justifyContent:'space-between', alignItems:'center', gap: 16, background:'var(--paper)' }}>
            <div>
              <div className="serif-en" style={{ fontSize: isMobile?24:30 }}>Just play.</div>
              <div style={{ fontFamily:'var(--serif-cn)', fontSize: 14, color:'var(--ink-2)', marginTop: 2 }}>什么都别问 · 直接给我一个电台</div>
            </div>
            <button className="btn" onClick={onPlay}>启动 <Icon.play/></button>
          </div>
        </div>

        {/* RIGHT — MoodWheel */}
        <div>
          <div className="meta" style={{ marginBottom: 12 }}>方式 C · MOOD COMPASS · 心情色盘</div>
          <div style={{ display:'flex', justifyContent:'center' }}>
            <MoodWheel size={isMobile?260:340} geometry={wheelGeometry}/>
          </div>
          <div className="meta" style={{ textAlign:'center', marginTop: 14, color:'var(--ink-2)' }}>
            DRAG · 拖动球点选择此刻的心情坐标
          </div>

          {/* Resume + recommendation cards */}
          <div style={{ marginTop: 28 }}>
            <div className="meta" style={{ marginBottom: 10 }}>继续 · CONTINUE</div>
            <div style={{ display:'flex', gap: 12, padding: 14, border:'1px solid var(--rule)', borderRadius: 16, alignItems:'center' }}>
              <MoodBlob size={56} drift={false} geometry="circle"/>
              <div style={{ flex:1 }}>
                <div style={{ fontFamily:'var(--serif-cn)', fontSize: 15, fontWeight:500 }}>加班疲惫电台</div>
                <div className="meta" style={{ marginTop: 2 }}>剩 7 首 · 28 MIN · 昨晚 23:14</div>
              </div>
              <button className="btn-pill"><Icon.play/></button>
            </div>
          </div>

          <div style={{ marginTop: 20 }}>
            <div className="meta" style={{ marginBottom: 10 }}>今日推荐 · FOR THE HOUR</div>
            <div style={{ padding: 16, borderRadius: 16, color:'var(--paper)', position:'relative', overflow:'hidden', minHeight: 120 }}>
              <div style={{ position:'absolute', inset:0, background: `
                radial-gradient(circle at 20% 20%, var(--mood-a) 0%, transparent 60%),
                radial-gradient(circle at 80% 30%, var(--mood-b) 0%, transparent 60%),
                radial-gradient(circle at 60% 90%, var(--mood-d) 0%, transparent 70%)`, backgroundColor:'var(--mood-c)' }}/>
              <div style={{ position:'relative' }}>
                <div className="mono" style={{ fontSize:10, letterSpacing:'.16em', opacity:.85 }}>23:00 — 02:00</div>
                <div className="serif-en" style={{ fontSize: 36, lineHeight:1, marginTop: 6 }}>night float</div>
                <div style={{ fontSize: 14, marginTop: 4, opacity:.9 }}>深夜漂浮电台 · 13 首 · 49 min</div>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Recent sessions strip */}
      <div style={{ position:'relative', padding: isMobile? '0 22px 140px':'0 56px 100px' }}>
        <div className="between" style={{ marginBottom: 14 }}>
          <div className="meta">最近的电台 · RECENT STATIONS / 09 ARCHIVED</div>
          <div className="meta" style={{ color:'var(--ink-2)' }}>查看全部 →</div>
        </div>
        <div style={{ display:'grid', gridTemplateColumns: isMobile?'repeat(3, 240px)':'repeat(4, 1fr)', gap: 16, overflowX: isMobile?'auto':'visible' }}>
          {[
            { t:'通勤的余光', tag:'平静 · 中能量', n: 14, m: '52', c:'dusk' },
            { t:'雨打窗台', tag:'忧郁 · 低能量', n: 9, m: '34', c:'melancholy' },
            { t:'清晨慢跑', tag:'明亮 · 高能量', n: 18, m: '61', c:'energetic' },
            { t:'专注写作', tag:'冷静 · 中能量', n: 12, m: '48', c:'focused' },
          ].map((s, i) => (
            <div key={i} data-mood={s.c} style={{ border:'1px solid var(--rule)', borderRadius: 16, padding: 14, background:'var(--paper)' }}>
              <MoodBlob size={isMobile?200:260} drift={false} geometry="blob" style={{ marginBottom: 10 }}/>
              <div className="meta">№ 0{i+1} · {s.tag.toUpperCase()}</div>
              <div style={{ fontFamily:'var(--serif-cn)', fontSize: 18, fontWeight:500, marginTop: 4 }}>{s.t}</div>
              <div className="meta" style={{ marginTop: 6, color:'var(--ink-2)' }}>{s.n} 首 · {s.m} MIN</div>
            </div>
          ))}
        </div>
      </div>

      {/* Mini player rendered as ScreenFrame overlay */}
    </ScreenFrame>
  );
}

function MiniPlayer({ isMobile, onClick }) {
  return (
    <div onClick={onClick} style={{
      position: 'absolute',
      left: isMobile?12:24, right: isMobile?12:24, bottom: isMobile?12:18,
      height: 64, background:'var(--ink)', color:'var(--bg)',
      borderRadius: 999, padding: '0 8px 0 8px',
      display:'flex', alignItems:'center', gap: 12, cursor:'pointer', boxShadow:'0 12px 40px rgba(0,0,0,0.18)',
      zIndex: 10,
    }}>
      <MoodBlob size={48} drift={true} geometry="circle"/>
      <div style={{ flex:1, minWidth:0 }}>
        <div style={{ fontFamily:'var(--serif-cn)', fontSize:14, fontWeight:500, whiteSpace:'nowrap', overflow:'hidden', textOverflow:'ellipsis' }}>
          weightless — 晚归
        </div>
        <div className="mono" style={{ fontSize:10, letterSpacing:'.14em', opacity:.7, marginTop:2 }}>MARCONI UNION · NETEASE · 1:24 / 8:09</div>
      </div>
      <div className="row" style={{ gap: 4, paddingRight: 4 }}>
        <button style={iconBtnDark}><Icon.prev/></button>
        <button style={{ ...iconBtnDark, background:'var(--bg)', color:'var(--ink)' }}><Icon.pause/></button>
        <button style={iconBtnDark}><Icon.next/></button>
      </div>
    </div>
  );
}
const iconBtnDark = { width:36, height:36, borderRadius:'50%', border:'none', background:'transparent', color:'var(--bg)', cursor:'pointer', display:'inline-flex', alignItems:'center', justifyContent:'center' };

window.Home = Home;
window.MiniPlayer = MiniPlayer;
