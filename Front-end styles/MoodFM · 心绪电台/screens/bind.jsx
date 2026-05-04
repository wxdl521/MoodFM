/* global React, ScreenFrame, MoodBlob, Icon, Logo */
// P5 · Platform Bind page (with QR / phone / cookie tabs)

function Bind({ device='desktop', theme, mood, onBack, onDone }) {
  const isMobile = device === 'mobile';
  const W = isMobile ? 390 : 1440;
  const H = isMobile ? 844 : 900;
  const [openModal, setOpenModal] = React.useState(true);
  const [tab, setTab] = React.useState('qr');
  const [platform, setPlatform] = React.useState('网易云音乐');

  return (
    <ScreenFrame theme={theme} mood={mood} width={W} height={H} label="02d Bind" scroll={isMobile}>
      <div className="mood-blob drift" style={{ width: 700, height: 700, right: -200, top: -200, opacity: 0.3 }}/>

      {/* Top */}
      <div style={{ position:'sticky', top:0, zIndex:5, background:'var(--bg)', padding: isMobile?'16px 22px':'22px 56px', borderBottom:'1px solid var(--rule)', display:'flex', alignItems:'center', justifyContent:'space-between' }}>
        <button onClick={onBack} className="btn-pill"><Icon.chevL/> 返回</button>
        <div className="meta">PLATFORM · 平台绑定</div>
        <button onClick={onDone} className="btn-pill">完成 →</button>
      </div>

      <div style={{ position:'relative', zIndex: 2, padding: isMobile?'24px 22px 60px':'48px 56px 80px', display: 'grid', gridTemplateColumns: isMobile?'1fr':'1.2fr 1fr', gap: 48 }}>
        {/* LEFT: header + cards */}
        <div>
          <div className="meta">SECTION II · 接入</div>
          <h1 className="display" style={{ fontSize: isMobile?52:108, margin:'12px 0 0' }}>
            Plug in.
          </h1>
          <div className="display-cn" style={{ fontSize: isMobile?22:32, marginTop: 8, color:'var(--ink-2)' }}>
            把你的曲库接进来。
          </div>
          <p style={{ marginTop: 18, fontSize: 14, lineHeight: 1.7, color:'var(--ink-2)', maxWidth: 480, fontFamily:'var(--serif-cn)' }}>
            支持网易云音乐与 QQ 音乐。我们读取你的曲库与红心，跨平台编排——所有凭证都本地加密，可随时解绑。
          </p>

          <div style={{ marginTop: 32, display:'grid', gridTemplateColumns: '1fr', gap: 14 }}>
            <BindCard logo="网" name="网易云音乐" en="NETEASE CLOUD MUSIC" status="未绑定" cookie={null} onBind={()=>{ setPlatform('网易云音乐'); setOpenModal(true); }}/>
            <BindCard logo="Q" name="QQ 音乐" en="QQ MUSIC" status="已绑定" cookie="34d" user="ji****ye" onBind={()=>{ setPlatform('QQ 音乐'); setOpenModal(true); }} bound/>
            <BindCard logo="B" name="Bilibili 音乐" en="BILIBILI MUSIC" status="即将上线" disabled/>
          </div>

          <details style={{ marginTop: 28, padding: 18, border:'1px solid var(--rule)', borderRadius: 14, background:'var(--paper)' }}>
            <summary style={{ cursor:'pointer', fontFamily:'var(--serif-cn)', fontSize: 14 }}>
              <span className="meta" style={{ marginRight: 10 }}>·</span>
              账号信息如何被加密保护？
            </summary>
            <div style={{ marginTop: 12, fontSize: 13, lineHeight: 1.7, color:'var(--ink-2)', fontFamily:'var(--serif-cn)' }}>
              所有 Cookie 与 Token 仅存储在你的本地与端到端加密的数据库；服务端永不留存明文。每 30 天自动续期一次，过期会主动提醒你重绑。
            </div>
          </details>
        </div>

        {/* RIGHT: live modal preview (always-open dialog feel) */}
        {!isMobile && (
          <div style={{ position:'sticky', top: 100, alignSelf:'start' }}>
            <BindModal tab={tab} setTab={setTab} platform={platform} inline/>
          </div>
        )}
      </div>

      {/* Mobile modal — full screen */}
      {isMobile && openModal && (
        <div style={{ position:'fixed', inset: 0, background:'rgba(0,0,0,0.4)', zIndex: 50, display:'flex', alignItems:'flex-end' }}>
          <div style={{ width:'100%', background:'var(--bg)', borderTopLeftRadius: 24, borderTopRightRadius: 24, maxHeight: '88vh', overflow:'auto' }}>
            <BindModal tab={tab} setTab={setTab} platform={platform} onClose={()=>setOpenModal(false)}/>
          </div>
        </div>
      )}
    </ScreenFrame>
  );
}

