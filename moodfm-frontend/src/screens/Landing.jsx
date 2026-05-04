import { useNavigate } from 'react-router-dom';
import { MoodBlob, Icon, Logo } from '../components/atoms';

export default function Landing() {
  const navigate = useNavigate();

  return (
    <div data-mood="dusk" className="mfm" style={{ minHeight: '100vh', position: 'relative', overflow: 'hidden' }}>
      <div className="mood-blob drift" style={{ width: 900, height: 900, right: -200, top: -260, opacity: 0.55 }}/>
      <div className="mood-blob drift" style={{ width: 620, height: 620, left: -160, bottom: -220, opacity: 0.45 }}/>

      {/* Top bar */}
      <div style={{ position:'relative', zIndex:2, padding:'28px 56px', display:'flex', alignItems:'center', justifyContent:'space-between' }}>
        <div className="row" style={{ gap: 10 }}>
          <Logo size={26}/>
          <div style={{ fontFamily:'var(--serif-en)', fontSize:22, fontStyle:'italic' }}>
            Mood<span style={{ color:'var(--ink-3)' }}>FM</span>
          </div>
        </div>
        <div className="row mono" style={{ gap:28, fontSize:11, letterSpacing:'0.18em', textTransform:'uppercase', color:'var(--ink-2)' }}>
          <span>About</span><span>How it works</span><span>Manifesto</span>
        </div>
        <div className="row" style={{ gap:10 }}>
          <button className="btn-pill" onClick={() => navigate('/auth')}>登录 · Sign in</button>
          <button className="btn" onClick={() => navigate('/auth?mode=signup')}>开始 · Start</button>
        </div>
      </div>

      {/* Hero */}
      <div style={{
        position:'relative', zIndex:2, padding:'40px 56px 0',
        display:'grid', gridTemplateColumns:'1.3fr 1fr', gap:56, alignItems:'start',
      }}>
        <div>
          <div className="meta" style={{ marginBottom:22 }}>ISSUE №01 · 2026 / SPRING — A PRIVATE RADIO STATION</div>
          <h1 className="display" style={{ fontSize:132, margin:0, color:'var(--ink)' }}>
            <span style={{ display:'block' }}>Tune into</span>
            <span style={{ display:'block' }}>the <em style={{ color:'transparent', WebkitTextStroke:'1px var(--ink)' }}>weather</em></span>
            <span style={{ display:'block' }}>of your <em>heart.</em></span>
          </h1>
          <div className="display-cn" style={{ fontSize:44, marginTop:28, color:'var(--ink)', maxWidth:620 }}>
            为此刻的你，<br/>调一台只属于你的电台。
          </div>
          <p style={{ marginTop:32, maxWidth:460, color:'var(--ink-2)', fontSize:17, lineHeight:1.6 }}>
            MoodFM 读取你的情绪，跨越网易云与 QQ 音乐，从你的曲库与海量乐池中实时编排。
            一段文字、一张色盘、或者一个按钮——交给我们，剩下的就听吧。
          </p>
          <div className="row" style={{ gap:12, marginTop:40, flexWrap:'wrap' }}>
            <button className="btn" onClick={() => navigate('/auth')}>进入电台 · Enter <Icon.arrow/></button>
            <button className="btn btn-ghost">观看示例 · Watch demo</button>
          </div>
          <div className="meta" style={{ marginTop:56, color:'var(--ink-3)' }}>▼ SCROLL · 三种方式开始你的电台</div>
        </div>

        <div style={{ display:'flex', justifyContent:'flex-end' }}>
          <MoodBlob size={460} drift={true} geometry="blob">
            <div style={{
              position:'absolute', inset:0, display:'flex', alignItems:'center', justifyContent:'center',
              flexDirection:'column', color:'rgba(255,255,255,0.92)', textShadow:'0 1px 12px rgba(0,0,0,0.2)',
            }}>
              <div className="mono" style={{ fontSize:11, letterSpacing:'0.2em', opacity:.85 }}>NOW PLAYING · 北京 · 23:14</div>
              <div className="serif-en" style={{ fontSize:56, marginTop:8 }}>weightless</div>
              <div style={{ fontFamily:'var(--serif-cn)', fontSize:16, marginTop:4, opacity:.85 }}>晚归 · Late return</div>
            </div>
          </MoodBlob>
        </div>
      </div>

      {/* Features strip */}
      <hr className="rule" style={{ margin:'60px 56px 0' }}/>
      <div style={{ position:'relative', zIndex:2, padding:'32px 56px 80px', display:'grid', gridTemplateColumns:'repeat(3, 1fr)', gap:0 }}>
        {[
          { n:'01', en:'Read', cn:'读你', body:'一句话、一个场景、或一片色盘——AI 把你此刻的情绪翻译成可听见的频率。' },
          { n:'02', en:'Compose', cn:'调台', body:'横跨多个平台，从你的红心、推荐池与全网曲库里编排一档专属节目。' },
          { n:'03', en:'Remember', cn:'记得', body:'每次收听都成为你心情地图的一部分，每周生成一份情绪周报。' },
        ].map((f, i) => (
          <div key={f.n} style={{ padding:'0 28px 0 0', borderRight: i<2?'1px solid var(--rule)':'none', paddingLeft: i>0?28:0 }}>
            <div className="meta">CHAPTER {f.n}</div>
            <div className="serif-en" style={{ fontSize:48, marginTop:6, color:'var(--ink)' }}>{f.en}<span style={{ color:'var(--ink-3)' }}>.</span></div>
            <div style={{ fontFamily:'var(--serif-cn)', fontSize:22, fontWeight:500, marginTop:4 }}>{f.cn}</div>
            <p style={{ color:'var(--ink-2)', fontSize:14, lineHeight:1.6, marginTop:12, maxWidth:320 }}>{f.body}</p>
          </div>
        ))}
      </div>

      {/* Footer */}
      <div style={{
        position:'relative', zIndex:2, padding:'14px 56px', borderTop:'1px solid var(--rule)',
        display:'flex', justifyContent:'space-between',
        fontFamily:'var(--mono)', fontSize:11, letterSpacing:'0.18em', textTransform:'uppercase', color:'var(--ink-3)',
      }}>
        <span>© MoodFM 2026 / a private station</span>
        <span>网易云音乐 · QQ 音乐 · BiliBili Music</span>
        <span>v1.0 — issue spring</span>
      </div>
    </div>
  );
}
