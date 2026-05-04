/* global React, ScreenFrame, MoodBlob, Icon, Logo */
// P14 · Weekly Report — long-form editorial + share-as-image mode

function WeeklyReport({ device='desktop', theme, mood, onBack, mode='page' }) {
  const isMobile = device === 'mobile';
  const isShare = mode === 'share';
  const W = isShare ? 390 : (isMobile ? 390 : 1440);
  const H = isShare ? 1480 : (isMobile ? 844 : 900);

  if (isShare) return <ShareCard W={W} H={H} theme={theme} mood={mood}/>;

  return (
    <ScreenFrame theme={theme} mood={mood} width={W} height={H} label="04b Weekly" scroll={true}>
      {/* Top */}
      <div style={{ position:'sticky', top:0, zIndex:5, background:'var(--bg)', padding: isMobile?'14px 18px':'22px 56px', borderBottom:'1px solid var(--rule)', display:'flex', alignItems:'center', justifyContent:'space-between' }}>
        <button onClick={onBack} className="btn-pill"><Icon.chevL/> Insights</button>
        {!isMobile && <div className="meta">WEEKLY REPORT · 周报</div>}
        <button className="btn" style={{ height: 36, padding:'0 14px' }}>分享长图 <Icon.arrow/></button>
      </div>

      {/* Hero */}
      <div style={{ position:'relative', overflow:'hidden', padding: isMobile?'40px 22px 60px':'80px 56px 100px', borderBottom: '1px solid var(--rule)' }}>
        <div className="mood-blob drift" style={{ width: isMobile?500:900, height: isMobile?500:900, right: isMobile?-200:-200, top:-200, opacity: 0.5 }}/>
        <div style={{ position:'relative', zIndex: 2, maxWidth: 920 }}>
          <div className="meta">A WEEKLY DISPATCH · WEEK 18 / 2026 · APR 27 — MAY 03</div>
          <h1 className="display" style={{ fontSize: isMobile?64:160, margin: '20px 0 0', lineHeight: 0.92 }}>
            A week of
          </h1>
          <h1 className="display" style={{ fontSize: isMobile?64:160, margin: 0, lineHeight: 0.92 }}>
            <em>quiet</em> light.
          </h1>
          <div className="display-cn" style={{ fontSize: isMobile?28:48, marginTop: 24, color:'var(--ink-2)' }}>
            一周 · 安静、温柔、有一点点蓝。
          </div>
          <p style={{ marginTop: 28, fontSize: isMobile?15:18, lineHeight: 1.7, color:'var(--ink-2)', maxWidth: 640, fontFamily:'var(--serif-cn)' }}>
            这一周你听了 <strong style={{ color:'var(--ink)' }}>147 首歌、12 小时 38 分钟</strong>。情绪曲线整体偏向"温柔"与"专注"，深夜段出现两次"轻度忧郁"波峰——周三 23:14、周五 01:02。
          </p>
        </div>
      </div>

      {/* Numbers */}
      <div style={{ display:'grid', gridTemplateColumns: isMobile?'1fr 1fr':'repeat(4, 1fr)', gap: 0, borderBottom:'1px solid var(--rule)' }}>
        {[
          { n:'147', l:'TRACKS', cn:'首' },
          { n:'12h 38m', l:'LISTEN TIME', cn:'收听时长' },
          { n:'23', l:'NEW DISCOVERED', cn:'新发现' },
          { n:'82%', l:'MOOD MATCH', cn:'匹配度' },
        ].map((s, i) => (
          <div key={i} style={{ padding: isMobile?'24px 18px':'40px 32px', borderRight: (!isMobile && i<3) ? '1px solid var(--rule)' : (isMobile && i%2===0) ? '1px solid var(--rule)' : 'none', borderBottom: (isMobile && i<2) ? '1px solid var(--rule)' : 'none' }}>
            <div className="meta">{s.l}</div>
            <div className="display" style={{ fontSize: isMobile?40:64, marginTop: 8 }}>{s.n}</div>
            <div style={{ fontFamily:'var(--serif-cn)', fontSize: 13, color:'var(--ink-2)', marginTop: 2 }}>{s.cn}</div>
          </div>
        ))}
      </div>

      {/* Editorial body */}
      <div style={{ padding: isMobile?'40px 22px':'80px 56px', display: isMobile?'block':'grid', gridTemplateColumns: '1fr 2fr', gap: 56, borderBottom:'1px solid var(--rule)' }}>
        <div>
          <div className="meta">CHAPTER ONE</div>
          <h2 className="display" style={{ fontSize: isMobile?36:56, margin:'8px 0 0', lineHeight: 1.05 }}>
            The week, in one <em>shape</em>.
          </h2>
          <div style={{ fontFamily:'var(--serif-cn)', fontSize: 16, color:'var(--ink-2)', marginTop: 6 }}>一周一形</div>
        </div>
        <div>
          <div style={{ display:'flex', justifyContent:'center', padding: isMobile?'20px 0':'0' }}>
            <MoodBlob size={isMobile?260:340} drift={true} geometry="blob"/>
          </div>
          <p style={{ marginTop: 24, fontSize: isMobile?15:17, lineHeight: 1.8, color:'var(--ink-2)', fontFamily:'var(--serif-cn)' }}>
            如果用一个形状来描述这一周——大概是这样：边缘柔软，中间有一团暖光，左下角藏着一小片冷色调。这是 AI 根据你 <em>147 首歌</em>的情绪曲线生成的"周形"。
          </p>
        </div>
      </div>

      {/* Quote pullout */}
      <div style={{ padding: isMobile?'48px 22px':'80px 56px', textAlign:'center', borderBottom:'1px solid var(--rule)', background:'var(--bg-2)' }}>
        <div className="display" style={{ fontSize: isMobile?28:48, fontStyle:'italic', lineHeight: 1.3, maxWidth: 880, margin:'0 auto' }}>
          "你似乎更喜欢在 <em style={{ background:'linear-gradient(180deg, transparent 60%, var(--mood-a) 60%)', padding:'0 4px' }}>23 点之后</em> 听 ambient——
          也许，那是你对一天最温柔的告别。"
        </div>
        <div className="meta" style={{ marginTop: 24, color:'var(--ink-3)' }}>— AI · MOODFM EDITORIAL</div>
      </div>

      {/* Top discoveries */}
      <div style={{ padding: isMobile?'40px 22px':'72px 56px', borderBottom:'1px solid var(--rule)' }}>
        <div className="between" style={{ marginBottom: 24 }}>
          <div>
            <div className="meta">CHAPTER TWO · 本周新发现</div>
            <h2 className="display" style={{ fontSize: isMobile?32:56, margin:'8px 0 0' }}>23 <em>discoveries</em>.</h2>
          </div>
          <button className="btn-pill">查看全部 →</button>
        </div>
        <div style={{ display:'grid', gridTemplateColumns: isMobile?'1fr 1fr':'repeat(4, 1fr)', gap: 16 }}>
          {[
            { t:'Re: Stacks', a:'Bon Iver', g:'Folk', m:'TENDER' },
            { t:'晚安', a:'Mong Tong', g:'Ambient', m:'CALM' },
            { t:'Ferry', a:'Cigarettes After Sex', g:'Dream Pop', m:'BLUE' },
            { t:'Petrichor', a:'Ólafur Arnalds', g:'Neoclassical', m:'FOCUS' },
          ].map((tk, i) => (
            <div key={i} style={{ padding: 16, border:'1px solid var(--rule)', borderRadius: 14, background:'var(--paper)' }}>
              <MoodBlob size={isMobile?140:200} drift={false} geometry="blob"/>
              <div className="meta" style={{ marginTop: 10 }}>№ {String(i+1).padStart(2,'0')} · {tk.m}</div>
              <div className="serif-en" style={{ fontSize: 18, fontStyle:'italic', marginTop: 4 }}>{tk.t}</div>
              <div className="meta" style={{ marginTop: 2 }}>{tk.a} · {tk.g}</div>
            </div>
          ))}
        </div>
      </div>

      {/* Footer CTA */}
      <div style={{ padding: isMobile?'40px 22px 60px':'72px 56px', textAlign:'center' }}>
        <div className="meta">SHARE · 分享</div>
        <h2 className="display" style={{ fontSize: isMobile?32:56, margin:'8px 0 16px' }}>
          A week worth <em>keeping</em>.
        </h2>
        <div style={{ fontFamily:'var(--serif-cn)', fontSize: 15, color:'var(--ink-2)', maxWidth: 480, margin:'0 auto 24px' }}>
          导出一张可分享的长图，把这一周的"形状"留在朋友圈。
        </div>
        <div className="row" style={{ gap: 10, justifyContent:'center', flexWrap:'wrap' }}>
          <button className="btn">生成分享长图 <Icon.arrow/></button>
          <button className="btn-pill">导出 PDF</button>
        </div>
      </div>
    </ScreenFrame>
  );
}