function BindCard({ logo, name, en, status, cookie, user, onBind, bound, disabled }) {
  return (
    <div style={{
      padding: 20, border:'1px solid var(--rule)', borderRadius: 18,
      background:'var(--paper)', position:'relative',
      opacity: disabled ? 0.5 : 1,
    }}>
      <div className="row" style={{ gap: 14 }}>
        <div style={{
          width: 48, height: 48, borderRadius: '50%',
          background: bound ? 'linear-gradient(135deg, var(--mood-a), var(--mood-c))' : 'var(--bg-2)',
          color: bound ? '#fff' : 'var(--ink-3)',
          display:'flex', alignItems:'center', justifyContent:'center',
          fontFamily:'var(--serif-cn)', fontSize: 20, fontWeight: 600,
          flexShrink: 0,
        }}>{logo}</div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div className="row" style={{ gap: 8 }}>
            <div style={{ fontFamily:'var(--serif-cn)', fontSize: 18, fontWeight: 500 }}>{name}</div>
            {bound && <span className="meta" style={{ padding:'2px 8px', border:'1px solid var(--ink)', borderRadius: 999, color:'var(--ink)' }}>BOUND</span>}
          </div>
          <div className="meta" style={{ marginTop: 2 }}>{en}</div>
        </div>
        {!disabled && (
          <button onClick={onBind} className={bound?'btn-pill':'btn'} style={{ height: 38, padding:'0 16px' }}>
            {bound ? '重新绑定' : '绑定 →'}
          </button>
        )}
      </div>
      <div className="row" style={{ marginTop: 16, gap: 24, paddingTop: 16, borderTop: '1px dashed var(--rule)' }}>
        <Stat label="状态" value={status}/>
        {bound && <Stat label="账号" value={user}/>}
        {bound && <Stat label="Cookie 剩余" value={cookie} warn={parseInt(cookie)<7}/>}
      </div>
    </div>
  );
}

function Stat({ label, value, warn }) {
  return (
    <div>
      <div className="meta" style={{ color: warn?'var(--mood-b)':'var(--ink-3)' }}>{label}</div>
      <div style={{ fontFamily:'var(--mono)', fontSize: 13, marginTop: 4, color: warn?'var(--mood-b)':'var(--ink)' }}>{value}</div>
    </div>
  );
}

