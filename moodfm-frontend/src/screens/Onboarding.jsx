import { useState, useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { MoodBlob, Icon, Logo } from '../components/atoms';
import { platformApi } from '../api/platform';
import { userApi } from '../api/user';

const ALL_GENRES = ['Ambient','Classical','Folk','Indie','Electronic','Jazz','Hip-Hop','Rock','Pop','R&B','Post-Rock','Bossa Nova'];
const ALL_LANGS  = ['中文','English','日本語','한국어','Français','Español','Instrumental'];
const ALL_SCENES = ['通勤','学习','跑步','写作','睡前','深夜','派对','咖啡馆'];

function tog(set, v) {
  const n = new Set(set);
  n.has(v) ? n.delete(v) : n.add(v);
  return n;
}

export default function Onboarding() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [step, setStep] = useState(Number(params.get('step')) || 1);
  const total = 4;

  const [bindings, setBindings] = useState([]);
  const [genres, setGenres] = useState(new Set(['Ambient','Folk','Indie']));
  const [langs, setLangs] = useState(new Set(['中文','English','日本語']));
  const [scene, setScene] = useState('深夜');

  useEffect(() => {
    if (step === 2) {
      platformApi.getBindings().then(res => {
        if (res.code === 200) setBindings(res.data || []);
      }).catch(() => {});
    }
  }, [step]);

  const isBound = (p) => bindings.some(b => b.platform.toUpperCase() === p.toUpperCase());

  const next = () => {
    if (step === 3) {
      localStorage.setItem('moodfm_prefs', JSON.stringify({
        genres: [...genres], langs: [...langs], defaultScene: scene,
      }));
      userApi.savePreferences({ genres: [...genres], languages: [...langs], defaultScene: scene }).catch(() => {});
    }
    if (step < total) setStep(s => s + 1);
    else navigate('/home');
  };

  const prev = () => step > 1 && setStep(s => s - 1);

  return (
    <div data-mood="dusk" className="mfm" style={{ minHeight: '100vh', position: 'relative', overflow: 'hidden' }}>
      <div className="mood-blob drift" style={{ width: 800, height: 800, right: -150, top: -200, opacity: 0.4 }} />

      {/* Top nav */}
      <div style={{ position: 'relative', zIndex: 2, padding: '28px 56px', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <div className="row" style={{ gap: 10 }}>
          <Logo size={24} />
          <div style={{ fontFamily: 'var(--serif-en)', fontSize: 20, fontStyle: 'italic' }}>MoodFM</div>
        </div>
        <div className="meta">STEP {String(step).padStart(2, '0')} / {String(total).padStart(2, '0')}</div>
        <button onClick={() => navigate('/home')} style={{ background: 'transparent', border: 'none', cursor: 'pointer' }} className="meta">SKIP →</button>
      </div>

      {/* Progress bar */}
      <div className="row" style={{ position: 'relative', zIndex: 2, gap: 4, padding: '0 56px' }}>
        {[1, 2, 3, 4].map(s => (
          <div key={s} style={{ flex: 1, height: 2, background: s <= step ? 'var(--ink)' : 'var(--rule)', transition: 'background .3s' }} />
        ))}
      </div>

      {/* Step body */}
      <div style={{ position: 'relative', zIndex: 2, padding: '40px 56px 140px' }}>
        {step === 1 && <StepWelcome />}
        {step === 2 && <StepBind isBound={isBound} onGoToBind={() => navigate('/bind?returnTo=onboarding')} />}
        {step === 3 && <StepPrefs genres={genres} setGenres={setGenres} langs={langs} setLangs={setLangs} scene={scene} setScene={setScene} />}
        {step === 4 && <StepReady />}
      </div>

      {/* Footer */}
      <div style={{
        position: 'fixed', left: 0, right: 0, bottom: 0, zIndex: 10,
        background: 'var(--bg)', borderTop: '1px solid var(--rule)',
        padding: '20px 56px', display: 'flex', alignItems: 'center', justifyContent: 'space-between',
      }}>
        <button className="btn-pill" onClick={prev} style={{ visibility: step === 1 ? 'hidden' : 'visible' }}>
          <Icon.chevL /> 上一步
        </button>
        <div className="meta" style={{ color: 'var(--ink-3)' }}>
          {step === 1 && '欢迎介绍'}
          {step === 2 && '绑定平台 · 必选'}
          {step === 3 && '偏好设置'}
          {step === 4 && '准备就绪'}
        </div>
        <button className="btn" onClick={next}>
          {step < total ? <>下一步 <Icon.arrow /></> : <>进入电台 <Icon.arrow /></>}
        </button>
      </div>
    </div>
  );
}

function StepWelcome() {
  const cards = [
    { n: '01', en: 'Read', cn: '读你', body: '说一句话、选一个场景、或在色盘上标个点。AI 帮你把感觉翻译成可听见的频率。' },
    { n: '02', en: 'Compose', cn: '调台', body: '横跨网易云与 QQ 音乐，从你的红心和全网曲库实时编排——一档只属于此刻的电台。' },
    { n: '03', en: 'Remember', cn: '记得', body: '每次收听都成为你心情地图的一笔。每周一份情绪周报，长图分享给朋友。' },
  ];
  return (
    <div>
      <div className="meta">CHAPTER ONE</div>
      <h1 className="display" style={{ fontSize: 120, margin: '12px 0 0' }}>Three <em>verbs</em>.</h1>
      <div className="display-cn" style={{ fontSize: 32, marginTop: 8, color: 'var(--ink-2)' }}>关于这台电台，三个动词。</div>
      <div style={{ marginTop: 56, display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 1, background: 'var(--rule)', border: '1px solid var(--rule)' }}>
        {cards.map(c => (
          <div key={c.n} style={{ background: 'var(--paper)', padding: '32px 28px' }}>
            <div className="meta">№ {c.n}</div>
            <div style={{ fontFamily: 'var(--serif-en)', fontSize: 60, fontStyle: 'italic', marginTop: 8 }}>
              {c.en}<span style={{ color: 'var(--ink-3)' }}>.</span>
            </div>
            <div style={{ fontFamily: 'var(--serif-cn)', fontSize: 22, fontWeight: 500, marginTop: 4 }}>{c.cn}</div>
            <p style={{ color: 'var(--ink-2)', fontSize: 14, lineHeight: 1.7, marginTop: 14 }}>{c.body}</p>
          </div>
        ))}
      </div>
    </div>
  );
}

function StepBind({ isBound, onGoToBind }) {
  const platforms = [
    { id: 'netease', logo: '网', name: '网易云音乐', en: 'NETEASE CLOUD MUSIC', recommended: true },
    { id: 'qqmusic', logo: 'Q', name: 'QQ 音乐', en: 'QQ MUSIC' },
  ];
  return (
    <div>
      <div className="meta">CHAPTER TWO · 必选 / REQUIRED</div>
      <h1 className="display" style={{ fontSize: 108, margin: '12px 0 0' }}>Plug in <em>your</em></h1>
      <h1 className="display" style={{ fontSize: 108, margin: 0 }}>listening <em>history</em>.</h1>
      <div className="display-cn" style={{ fontSize: 30, marginTop: 12, color: 'var(--ink-2)' }}>
        把你听了多年的曲库接进来——从此电台从你的红心开始。
      </div>
      <div style={{ marginTop: 48, display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
        {platforms.map(p => {
          const bound = isBound(p.id);
          return (
            <div key={p.id} style={{ padding: 24, border: '1px solid var(--rule)', borderRadius: 18, background: 'var(--paper)', position: 'relative' }}>
              {p.recommended && <div style={{ position: 'absolute', top: 14, right: 14 }} className="meta">RECOMMENDED</div>}
              <div className="row" style={{ gap: 16 }}>
                <div style={{
                  width: 56, height: 56, borderRadius: '50%', flexShrink: 0,
                  background: bound ? 'linear-gradient(135deg, var(--mood-a), var(--mood-c))' : 'var(--bg-2)',
                  color: bound ? '#fff' : 'var(--ink-3)',
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  fontFamily: 'var(--serif-cn)', fontSize: 24, fontWeight: 600,
                }}>{p.logo}</div>
                <div style={{ flex: 1 }}>
                  <div style={{ fontFamily: 'var(--serif-cn)', fontSize: 20, fontWeight: 500 }}>{p.name}</div>
                  <div className="meta" style={{ marginTop: 2 }}>{p.en}</div>
                </div>
              </div>
              <div className="between" style={{ marginTop: 24 }}>
                <div className="meta">STATUS · <span style={{ color: bound ? 'var(--ink)' : 'var(--ink-3)' }}>{bound ? '已绑定' : '未绑定'}</span></div>
                {bound
                  ? <span className="meta" style={{ padding: '4px 10px', border: '1px solid var(--ink)', borderRadius: 999 }}>BOUND ✓</span>
                  : <button className="btn" style={{ height: 38, padding: '0 16px' }} onClick={onGoToBind}>绑定 →</button>
                }
              </div>
            </div>
          );
        })}
      </div>
      <div className="meta" style={{ marginTop: 28, color: 'var(--ink-3)' }}>
        · 我们仅读取曲库与红心 · Cookie 加密存储 · 随时可解绑
      </div>
    </div>
  );
}

function StepPrefs({ genres, setGenres, langs, setLangs, scene, setScene }) {
  return (
    <div>
      <div className="meta">CHAPTER THREE · 偏好</div>
      <h1 className="display" style={{ fontSize: 96, margin: '12px 0 0' }}>What's in your</h1>
      <h1 className="display" style={{ fontSize: 96, margin: 0 }}><em>weather</em>?</h1>
      <div className="display-cn" style={{ fontSize: 26, marginTop: 12, color: 'var(--ink-2)' }}>说说你的"音乐天气"——这些会成为电台的初始风向。</div>

      <div style={{ marginTop: 36 }}>
        <div className="meta" style={{ marginBottom: 12 }}>FIELD A · 流派 / GENRES（多选）</div>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
          {ALL_GENRES.map(g => (
            <button key={g} onClick={() => setGenres(tog(genres, g))} className={`btn-pill${genres.has(g) ? ' active' : ''}`}>{g}</button>
          ))}
        </div>
      </div>

      <div style={{ marginTop: 28 }}>
        <div className="meta" style={{ marginBottom: 12 }}>FIELD B · 语言 / LANGUAGES（多选）</div>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
          {ALL_LANGS.map(l => (
            <button key={l} onClick={() => setLangs(tog(langs, l))} className={`btn-pill${langs.has(l) ? ' active' : ''}`}>{l}</button>
          ))}
        </div>
      </div>

      <div style={{ marginTop: 28 }}>
        <div className="meta" style={{ marginBottom: 12 }}>FIELD C · 默认场景 / DEFAULT SCENE（单选）</div>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
          {ALL_SCENES.map(s => (
            <button key={s} onClick={() => setScene(s)} className={`btn-pill${scene === s ? ' active' : ''}`}>{s}</button>
          ))}
        </div>
      </div>
    </div>
  );
}

function StepReady() {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 56, paddingTop: 60 }}>
      <MoodBlob size={380} drift={true} geometry="blob" />
      <div>
        <div className="meta">CHAPTER FOUR · 准备好了</div>
        <h1 className="display" style={{ fontSize: 120, margin: '12px 0 0' }}>You're <em>tuned</em>.</h1>
        <div className="display-cn" style={{ fontSize: 36, marginTop: 12 }}>调好了。今晚第一档电台准备启动。</div>
        <p style={{ marginTop: 20, fontSize: 15, lineHeight: 1.7, color: 'var(--ink-2)', maxWidth: 460, fontFamily: 'var(--serif-cn)' }}>
          偏好已记录 · 你的播放历史会持续优化推荐。<br />
          点"进入电台"——从你的第一首开始。
        </p>
      </div>
    </div>
  );
}
