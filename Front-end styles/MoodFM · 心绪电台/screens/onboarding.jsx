/* global React, ScreenFrame, MoodBlob, Icon, Logo */
// P4 · Onboarding (multi-step)

function Onboarding({ device='desktop', theme, mood, step: initialStep = 1, onDone }) {
  const isMobile = device === 'mobile';
  const W = isMobile ? 390 : 1440;
  const H = isMobile ? 844 : 900;
  const [step, setStep] = React.useState(initialStep);
  const total = 4;

  const next = () => step < total ? setStep(step+1) : onDone && onDone();
  const prev = () => step > 1 && setStep(step-1);

  return (
    <ScreenFrame theme={theme} mood={mood} width={W} height={H} label={`02c Onboarding ${step}/4`} scroll={isMobile}>
      <div className="mood-blob drift" style={{ width: isMobile?500:800, height: isMobile?500:800, right: isMobile?-200:-150, top:-200, opacity: 0.4 }}/>

      {/* Top */}
      <div style={{ position:'relative', zIndex: 2, padding: isMobile?'20px 22px':'28px 56px', display:'flex', alignItems:'center', justifyContent:'space-between' }}>
        <div className="row" style={{ gap: 10 }}>
          <Logo size={isMobile?20:24}/>
          <div style={{ fontFamily:'var(--serif-en)', fontSize: isMobile?16:20, fontStyle:'italic' }}>MoodFM</div>
        </div>
        <div className="meta">STEP {String(step).padStart(2,'0')} / {String(total).padStart(2,'0')}</div>
        <a onClick={onDone} style={{ cursor:'pointer' }} className="meta">SKIP →</a>
      </div>

      {/* Progress dots */}
      <div className="row" style={{ position:'relative', zIndex:2, gap: 4, padding: isMobile?'0 22px':'0 56px' }}>
        {[1,2,3,4].map(s => (
          <div key={s} style={{ flex:1, height: 2, background: s<=step ? 'var(--ink)' : 'var(--rule)', transition:'background .3s' }}/>
        ))}
      </div>

      {/* Step body */}
      <div style={{ position:'relative', zIndex:2, padding: isMobile?'24px 22px':'40px 56px', minHeight: H - 200 }}>
        {step === 1 && <StepWelcome isMobile={isMobile}/>}
        {step === 2 && <StepBind isMobile={isMobile}/>}
        {step === 3 && <StepPrefs isMobile={isMobile}/>}
        {step === 4 && <StepReady isMobile={isMobile}/>}
      </div>

      {/* Footer nav */}
      <div style={{
        position:'absolute', left: 0, right: 0, bottom: 0, zIndex: 5,
        background:'var(--bg)', borderTop:'1px solid var(--rule)',
        padding: isMobile?'14px 22px':'20px 56px',
        display:'flex', alignItems:'center', justifyContent:'space-between', gap: 12,
      }}>
        <button className="btn-pill" onClick={prev} style={{ visibility: step===1?'hidden':'visible' }}>
          <Icon.chevL/> 上一步
        </button>
        <div className="meta" style={{ color:'var(--ink-3)' }}>
          {step===1 && '欢迎介绍'}
          {step===2 && '绑定平台 · 必选'}
          {step===3 && '偏好设置'}
          {step===4 && '准备就绪'}
        </div>
        <button className="btn" onClick={next}>
          {step < total ? <>下一步 <Icon.arrow/></> : <>进入电台 <Icon.arrow/></>}
        </button>
      </div>
    </ScreenFrame>
  );
}