function BindModal({ tab, setTab, platform, onClose, inline }) {
  return (
    <div style={{
      background:'var(--paper)',
      borderRadius: inline ? 20 : 0,
      border: inline ? '1px solid var(--rule)' : 'none',
      padding: 28,
    }}>
      <div className="between" style={{ marginBottom: 18 }}>
        <div>
          <div className="meta">PLATFORM</div>
          <div className="display" style={{ fontSize: 36, marginTop: 4 }}>
            <em>{platform}</em>
          </div>
        </div>
        {onClose && <button onClick={onClose} className="btn-pill">×</button>}
      </div>

      {/* Tabs */}
      <div className="row" style={{ gap: 0, borderBottom: '1px solid var(--rule)', marginBottom: 24 }}>
        {[
          {k:'qr', l:'扫码登录', e:'QR'},
          {k:'phone', l:'手机号', e:'PHONE'},
          {k:'cookie', l:'Cookie', e:'ADV'},
        ].map(t => (
          <button key={t.k} onClick={()=>setTab(t.k)} style={{
            background:'transparent', border:'none', padding:'12px 14px',
            cursor:'pointer', position:'relative',
            fontFamily:'var(--serif-cn)', fontSize: 14,
            color: tab===t.k ? 'var(--ink)' : 'var(--ink-3)',
          }}>
            <span className="mono" style={{ fontSize: 10, letterSpacing:'.16em', color:'var(--ink-3)', marginRight: 6 }}>{t.e}</span>
            {t.l}
            {tab===t.k && <div style={{ position:'absolute', left: 0, right: 0, bottom: -1, height: 2, background:'var(--ink)' }}/>}
          </button>
        ))}
      </div>

      {tab === 'qr' && <QRBlock/>}
      {tab === 'phone' && <PhoneBlock/>}
      {tab === 'cookie' && <CookieBlock/>}
    </div>
  );
}

function QRBlock() {
  const [count, setCount] = React.useState(67);
  const [status, setStatus] = React.useState('waiting'); // waiting | scanned | success | expired
  React.useEffect(()=>{
    if (status !== 'waiting') return;
    const t = setInterval(()=>setCount(c=>{
      if (c <= 1) { clearInterval(t); setStatus('expired'); return 0; }
      return c - 1;
    }), 1000);
    return ()=>clearInterval(t);
  }, [status]);

  const refresh = () => { setCount(90); setStatus('waiting'); };

  return (
    <div style={{ textAlign:'center' }}>
      <div style={{ display:'inline-block', position:'relative', padding: 24, border:'1px solid var(--rule)', borderRadius: 18, background:'var(--bg)' }}>
        {/* Fake QR */}
        <div style={{ width: 200, height: 200, position:'relative', background:'#fff', padding: 8 }}>
          <FakeQR/>
          {status === 'expired' && (
            <div style={{ position:'absolute', inset:0, background:'rgba(255,255,255,0.92)', display:'flex', flexDirection:'column', alignItems:'center', justifyContent:'center' }}>
              <div className="meta">QR EXPIRED</div>
              <div style={{ fontFamily:'var(--serif-cn)', fontSize: 14, marginTop: 4, color:'var(--ink-2)' }}>二维码已过期</div>
              <button onClick={refresh} className="btn" style={{ marginTop: 12, height: 36 }}>刷新</button>
            </div>
          )}
        </div>
      </div>
      <div className="meta" style={{ marginTop: 18 }}>
        STATUS · <span style={{ color: status==='waiting'?'var(--ink)':status==='expired'?'var(--mood-b)':'var(--mood-c)' }}>
          {status==='waiting' && '等待扫码'}
          {status==='scanned' && '已扫码 · 请在手机上确认'}
          {status==='success' && '登录成功'}
          {status==='expired' && '二维码已过期'}
        </span>
      </div>
      {status==='waiting' && (
        <div style={{ marginTop: 8, fontFamily:'var(--mono)', fontSize: 13, color:'var(--ink-2)' }}>
          {count}s 后过期 · refresh in {count}s
        </div>
      )}
      <div style={{ marginTop: 16, fontFamily:'var(--serif-cn)', fontSize: 13, color:'var(--ink-2)' }}>
        打开网易云 App → 我的 → 扫一扫
      </div>
    </div>
  );
}