function ShareCard({ W, H, theme, mood }) {
  return (
    <ScreenFrame theme={theme} mood={mood} width={W} height={H} label="04c Share Card" scroll={false}>
      <div style={{ position:'relative', width:'100%', height:'100%', overflow:'hidden', padding: '36px 28px', display:'flex', flexDirection:'column' }}>
        <div className="mood-blob drift" style={{ width: 600, height: 600, right: -200, top:-150, opacity: 0.65 }}/>

        <div style={{ position:'relative', zIndex: 2, display:'flex', flexDirection:'column', height:'100%' }}>
          {/* Top brand */}
          <div className="row" style={{ gap: 8, justifyContent:'space-between' }}>
            <div className="row" style={{ gap: 8 }}>
              <Logo size={20}/>
              <div style={{ fontFamily:'var(--serif-en)', fontSize: 16, fontStyle:'italic' }}>MoodFM</div>
            </div>
            <div className="meta" style={{ color:'var(--ink-3)' }}>WEEK 18 · 2026</div>
          </div>

          {/* Hero text */}
          <div style={{ marginTop: 32 }}>
            <div className="meta">A WEEK OF</div>
            <h1 className="display" style={{ fontSize: 88, margin:'10px 0 0', lineHeight: 0.92 }}>
              <em>quiet</em>
            </h1>
            <h1 className="display" style={{ fontSize: 88, margin: 0, lineHeight: 0.92 }}>
              light.
            </h1>
            <div className="display-cn" style={{ fontSize: 22, marginTop: 14 }}>
              一周 · 安静、温柔、有一点点蓝。
            </div>
          </div>

          {/* Big blob */}
          <div style={{ marginTop: 28, display:'flex', justifyContent:'center' }}>
            <MoodBlob size={280} drift={true} geometry="blob"/>
          </div>

          {/* Stats grid */}
          <div style={{ marginTop: 24, display:'grid', gridTemplateColumns:'1fr 1fr', gap: 1, background:'var(--rule)', border:'1px solid var(--rule)', borderRadius: 14, overflow:'hidden' }}>
            {[
              { n:'147', l:'首 · TRACKS' },
              { n:'12h 38m', l:'时长 · TIME' },
              { n:'23', l:'新发现 · NEW' },
              { n:'82%', l:'匹配 · MATCH' },
            ].map((s,i)=>(
              <div key={i} style={{ background:'var(--paper)', padding:'14px 12px' }}>
                <div className="display" style={{ fontSize: 28 }}>{s.n}</div>
                <div className="meta" style={{ marginTop: 2 }}>{s.l}</div>
              </div>
            ))}
          </div>

          {/* Top track */}
          <div style={{ marginTop: 24, padding: 16, background:'var(--paper)', border:'1px solid var(--rule)', borderRadius: 14 }}>
            <div className="meta">MOST PLAYED · 最常听</div>
            <div className="serif-en" style={{ fontSize: 26, fontStyle:'italic', marginTop: 6 }}>weightless</div>
            <div style={{ fontFamily:'var(--serif-cn)', fontSize: 13, color:'var(--ink-2)' }}>Marconi Union · 14 次</div>
          </div>

          {/* Footer */}
          <div style={{ marginTop: 'auto', paddingTop: 28, borderTop:'1px solid var(--rule)', display:'flex', justifyContent:'space-between', alignItems:'flex-end' }}>
            <div>
              <div className="meta">江野 · @jiangye</div>
              <div className="meta" style={{ marginTop: 4, color:'var(--ink-3)' }}>moodfm.app</div>
            </div>
            <div style={{ width: 64, height: 64, background:'#fff', border:'1px solid var(--rule)', display:'flex', alignItems:'center', justifyContent:'center' }}>
              <div className="meta" style={{ fontSize: 8, color:'var(--ink-3)' }}>QR</div>
            </div>
          </div>
        </div>
      </div>
    </ScreenFrame>
  );
}

window.WeeklyReport = WeeklyReport;