function StepWelcome({ isMobile }) {
  const cards = [
    { n: '01', en: 'Read', cn: '读你', body: '说一句话、选一个场景、或在色盘上标个点。AI 帮你把感觉翻译成可听见的频率。' },
    { n: '02', en: 'Compose', cn: '调台', body: '横跨网易云与 QQ 音乐，从你的红心和全网曲库实时编排——一档只属于此刻的电台。' },
    { n: '03', en: 'Remember', cn: '记得', body: '每次收听都成为你心情地图的一笔。每周一份情绪周报，长图分享给朋友。' },
  ];
  return (
    <div>
      <div className="meta">CHAPTER ONE</div>
      <h1 className="display" style={{ fontSize: isMobile?56:120, margin: '12px 0 0' }}>
        Three <em>verbs</em>.
      </h1>
      <div className="display-cn" style={{ fontSize: isMobile?22:32, marginTop: 8, color:'var(--ink-2)' }}>
        关于这台电台，三个动词。
      </div>

      <div style={{ marginTop: isMobile?32:56, display:'grid', gridTemplateColumns: isMobile?'1fr':'repeat(3, 1fr)', gap: 1, background:'var(--rule)', border:'1px solid var(--rule)' }}>
        {cards.map(c => (
          <div key={c.n} style={{ background:'var(--paper)', padding: isMobile?'24px 20px':'32px 28px' }}>
            <div className="meta">№ {c.n}</div>
            <div className="serif-en" style={{ fontSize: isMobile?44:60, marginTop: 8 }}>{c.en}<span style={{ color:'var(--ink-3)' }}>.</span></div>
            <div style={{ fontFamily:'var(--serif-cn)', fontSize: 22, fontWeight:500, marginTop: 4 }}>{c.cn}</div>
            <p style={{ color:'var(--ink-2)', fontSize: 14, lineHeight: 1.7, marginTop: 14 }}>{c.body}</p>
          </div>
        ))}
      </div>
    </div>
  );
}

function StepBind({ isMobile }) {
  return (
    <div>
      <div className="meta">CHAPTER TWO · 必选 / REQUIRED</div>
      <h1 className="display" style={{ fontSize: isMobile?52:108, margin: '12px 0 0' }}>
        Plug in <em>your</em>
      </h1>
      <h1 className="display" style={{ fontSize: isMobile?52:108, margin: 0 }}>
        listening <em>history</em>.
      </h1>
      <div className="display-cn" style={{ fontSize: isMobile?22:30, marginTop: 12, color:'var(--ink-2)' }}>
        把你听了多年的曲库接进来——从此电台从你的红心开始。
      </div>

      <div style={{ marginTop: isMobile?28:48, display:'grid', gridTemplateColumns: isMobile?'1fr':'1fr 1fr', gap: 16 }}>
        <PlatformCard logo="网" name="网易云音乐" en="NETEASE CLOUD MUSIC" status="未绑定" cta="绑定 →" recommended />
        <PlatformCard logo="Q" name="QQ 音乐" en="QQ MUSIC" status="未绑定" cta="绑定 →"/>
      </div>

      <div className="meta" style={{ marginTop: 28, color:'var(--ink-3)', maxWidth: 500 }}>
        · 我们仅读取曲库与红心 · Cookie 加密存储 · 随时可解绑
      </div>
    </div>
  );
}

function PlatformCard({ logo, name, en, status, cta, recommended, bound }) {
  return (
    <div style={{
      padding: 24, border:'1px solid var(--rule)', borderRadius: 18,
      background:'var(--paper)', position:'relative',
    }}>
      {recommended && (
        <div style={{ position:'absolute', top: 14, right: 14 }} className="meta">RECOMMENDED</div>
      )}
      <div className="row" style={{ gap: 16 }}>
        <div style={{
          width: 56, height: 56, borderRadius: '50%',
          background: 'linear-gradient(135deg, var(--mood-a), var(--mood-c))',
          color:'#fff', display:'flex', alignItems:'center', justifyContent:'center',
          fontFamily:'var(--serif-cn)', fontSize: 24, fontWeight: 600,
        }}>{logo}</div>
        <div style={{ flex: 1 }}>
          <div style={{ fontFamily:'var(--serif-cn)', fontSize: 20, fontWeight: 500 }}>{name}</div>
          <div className="meta" style={{ marginTop: 2 }}>{en}</div>
        </div>
      </div>
      <div className="between" style={{ marginTop: 24 }}>
        <div className="meta">
          STATUS · <span style={{ color: bound ? 'var(--ink)' : 'var(--ink-3)' }}>{status}</span>
        </div>
        <button className="btn" style={{ height: 38, padding: '0 16px' }}>{cta}</button>
      </div>
    </div>
  );
}

