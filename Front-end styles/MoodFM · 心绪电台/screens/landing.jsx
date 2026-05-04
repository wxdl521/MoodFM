/* global React, ScreenFrame, MoodBlob, Icon */
// P1 · Landing page

function Landing({ device = 'desktop', theme, mood, onEnter }) {
  const isMobile = device === 'mobile';
  const W = isMobile ? 390 : 1440;
  const H = isMobile ? 844 : 900;

  return (
    <ScreenFrame theme={theme} mood={mood} width={W} height={H} label="01 Landing" scroll={isMobile}>
      {/* Background blobs */}
      <div className="mood-blob drift" style={{
        width: isMobile ? 520 : 900, height: isMobile ? 520 : 900,
        right: isMobile ? -180 : -200, top: isMobile ? -200 : -260, opacity: 0.55,
      }}/>
      <div className="mood-blob drift" style={{
        width: isMobile ? 380 : 620, height: isMobile ? 380 : 620,
        left: isMobile ? -160 : -160, bottom: isMobile ? -180 : -220, opacity: 0.45,
      }}/>

      {/* Top bar */}
      <div style={{
        position: 'relative', zIndex: 2,
        padding: isMobile ? '20px 22px' : '28px 56px',
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
      }}>
        <div className="row" style={{ gap: 10 }}>
          <Logo size={isMobile ? 22 : 26} />
          <div style={{ fontFamily: 'var(--serif-en)', fontSize: isMobile ? 18 : 22, fontStyle: 'italic' }}>
            Mood<span style={{ color: 'var(--ink-3)' }}>FM</span>
          </div>
        </div>
        {!isMobile && (
          <div className="row mono" style={{ gap: 28, fontSize: 11, letterSpacing: '0.18em', textTransform: 'uppercase', color: 'var(--ink-2)' }}>
            <span>About</span><span>How it works</span><span>Manifesto</span>
          </div>
        )}
        <div className="row" style={{ gap: 10 }}>
          <button className="btn-pill" onClick={onEnter}>登录 · Sign in</button>
          {!isMobile && <button className="btn" onClick={onEnter}>开始 · Start</button>}
        </div>
      </div>

      {/* Hero */}
      <div style={{
        position: 'relative', zIndex: 2,
        padding: isMobile ? '12px 22px 0' : '40px 56px 0',
        display: 'grid',
        gridTemplateColumns: isMobile ? '1fr' : '1.3fr 1fr',
        gap: isMobile ? 28 : 56,
        alignItems: 'start',
      }}>
        <div>
          <div className="meta" style={{ marginBottom: isMobile ? 16 : 22 }}>
            ISSUE №01 · 2026 / SPRING — A PRIVATE RADIO STATION
          </div>

          <h1 className="display" style={{
            fontSize: isMobile ? 64 : 132,
            margin: 0, color: 'var(--ink)',
          }}>
            <span style={{ display: 'block' }}>Tune into</span>
            <span style={{ display: 'block' }}>the <em style={{ color: 'transparent', WebkitTextStroke: '1px var(--ink)' }}>weather</em></span>
            <span style={{ display: 'block' }}>of your <em>heart.</em></span>
          </h1>

          <div className="display-cn" style={{
            fontSize: isMobile ? 28 : 44,
            marginTop: isMobile ? 18 : 28,
            color: 'var(--ink)',
            maxWidth: 620,
          }}>
            为此刻的你，<br/>调一台只属于你的电台。
          </div>

          <p style={{
            marginTop: isMobile ? 18 : 32,
            maxWidth: 460, color: 'var(--ink-2)',
            fontSize: isMobile ? 15 : 17, lineHeight: 1.6,
          }}>
            MoodFM 读取你的情绪，跨越网易云与 QQ 音乐，从你的曲库与海量乐池中实时编排。
            一段文字、一张色盘、或者一个按钮——交给我们，剩下的就听吧。
          </p>

          <div className="row" style={{ gap: 12, marginTop: isMobile ? 26 : 40, flexWrap: 'wrap' }}>
            <button className="btn" onClick={onEnter}>
              进入电台 · Enter <Icon.arrow/>
            </button>
            <button className="btn btn-ghost">观看示例 · Watch demo</button>
          </div>

          <div className="meta" style={{ marginTop: isMobile ? 30 : 56, color: 'var(--ink-3)' }}>
            ▼ SCROLL · 三种方式开始你的电台
          </div>
        </div>

        {/* Hero blob art */}
        <div style={{ position: 'relative', display: 'flex', justifyContent: isMobile ? 'center' : 'flex-end' }}>
          <MoodBlob size={isMobile ? 280 : 460} drift={true} geometry="blob">
            <div style={{
              position: 'absolute', inset: 0,
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              flexDirection: 'column', color: 'rgba(255,255,255,0.92)',
              textShadow: '0 1px 12px rgba(0,0,0,0.2)',
            }}>
              <div className="mono" style={{ fontSize: 11, letterSpacing: '0.2em', opacity: 0.85 }}>NOW PLAYING · 北京 · 23:14</div>
              <div className="serif-en" style={{ fontSize: isMobile ? 32 : 56, marginTop: 8 }}>weightless</div>
              <div style={{ fontFamily: 'var(--serif-cn)', fontSize: isMobile ? 14 : 16, marginTop: 4, opacity: 0.85 }}>晚归 · Late return</div>
            </div>
          </MoodBlob>
        </div>
      </div>

      {/* Three-feature strip */}
      {!isMobile && <hr className="rule" style={{ margin: '60px 56px 0' }}/>}
      <div style={{
        position: 'relative', zIndex: 2,
        padding: isMobile ? '40px 22px 24px' : '32px 56px 0',
        display: 'grid',
        gridTemplateColumns: isMobile ? '1fr' : 'repeat(3, 1fr)',
        gap: isMobile ? 24 : 0,
      }}>
        {[
          { n: '01', en: 'Read', cn: '读你', body: '一句话、一个场景、或一片色盘——AI 把你此刻的情绪翻译成可听见的频率。' },
          { n: '02', en: 'Compose', cn: '调台', body: '横跨多个平台，从你的红心、推荐池与全网曲库里编排一档专属节目。' },
          { n: '03', en: 'Remember', cn: '记得', body: '每次收听都成为你心情地图的一部分，每周生成一份情绪周报。' },
        ].map((f, i) => (
          <div key={f.n} style={{
            padding: isMobile ? 0 : '0 28px 0 0',
            borderRight: !isMobile && i < 2 ? '1px solid var(--rule)' : 'none',
          }}>
            <div className="meta">CHAPTER {f.n}</div>
            <div className="serif-en" style={{ fontSize: isMobile ? 36 : 48, marginTop: 6, color: 'var(--ink)' }}>
              {f.en}<span style={{ color: 'var(--ink-3)' }}>.</span>
            </div>
            <div style={{ fontFamily: 'var(--serif-cn)', fontSize: 22, fontWeight: 500, marginTop: 4 }}>{f.cn}</div>
            <p style={{ color: 'var(--ink-2)', fontSize: 14, lineHeight: 1.6, marginTop: 12, maxWidth: 320 }}>{f.body}</p>
          </div>
        ))}
      </div>

      {/* Footer marquee */}
      {!isMobile && (
        <div style={{
          position: 'absolute', left: 0, right: 0, bottom: 0,
          padding: '14px 56px', borderTop: '1px solid var(--rule)',
          display: 'flex', justifyContent: 'space-between',
          fontFamily: 'var(--mono)', fontSize: 11, letterSpacing: '0.18em', textTransform: 'uppercase', color: 'var(--ink-3)',
        }}>
          <span>© MoodFM 2026 / a private station</span>
          <span>网易云音乐 · QQ 音乐 · BiliBili Music</span>
          <span>v1.0 — issue spring</span>
        </div>
      )}
    </ScreenFrame>
  );
}

function Logo({ size = 24 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 32 32">
      <defs>
        <radialGradient id="lg" cx="30%" cy="30%">
          <stop offset="0%" stopColor="var(--mood-a)"/>
          <stop offset="100%" stopColor="var(--mood-d)"/>
        </radialGradient>
      </defs>
      <circle cx="16" cy="16" r="14" fill="url(#lg)"/>
      <circle cx="16" cy="16" r="3" fill="var(--paper)"/>
    </svg>
  );
}

window.Landing = Landing;
window.Logo = Logo;