function FakeQR() {
  // procedurally generate a 17×17 grid for visual QR feel
  const cells = [];
  for (let y=0;y<17;y++){
    const row=[];
    for (let x=0;x<17;x++){
      const corner = (x<3 && y<3) || (x>13 && y<3) || (x<3 && y>13);
      const fill = corner ? ((x===0||x===2||y===0||y===2||(x===14||x===16)||(y===14||y===16))?'#000': (x===1&&y===1)||(x===15&&y===1)||(x===1&&y===15)?'#000':'#fff') : (Math.random()>0.5?'#000':'#fff');
      row.push(fill);
    }
    cells.push(row);
  }
  return (
    <svg viewBox="0 0 17 17" width="184" height="184">
      {cells.map((row,y)=>row.map((c,x)=>(
        <rect key={x+''+y} x={x} y={y} width="1" height="1" fill={c}/>
      )))}
      {/* finder corners */}
      {[[0,0],[10,0],[0,10]].map(([cx,cy],i)=>(
        <g key={i}>
          <rect x={cx} y={cy} width="7" height="7" fill="#000"/>
          <rect x={cx+1} y={cy+1} width="5" height="5" fill="#fff"/>
          <rect x={cx+2} y={cy+2} width="3" height="3" fill="#000"/>
        </g>
      ))}
    </svg>
  );
}

function PhoneBlock() {
  const [phone, setPhone] = React.useState('138 ████ ██88');
  const [code, setCode] = React.useState('');
  const [count, setCount] = React.useState(0);
  const send = () => { if (count>0) return; setCount(60); const t=setInterval(()=>setCount(s=>{ if (s<=1){clearInterval(t); return 0;} return s-1;}), 1000); };

  return (
    <div>
      <div style={{ marginBottom: 14 }}>
        <div className="meta" style={{ marginBottom: 8 }}>手机号 · PHONE</div>
        <input className="field" value={phone} onChange={e=>setPhone(e.target.value)} placeholder="+86 138 ····"/>
      </div>
      <div style={{ marginBottom: 18, position:'relative' }}>
        <div className="meta" style={{ marginBottom: 8 }}>验证码 · OTP</div>
        <input className="field" value={code} onChange={e=>setCode(e.target.value)} placeholder="6 位" style={{ paddingRight: 130 }}/>
        <button onClick={send} style={{
          position:'absolute', right: 6, bottom: 6, height: 36, padding:'0 14px', borderRadius: 999,
          background: count>0?'transparent':'var(--ink)', color: count>0?'var(--ink-3)':'var(--bg)',
          border: count>0?'1px solid var(--rule)':'none', cursor:count>0?'not-allowed':'pointer',
          fontFamily:'var(--mono)', fontSize: 11, letterSpacing:'.14em', textTransform:'uppercase',
        }}>{count>0?`${count}s`:'获取验证码'}</button>
      </div>
      <button className="btn" style={{ width:'100%', height: 48 }}>验证并绑定 <Icon.arrow/></button>
      <div className="meta" style={{ marginTop: 14, color:'var(--ink-3)' }}>
        · 与 App 内手机号一致 · 验证后立即同步曲库
      </div>
    </div>
  );
}

function CookieBlock() {
  return (
    <div>
      <div style={{ marginBottom: 14 }}>
        <div className="between" style={{ marginBottom: 8 }}>
          <div className="meta">COOKIE</div>
          <a className="meta" style={{ color:'var(--ink-2)', textDecoration:'underline', cursor:'pointer' }}>如何获取 →</a>
        </div>
        <textarea className="field" placeholder="MUSIC_U=...; __csrf=...; NMTID=...;" style={{ minHeight: 160, fontFamily:'var(--mono)', fontSize: 12 }}/>
      </div>
      <button className="btn" style={{ width:'100%', height: 48 }}>验证并保存 <Icon.arrow/></button>
      <div className="meta" style={{ marginTop: 14, color:'var(--ink-3)', lineHeight: 1.6 }}>
        · 高级方式 · 适合扫码与短信都不便时使用<br/>
        · Cookie 会本地加密 · 服务端不留存明文
      </div>
    </div>
  );
}

window.Bind = Bind;