function StepPrefs({ isMobile }) {
  const [genres, setGenres] = React.useState(new Set(['Ambient','Folk','Indie']));
  const [lang, setLang] = React.useState(new Set(['中文','English','日本語']));
  const [scene, setScene] = React.useState('深夜');
  const allGenres = ['Ambient','Classical','Folk','Indie','Electronic','Jazz','Hip-Hop','Rock','Pop','R&B','Post-Rock','Bossa Nova'];
  const allLang = ['中文','English','日本語','한국어','Français','Español','Instrumental'];
  const allScene = ['通勤','学习','跑步','写作','睡前','深夜','派对','咖啡馆'];
  const tog = (set, v, sf) => { const n = new Set(set); n.has(v) ? n.delete(v) : n.add(v); sf(n); };

  return (
    <div>
      <div className="meta">CHAPTER THREE · 偏好</div>
      <h1 className="display" style={{ fontSize: isMobile?52:96, margin: '12px 0 0' }}>
        What's in your
      </h1>
      <h1 className="display" style={{ fontSize: isMobile?52:96, margin: 0 }}>
        <em>weather</em>?
      </h1>
      <div className="display-cn" style={{ fontSize: isMobile?20:26, marginTop: 12, color:'var(--ink-2)' }}>
        说说你的"音乐天气"——这些会成为电台的初始风向。
      </div>

      <div style={{ marginTop: 36 }}>
        <div className="meta" style={{ marginBottom: 12 }}>FIELD A · 流派 / GENRES (多选)</div>
        <div style={{ display:'flex', flexWrap:'wrap', gap: 8 }}>
          {allGenres.map(g => (
            <button key={g} onClick={()=>tog(genres,g,setGenres)} className={'btn-pill' + (genres.has(g)?' active':'')}>{g}</button>
          ))}
        </div>
      </div>

      <div style={{ marginTop: 28 }}>
        <div className="meta" style={{ marginBottom: 12 }}>FIELD B · 语言 / LANGUAGES (多选)</div>
        <div style={{ display:'flex', flexWrap:'wrap', gap: 8 }}>
          {allLang.map(l => (
            <button key={l} onClick={()=>tog(lang,l,setLang)} className={'btn-pill' + (lang.has(l)?' active':'')}>{l}</button>
          ))}
        </div>
      </div>

      <div style={{ marginTop: 28 }}>
        <div className="meta" style={{ marginBottom: 12 }}>FIELD C · 默认场景 / DEFAULT SCENE (单选)</div>
        <div style={{ display:'flex', flexWrap:'wrap', gap: 8 }}>
          {allScene.map(s => (
            <button key={s} onClick={()=>setScene(s)} className={'btn-pill' + (scene===s?' active':'')}>{s}</button>
          ))}
        </div>
      </div>
    </div>
  );
}

function StepReady({ isMobile }) {
  return (
    <div style={{ display:'flex', flexDirection: isMobile?'column':'row', alignItems:'center', gap: isMobile?32:56, paddingTop: isMobile?20:60 }}>
      <MoodBlob size={isMobile?280:380} drift={true} geometry="blob"/>
      <div>
        <div className="meta">CHAPTER FOUR · 准备好了</div>
        <h1 className="display" style={{ fontSize: isMobile?60:120, margin: '12px 0 0' }}>
          You're <em>tuned</em>.
        </h1>
        <div className="display-cn" style={{ fontSize: isMobile?26:36, marginTop: 12 }}>
          调好了。今晚第一档电台准备启动。
        </div>
        <p style={{ marginTop: 20, fontSize: 15, lineHeight: 1.7, color:'var(--ink-2)', maxWidth: 460, fontFamily:'var(--serif-cn)' }}>
          已绑定 1 个平台 · 偏好已记录 · 你的播放历史会持续优化推荐。
          点"进入电台"——我们已经替你准备了第一首。
        </p>
        <div className="row" style={{ marginTop: 28, gap: 10, color:'var(--ink-3)' }}>
          <span className="meta">FIRST UP →</span>
          <span style={{ fontFamily:'var(--serif-en)', fontSize: 22, fontStyle:'italic', color:'var(--ink)' }}>weightless</span>
          <span style={{ fontFamily:'var(--serif-cn)', fontSize: 14, color:'var(--ink-2)' }}>· Marconi Union</span>
        </div>
      </div>
    </div>
  );
}

window.Onboarding = Onboarding;
