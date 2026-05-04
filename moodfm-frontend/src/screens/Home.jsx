import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { MoodBlob, MoodWheel, Icon, Logo } from '../components/atoms';
import { radioApi } from '../api/radio';
import { useAuthStore } from '../store/authStore';
import { usePlayerStore } from '../store/playerStore';

const SCENES = ['通勤', '学习', '跑步', '写作', '睡前', '派对', '深夜'];

function formatRelTime(t) {
  if (!t) return '—';
  const diff = Date.now() - new Date(t).getTime();
  if (diff < 3600000) return `${Math.floor(diff / 60000)} 分钟前`;
  if (diff < 86400000) return `${Math.floor(diff / 3600000)} 小时前`;
  return new Date(t).toLocaleDateString('zh-CN', { month: 'long', day: 'numeric' });
}

function MiniPlayer({ song, playing, onPlay, onNext, onPrev, onToggle }) {
  if (!song) return null;
  return (
    <div style={{
      position:'fixed', left:24, right:24, bottom:18, height:64,
      background:'var(--ink)', color:'var(--bg)', borderRadius:999,
      padding:'0 8px', display:'flex', alignItems:'center', gap:12,
      cursor:'pointer', boxShadow:'0 12px 40px rgba(0,0,0,0.18)', zIndex:10,
    }}>
      <MoodBlob size={48} drift={true} geometry="circle"/>
      <div style={{ flex:1, minWidth:0 }}>
        <div style={{ fontFamily:'var(--serif-cn)', fontSize:14, fontWeight:500, whiteSpace:'nowrap', overflow:'hidden', textOverflow:'ellipsis' }}>
          {song.title || song.name || '—'}
        </div>
        <div className="mono" style={{ fontSize:10, letterSpacing:'.14em', opacity:.7, marginTop:2 }}>
          {song.artist || song.artistName || '—'} · {song.platform || 'NETEASE'}
        </div>
      </div>
      <div className="row" style={{ gap:4, paddingRight:4 }}>
        <button onClick={onPrev} style={iconBtnDark}><Icon.prev/></button>
        <button onClick={onToggle} style={{ ...iconBtnDark, background:'var(--bg)', color:'var(--ink)' }}>
          {playing ? <Icon.pause/> : <Icon.play/>}
        </button>
        <button onClick={onNext} style={iconBtnDark}><Icon.next/></button>
      </div>
    </div>
  );
}
const iconBtnDark = { width:36, height:36, borderRadius:'50%', border:'none', background:'transparent', color:'var(--bg)', cursor:'pointer', display:'inline-flex', alignItems:'center', justifyContent:'center' };

export default function Home() {
  const navigate = useNavigate();
  const user = useAuthStore(s => s.user);
  const logout = useAuthStore(s => s.logout);
  const { session, queue, currentIndex, playing, setSession, setPlaying, next, prev } = usePlayerStore();
  const currentSong = queue[currentIndex] || null;

  const [input, setInput] = useState('');
  const [scene, setScene] = useState('通勤');
  const [moodPos, setMoodPos] = useState({ x: 0.62, y: 0.32 });
  const [loading, setLoading] = useState(false);
  const [sessions, setSessions] = useState([]);

  useEffect(() => {
    radioApi.getSessions(4).then(res => {
      if (res.code === 200) setSessions(res.data || []);
    }).catch(() => {});
  }, []);

  const hour = new Date().getHours();
  const greet = hour < 11 ? '早安' : hour < 18 ? '下午好' : hour < 23 ? '晚上好' : '深夜好';
  const greetEn = hour < 11 ? 'Good morning' : hour < 18 ? 'Good afternoon' : hour < 23 ? 'Good evening' : 'Late night';

  const FOR_THE_HOUR = hour < 6
    ? { range: '00:00 – 06:00', name: '深夜信号', desc: '静下来，只剩你和音乐。', gradient: 'linear-gradient(135deg, #1a1a2e 0%, #16213e 60%, #0f3460 100%)', light: false }
    : hour < 11
    ? { range: '06:00 – 11:00', name: '晨间电台', desc: '新的一天，从一段旋律开始。', gradient: 'linear-gradient(135deg, #ffd89b 0%, #f5a623 50%, #f093fb 100%)', light: true }
    : hour < 18
    ? { range: '11:00 – 18:00', name: '午后频率', desc: '专注与漫游之间的那道频率。', gradient: 'linear-gradient(135deg, #4facfe 0%, #43e97b 100%)', light: true }
    : hour < 23
    ? { range: '18:00 – 23:00', name: '黄昏电台', desc: '城市开始沉下去，音乐浮起来。', gradient: 'linear-gradient(135deg, #f7971e 0%, #f64f59 100%)', light: false }
    : { range: '23:00 – 00:00', name: '深夜信号', desc: '让呼吸放慢到音乐的速度。', gradient: 'linear-gradient(135deg, #1a1a2e 0%, #16213e 60%, #0f3460 100%)', light: false };

  const startRadio = async (method = 'text') => {
    setLoading(true);
    try {
      const payload =
        method === 'text'  ? { inputType: 'TEXT', textInput: input || '随机心情' } :
        method === 'scene' ? { inputType: 'SCENE', scene } :
        method === 'mood'  ? { inputType: 'MOOD_WHEEL', moodX: moodPos.x, moodY: moodPos.y } :
                             { inputType: 'SILENT' };
      const res = await radioApi.startRadio(payload);
      if (res.code === 200) {
        const { sessionId, moodSummary, scene, playlistId, songs } = res.data;
        setSession({ id: sessionId, moodSummary, scene, playlistId }, songs || []);
        navigate('/player');
      }
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div data-mood="dusk" className="mfm" style={{ minHeight:'100vh' }}>
      <div className="mood-blob drift" style={{ width:760, height:760, right:-220, top:-260, opacity:0.4 }}/>

      {/* Nav */}
      <div style={{
        position:'sticky', top:0, zIndex:5, background:'var(--bg)',
        padding:'22px 56px', borderBottom:'1px solid var(--rule)',
        display:'flex', alignItems:'center', justifyContent:'space-between',
      }}>
        <div className="row" style={{ gap:10 }}>
          <Logo size={24}/>
          <div style={{ fontFamily:'var(--serif-en)', fontSize:20, fontStyle:'italic' }}>MoodFM</div>
          <div className="row mono" style={{ marginLeft:32, gap:22, fontSize:11, letterSpacing:'.16em', textTransform:'uppercase', color:'var(--ink-2)' }}>
            <span style={{ color:'var(--ink)' }}>· 电台 Radio</span>
            <span style={{ cursor:'pointer' }} onClick={() => navigate('/bind')}>绑定 Bind</span>
            <span style={{ cursor:'pointer' }} onClick={() => navigate('/calendar')}>历史 History</span>
            <span style={{ cursor:'pointer' }} onClick={() => navigate('/insights')}>画像 Insights</span>
          </div>
        </div>
        <div className="row" style={{ gap:14 }}>
          <span className="mono" style={{ fontSize:10, letterSpacing:'.14em', color:'var(--ink-3)' }}>NETEASE · 默认</span>
          <Icon.search/>
          <div style={{ width:30, height:30, borderRadius:'50%', background:'var(--bg-2)', border:'1px solid var(--rule)', fontSize:12, display:'flex', alignItems:'center', justifyContent:'center', fontFamily:'var(--serif-cn)', cursor:'pointer' }} onClick={logout}>
            {user?.nickname?.[0] || '我'}
          </div>
        </div>
      </div>

      <div style={{
        position:'relative', zIndex:2, padding:'40px 56px 80px',
        display:'grid', gridTemplateColumns:'1.3fr 1fr', gap:56,
      }}>
        {/* LEFT */}
        <div>
          <div className="meta">{greetEn.toUpperCase()} · {new Date().toLocaleDateString('zh-CN',{month:'long',day:'numeric'})}</div>
          <h1 className="display" style={{ fontSize:96, margin:'8px 0 0' }}>
            {greetEn}<em style={{ color:'var(--ink-3)' }}>,</em>
          </h1>
          <div className="display-cn" style={{ fontSize:40, marginTop:4 }}>
            {greet}，{user?.nickname || '朋友'}。<span style={{ color:'var(--ink-3)' }}>今晚想听点什么？</span>
          </div>

          {/* Text input */}
          <div style={{ marginTop:36 }}>
            <div className="meta" style={{ marginBottom:10 }}>方式 A · IN YOUR OWN WORDS</div>
            <div style={{ position:'relative' }}>
              <textarea className="field" placeholder="此刻你在哪里、心里是什么颜色？比如：加班到很晚，需要一点不打扰我的电流声…"
                value={input} onChange={e=>setInput(e.target.value)}
                style={{ paddingRight:120, fontSize:16, minHeight:120 }}/>
              <button className="btn" onClick={() => startRadio('text')} disabled={loading}
                style={{ position:'absolute', right:10, bottom:10, height:40, padding:'0 16px', opacity:loading?0.7:1 }}>
                {loading ? '调台中…' : <>调台 <Icon.arrow/></>}
              </button>
            </div>
          </div>

          {/* Scenes */}
          <div style={{ marginTop:28 }}>
            <div className="meta" style={{ marginBottom:10 }}>方式 B · PRESET SCENES</div>
            <div style={{ display:'flex', flexWrap:'wrap', gap:8 }}>
              {SCENES.map(s => (
                <button key={s} className={'btn-pill'+(s===scene?' active':'')} onClick={() => { setScene(s); startRadio('scene'); }}>
                  {s}
                </button>
              ))}
            </div>
          </div>

          {/* Silent start */}
          <div style={{ marginTop:28, padding:'22px 26px', border:'1px solid var(--rule)', borderRadius:18, display:'flex', justifyContent:'space-between', alignItems:'center', gap:16, background:'var(--paper)' }}>
            <div>
              <div className="serif-en" style={{ fontSize:30 }}>Just play.</div>
              <div style={{ fontFamily:'var(--serif-cn)', fontSize:14, color:'var(--ink-2)', marginTop:2 }}>什么都别问 · 直接给我一个电台</div>
            </div>
            <button className="btn" onClick={() => startRadio('silent')} disabled={loading}>
              启动 <Icon.play/>
            </button>
          </div>
        </div>

        {/* RIGHT */}
        <div>
          <div className="meta" style={{ marginBottom:12 }}>方式 C · MOOD COMPASS · 心情色盘</div>
          <div style={{ display:'flex', justifyContent:'center' }}>
            <MoodWheel size={340} geometry="circle" value={moodPos} onChange={setMoodPos}/>
          </div>
          <div className="meta" style={{ textAlign:'center', marginTop:14, color:'var(--ink-2)' }}>
            DRAG · 拖动球点选择此刻的心情坐标
          </div>
          <div style={{ marginTop:16, textAlign:'center' }}>
            <button className="btn" onClick={() => startRadio('mood')} disabled={loading}>
              按心情调台 <Icon.arrow/>
            </button>
          </div>

          {/* FOR THE HOUR */}
          <div style={{ marginTop:28 }}>
            <div className="meta" style={{ marginBottom:10 }}>今日推荐 · FOR THE HOUR</div>
            <div style={{
              borderRadius:18, padding:'20px 22px', background:FOR_THE_HOUR.gradient,
              color: FOR_THE_HOUR.light ? 'rgba(0,0,0,0.85)' : '#fff',
              position:'relative', overflow:'hidden',
            }}>
              <div style={{ fontSize:10, letterSpacing:'.16em', fontFamily:'var(--mono)', opacity:0.7, textTransform:'uppercase' }}>
                {FOR_THE_HOUR.range}
              </div>
              <div style={{ fontFamily:'var(--serif-en)', fontSize:32, fontStyle:'italic', marginTop:6, lineHeight:1.1 }}>
                {FOR_THE_HOUR.name}
              </div>
              <div style={{ fontFamily:'var(--serif-cn)', fontSize:13, marginTop:8, opacity:0.85, lineHeight:1.6 }}>
                {FOR_THE_HOUR.desc}
              </div>
              <button
                className="btn"
                onClick={() => startRadio('scene')}
                disabled={loading}
                style={{
                  marginTop:16, height:36, padding:'0 14px', fontSize:12,
                  background: FOR_THE_HOUR.light ? 'rgba(0,0,0,0.15)' : 'rgba(255,255,255,0.2)',
                  color: FOR_THE_HOUR.light ? 'rgba(0,0,0,0.85)' : '#fff',
                  border: `1px solid ${FOR_THE_HOUR.light ? 'rgba(0,0,0,0.2)' : 'rgba(255,255,255,0.3)'}`,
                  backdropFilter:'blur(8px)',
                }}
              >
                播放此时段 <Icon.arrow/>
              </button>
            </div>
          </div>

          {/* Continue current session */}
          <div style={{ marginTop:28 }}>
            <div className="meta" style={{ marginBottom:10 }}>继续 · CONTINUE</div>
            {session ? (
              <div className="row" style={{ gap:12, padding:14, border:'1px solid var(--rule)', borderRadius:16, alignItems:'center', cursor:'pointer' }} onClick={() => navigate('/player')}>
                <MoodBlob size={56} drift={false} geometry="circle"/>
                <div style={{ flex:1 }}>
                  <div style={{ fontFamily:'var(--serif-cn)', fontSize:15, fontWeight:500 }}>当前电台</div>
                  <div className="meta" style={{ marginTop:2 }}>剩 {queue.length - currentIndex} 首</div>
                </div>
                <button className="btn-pill"><Icon.play/></button>
              </div>
            ) : (
              <div style={{ padding:14, border:'1px dashed var(--rule)', borderRadius:16, textAlign:'center', fontFamily:'var(--serif-cn)', fontSize:14, color:'var(--ink-3)' }}>
                尚无进行中的电台
              </div>
            )}
          </div>

          {/* Recent sessions — 2×2 grid */}
          {sessions.length > 0 && (
            <div style={{ marginTop:28 }}>
              <div className="meta" style={{ marginBottom:10 }}>最近 · RECENT</div>
              <div style={{ display:'grid', gridTemplateColumns:'1fr 1fr', gap:10 }}>
                {sessions.map((s, i) => {
                  const moods = ['dusk','night','dawn','day'];
                  return (
                    <div key={s.id} data-mood={moods[i % moods.length]}
                      style={{ padding:'14px 16px', border:'1px solid var(--rule)', borderRadius:16, cursor:'default', background:'var(--paper)' }}>
                      <MoodBlob size={36} drift={false} geometry="circle"/>
                      <div className="meta" style={{ marginTop:10, opacity:0.6 }}>{s.scene || '—'}</div>
                      <div style={{ fontFamily:'var(--serif-cn)', fontSize:13, fontWeight:500, marginTop:4, whiteSpace:'nowrap', overflow:'hidden', textOverflow:'ellipsis' }}>
                        {s.moodSummary || s.scene || '即兴电台'}
                      </div>
                      <div className="meta" style={{ marginTop:6, opacity:0.5 }}>
                        {formatRelTime(s.startedAt)}
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          )}
        </div>
      </div>

      <MiniPlayer song={currentSong} playing={playing} onToggle={() => setPlaying(!playing)} onNext={next} onPrev={prev}/>
    </div>
  );
}
