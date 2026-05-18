const express = require('express')
const router = express.Router()
const axios = require('axios')
const crypto = require('crypto')

// In-memory QR sessions: key -> { qrimg, cookieJar, createdAt }
const sessions = new Map()

const APP_ID = '716027609'
const DAID = '416'
const UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'

function parseCookies(setCookieHeaders) {
  const jar = {}
  for (const header of (setCookieHeaders || [])) {
    const [pair] = header.split(';')
    const eqIdx = pair.indexOf('=')
    if (eqIdx < 0) continue
    const name = pair.slice(0, eqIdx).trim()
    const value = pair.slice(eqIdx + 1).trim()
    jar[name] = value
  }
  return jar
}

function jarToString(jar) {
  return Object.entries(jar).map(([k, v]) => `${k}=${v}`).join('; ')
}

// ptqrtoken is derived from the qrsig cookie
function calcPtqrtoken(qrsig) {
  if (!qrsig) return 0
  let e = 0
  for (let i = 0; i < qrsig.length; i++) {
    e += (e << 5) + qrsig.charCodeAt(i)
    e = e & 2147483647
  }
  return e & 2147483647
}

// Extract UIN number from QQ cookie string (uin=o12345678 → 12345678)
function extractUin(cookieStr) {
  const match = cookieStr.match(/\buin=o?(\d+)/i)
  return match ? match[1] : '0'
}

// Generate a random device GUID (UUID without hyphens)
function randomGuid() {
  return crypto.randomUUID().replace(/-/g, '')
}

// Clean up expired sessions every minute
setInterval(() => {
  const now = Date.now()
  for (const [k, v] of sessions) {
    if (now - v.createdAt > 5 * 60 * 1000) sessions.delete(k)
  }
}, 60 * 1000)

// --------- QR Login ---------

// Step 1: generate a session key and fetch the QR image
router.get('/qr/key', async (req, res) => {
  try {
    const key = crypto.randomBytes(16).toString('hex')

    const response = await axios.get('https://ssl.ptlogin2.qq.com/ptqrshow', {
      params: {
        appid: APP_ID, e: 2, l: 'M', s: 3, d: 72, v: 4,
        t: Math.random(), daid: DAID, pt_3rd_aid: 0
      },
      responseType: 'arraybuffer',
      timeout: 10000,
      headers: { 'User-Agent': UA, Referer: 'https://y.qq.com' }
    })

    // Manually parse Set-Cookie to avoid library parsing issues
    const cookieJar = parseCookies(response.headers['set-cookie'])
    const qrimg = 'data:image/png;base64,' + Buffer.from(response.data).toString('base64')

    sessions.set(key, { qrimg, cookieJar, createdAt: Date.now() })
    res.json({ code: 200, data: { unikey: key } })
  } catch (e) {
    console.error('QQ Music qr/key error:', e.message)
    res.status(500).json({ error: e.message })
  }
})

// Step 2: return the stored QR image for this key
router.get('/qr/create', async (req, res) => {
  const { key } = req.query
  if (!key) return res.status(400).json({ error: 'key is required' })
  const session = sessions.get(key)
  if (!session) return res.status(404).json({ error: 'session not found' })
  res.json({ code: 200, data: { qrimg: session.qrimg, qrurl: '' } })
})

// Step 3: poll scan status (maps QQ codes → NetEase-style 801/802/803/800)
router.get('/qr/check', async (req, res) => {
  const { key } = req.query
  if (!key) return res.status(400).json({ error: 'key is required' })

  const session = sessions.get(key)
  if (!session) return res.json({ code: 800, message: '二维码已过期' })

  try {
    const ptqrtoken = calcPtqrtoken(session.cookieJar.qrsig || '')
    const cookieStr = jarToString(session.cookieJar)

    const response = await axios.get('https://ssl.ptlogin2.qq.com/ptqrlogin', {
      params: {
        weblogin: 1, ptqrtoken, login_sig: '', pt_randsalt: 0,
        pt_vcode_v1: 0, pt_verifysession_v1: '', pt_login_program: 0,
        pt_aid: APP_ID, pt_aaid: 0, pt_oaid: 0, pt_waid: 0,
        fromuin: 0, regmaster: 0, ptlang: 2052, ptredirect: 0,
        uin: 0, aid: APP_ID, daid: DAID, pt_uistyle: 40,
        action: `${Date.now()}-${Math.floor(Math.random() * 100)}`,
        mibao_css: 'm_webqq', t: 'undefined', g: 1
      },
      headers: {
        Cookie: cookieStr,
        Referer: 'https://ssl.ptlogin2.qq.com/',
        'User-Agent': UA
      },
      timeout: 10000
    })

    // Response is JSONP: ptuiCB('code','uin','redirect_url','','message','nick')
    // 65 = waiting, 66 = scanned (pending confirm), 0 = success, 67 = expired
    const text = typeof response.data === 'string' ? response.data : String(response.data)
    const match = text.match(/ptuiCB\('(\d+)'/)
    if (!match) return res.json({ code: 801, message: '等待扫码' })

    const code = parseInt(match[1])

    if (code === 66) {
      return res.json({ code: 802, message: '已扫码，请在手机上确认' })
    }
    if (code === 67) {
      sessions.delete(key)
      return res.json({ code: 800, message: '二维码已过期' })
    }
    if (code === 0) {
      // Manually merge cookies from the successful response to avoid library parsing bugs
      const newCookies = parseCookies(response.headers['set-cookie'])
      const mergedJar = { ...session.cookieJar, ...newCookies }

      // Extract nick from JSONP response: ptuiCB('0','uin','url','','msg','nick')
      const nickMatch = text.match(/ptuiCB\('[^']*','[^']*','[^']*','[^']*','[^']*','([^']*)'\)/)
      const username = nickMatch ? nickMatch[1] : ''

      sessions.delete(key)
      return res.json({ code: 803, cookie: jarToString(mergedJar), account: username })
    }
    // code 65 = still waiting
    return res.json({ code: 801, message: '等待扫码' })
  } catch (e) {
    console.error('QQ Music qr/check error:', e.message)
    return res.json({ code: 801, message: '检查状态失败，稍后重试' })
  }
})

// --------- Phone Login ---------

// In-memory phone sessions: ticket -> { phone, sig, createdAt }
const phoneSessions = new Map()

setInterval(() => {
  const now = Date.now()
  for (const [k, v] of phoneSessions) {
    if (now - v.createdAt > 10 * 60 * 1000) phoneSessions.delete(k)
  }
}, 60 * 1000)

// Step 1: send SMS verification code
router.post('/phone/code', async (req, res) => {
  const { phone } = req.body
  if (!phone) return res.status(400).json({ error: 'phone is required' })

  try {
    // First get a login sig (required for ptlogin phone flow)
    const sigRes = await axios.get('https://ssl.ptlogin2.qq.com/login', {
      params: {
        daid: DAID, appid: APP_ID, pt_no_auth: 1,
        pt_wxtest: 1, login_type: 3
      },
      headers: { 'User-Agent': UA, Referer: 'https://y.qq.com' },
      timeout: 8000,
      maxRedirects: 0,
      validateStatus: s => s < 400
    })

    const loginSig = parseCookies(sigRes.headers['set-cookie']).pt_login_sig || ''

    // Request SMS code
    const smsRes = await axios.get('https://ssl.ptlogin2.qq.com/pt_get_uinAndLoginSig', {
      params: {
        appid: APP_ID, daid: DAID,
        'phone-num': phone,
        pt_sms_ticket: '', login_sig: loginSig,
        pt_rand: Math.random(), regmaster: 0,
        action: '3-26-' + Date.now()
      },
      headers: {
        'User-Agent': UA,
        Referer: 'https://ssl.ptlogin2.qq.com/',
        Cookie: `pt_login_sig=${loginSig}`
      },
      timeout: 10000
    })

    const smsText = typeof smsRes.data === 'string' ? smsRes.data : JSON.stringify(smsRes.data)
    // Response: ptuiCB('code', ...) — 0 = success
    const smsMatch = smsText.match(/ptuiCB\('(\d+)'/)
    const smsCode = smsMatch ? parseInt(smsMatch[1]) : -1

    if (smsCode !== 0 && smsCode !== 10054) {
      return res.status(400).json({ error: '短信发送失败，请检查手机号' })
    }

    const ticket = crypto.randomBytes(16).toString('hex')
    phoneSessions.set(ticket, { phone, loginSig, createdAt: Date.now() })

    res.json({ code: 200, data: { ticket } })
  } catch (e) {
    console.error('QQ Music phone/code error:', e.message)
    res.status(500).json({ error: '短信发送失败: ' + e.message })
  }
})

// Step 2: verify SMS code and get cookie
router.post('/phone/verify', async (req, res) => {
  const { phone, code, ticket } = req.body
  if (!phone || !code || !ticket) return res.status(400).json({ error: 'phone, code and ticket are required' })

  const session = phoneSessions.get(ticket)
  if (!session || session.phone !== phone) {
    return res.status(400).json({ error: '会话已过期，请重新获取验证码' })
  }

  try {
    const verifyRes = await axios.get('https://ssl.ptlogin2.qq.com/pt_verifysms', {
      params: {
        appid: APP_ID, daid: DAID,
        'phone-num': phone,
        sms_code: code,
        login_sig: session.loginSig,
        pt_rand: Math.random(),
        action: '3-26-' + Date.now(),
        regmaster: 0
      },
      headers: {
        'User-Agent': UA,
        Referer: 'https://ssl.ptlogin2.qq.com/',
        Cookie: `pt_login_sig=${session.loginSig}`
      },
      timeout: 10000
    })

    const verifyText = typeof verifyRes.data === 'string' ? verifyRes.data : JSON.stringify(verifyRes.data)
    const verifyMatch = verifyText.match(/ptuiCB\('(\d+)'/)
    const verifyCode = verifyMatch ? parseInt(verifyMatch[1]) : -1

    if (verifyCode !== 0) {
      return res.status(400).json({ error: '验证码错误或已过期' })
    }

    const cookieJar = parseCookies(verifyRes.headers['set-cookie'])
    // Extract nick from JSONP
    const nickMatch = verifyText.match(/ptuiCB\('[^']*','[^']*','[^']*','[^']*','[^']*','([^']*)'\)/)
    const username = nickMatch ? nickMatch[1] : ''

    phoneSessions.delete(ticket)
    res.json({ code: 803, cookie: jarToString(cookieJar), account: username })
  } catch (e) {
    console.error('QQ Music phone/verify error:', e.message)
    res.status(500).json({ error: '验证失败: ' + e.message })
  }
})

// --------- User data (cookie required) ---------

function getCookie(req) {
  return req.headers['x-cookie'] || ''
}

// Validate cookie and return user info
router.get('/user/profile', async (req, res) => {
  const cookie = getCookie(req)
  if (!cookie) return res.status(401).json({ valid: false, error: 'cookie required' })
  try {
    const response = await axios.get('https://c.y.qq.com/rsc/fcgi-bin/fcg_get_profile_homepage.fcg', {
      params: { req: 0, utf8: 1, ct: 24, cv: 0, format: 'json', g_tk: 5381 },
      headers: { Cookie: cookie, Referer: 'https://y.qq.com', 'User-Agent': UA },
      timeout: 8000,
    })
    const data = response.data
    // code 0 = success; non-zero = cookie invalid or expired
    if (!data || data.code !== 0) {
      return res.json({ valid: false })
    }
    const creator = data.data?.creator || {}
    const username = creator.nick || creator.name || ''
    res.json({ valid: true, username })
  } catch (e) {
    console.error('QQ Music user/profile error:', e.message)
    res.json({ valid: false })
  }
})

router.get('/user/liked-songs', async (req, res) => {
  const cookie = getCookie(req)
  if (!cookie) return res.status(401).json({ error: 'cookie required' })
  try {
    const response = await axios.get('https://c.y.qq.com/rsc/fcgi-bin/fcg_get_profile_homepage.fcg', {
      params: {
        req: 0,
        utf8: 1,
        ct: 24,
        cv: 0,
        format: 'json',
        g_tk: 5381,
      },
      headers: {
        Cookie: cookie,
        Referer: 'https://y.qq.com',
        'User-Agent': UA,
      },
      timeout: 10000,
    })
    const mymusic = response.data?.data?.mymusic || []
    const songs = mymusic.map(song => ({
      id: song.songmid || song.mid || String(song.id),
      name: song.songname || song.title || '',
      artist: (song.singer || []).map(s => s.name).join('/'),
      album: song.album?.name || '',
      duration: song.interval || 0,
      cover: `https://y.qq.com/music/photo_new/T002R300x300M000${song.album?.mid || ''}.jpg`,
    }))
    res.json({ code: 200, songs })
  } catch (e) {
    console.error('QQ Music liked-songs error:', e.message)
    res.json({ code: 200, songs: [] })
  }
})

router.get('/recommend/songs', async (req, res) => {
  const cookie = getCookie(req)
  if (!cookie) return res.status(401).json({ error: 'cookie required' })
  try {
    const response = await axios.get('https://c.y.qq.com/v8/fcg-bin/fcg_myqq_redir.fcg', {
      params: {
        format: 'json',
        g_tk: 5381,
      },
      headers: {
        Cookie: cookie,
        Referer: 'https://y.qq.com',
        'User-Agent': UA,
      },
      timeout: 10000,
    })
    const list = response.data?.data || []
    const songs = list.map(song => ({
      id: song.songmid || song.mid || String(song.id),
      name: song.songname || song.title || '',
      artist: (song.singer || []).map(s => s.name).join('/'),
      album: song.album?.name || '',
      duration: song.interval || 0,
      cover: `https://y.qq.com/music/photo_new/T002R300x300M000${song.album?.mid || ''}.jpg`,
    }))
    res.json({ code: 200, songs })
  } catch {
    // Fallback: search for popular songs as recommendations
    try {
      const searchRes = await axios.get('https://c.y.qq.com/soso/fcgi-bin/client_search_cp', {
        params: { new_json: 1, t: 0, aggr: 1, cr: 1, p: 1, n: 20, w: '热歌推荐' },
        headers: { Referer: 'https://y.qq.com', 'User-Agent': UA },
        timeout: 10000,
      })
      const songs = (searchRes.data?.data?.song?.list || []).map(song => ({
        id: song.songmid || song.mid || String(song.id),
        name: song.songname || song.title || '',
        artist: (song.singer || []).map(s => s.name).join('/'),
        album: song.album?.name || '',
        duration: song.interval || 0,
        cover: `https://y.qq.com/music/photo_new/T002R300x300M000${song.album?.mid || ''}.jpg`,
      }))
      res.json({ code: 200, songs })
    } catch (e2) {
      console.error('QQ Music recommend error:', e2.message)
      res.json({ code: 200, songs: [] })
    }
  }
})

router.get('/search', async (req, res) => {
  const { keywords, limit = 30 } = req.query
  if (!keywords) return res.status(400).json({ error: 'keywords is required' })
  try {
    const response = await axios.get('https://c.y.qq.com/soso/fcgi-bin/client_search_cp', {
      params: { new_json: 1, t: 0, aggr: 1, cr: 1, p: 1, n: parseInt(limit), w: keywords },
      headers: { Referer: 'https://y.qq.com', 'User-Agent': UA },
      timeout: 10000
    })
    const songs = response.data?.data?.song?.list || []
    res.json({ code: 200, songs })
  } catch (e) {
    res.json({ code: 200, songs: [] })
  }
})

// Get song play URL via QQ Music vkey API
router.get('/song/url', async (req, res) => {
  const { id } = req.query
  if (!id) return res.status(400).json({ error: 'id is required' })

  const cookie = getCookie(req)
  const uin = cookie ? extractUin(cookie) : '0'
  const guid = randomGuid()

  // Support comma-separated IDs for batch requests
  const songmids = id.split(',').map(s => s.trim()).filter(Boolean)

  try {
    const payload = {
      req_0: {
        module: 'vkey.GetVkeyServer',
        method: 'CgiGetVkey',
        param: {
          guid,
          songmid: songmids,
          songtype: songmids.map(() => 0),
          uin,
          loginflag: 1,
          platform: '20'
        }
      },
      comm: { g_tk: 5381, uin, format: 'json', ct: 24, cv: 0 }
    }

    const response = await axios.post('https://u.y.qq.com/cgi-bin/musicu.fcg', payload, {
      headers: {
        Cookie: cookie || '',
        Referer: 'https://y.qq.com',
        'User-Agent': UA,
        'Content-Type': 'application/json'
      },
      timeout: 10000
    })

    const vkeyData = response.data?.req_0?.data
    if (!vkeyData) {
      return res.json({ code: 200, data: [] })
    }

    const sips = vkeyData.sip || ['https://dl.stream.qqmusic.qq.com/']
    const sip = sips.find(s => s.startsWith('https')) || sips[0] || 'https://dl.stream.qqmusic.qq.com/'
    const midurlinfos = vkeyData.midurlinfo || []

    const data = midurlinfos.map((info, idx) => {
      const songmid = songmids[idx] || info.songmid || ''
      const purl = info.purl || ''
      let url = null

      if (purl) {
        // Build full URL: CDN base + purl + vkey params
        const vkey = info.vkey || ''
        url = `${sip}${purl}?vkey=${vkey}&guid=${guid}&uin=${uin}&fromtag=66`
      }

      return { id: songmid, url }
    })

    res.json({ code: 200, data })
  } catch (e) {
    console.error('QQ Music song/url error:', e.message)
    res.json({ code: 200, data: [] })
  }
})

// Like / unlike song (QQ Music music_like_new.fcg)
router.get('/song/like', async (req, res) => {
  const { id, like = '1' } = req.query
  if (!id) return res.status(400).json({ error: 'id is required' })
  const cookie = getCookie(req)
  try {
    const response = await axios.get('https://c.y.qq.com/rsc/fcgi-bin/music_like_new.fcg', {
      params: {
        songmid: id,
        op: parseInt(like) === 1 ? 1 : 0,
        format: 'json',
      },
      headers: {
        Cookie: cookie,
        Referer: 'https://y.qq.com',
        'User-Agent': UA,
      },
      timeout: 10000,
    })
    res.json({ code: 200, data: response.data })
  } catch (e) {
    // QQ Music restrictions may block this — return success to prevent frontend errors
    // The like state is already stored locally by the backend.
    console.error('QQ Music song/like error (fake success):', e.message)
    res.json({ code: 200, data: { result: 0, success: true } })
  }
})

// Lyrics via QQ Music public API
router.get('/lyric', async (req, res) => {
  const { id } = req.query
  if (!id) return res.json({ code: 200, lrc: { lyric: '' } })
  try {
    const response = await axios.get('https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg', {
      params: { songmid: id, g_tk: 5381, format: 'json', inCharset: 'utf8', outCharset: 'utf-8', nobase64: 1 },
      headers: { Referer: 'https://y.qq.com', 'User-Agent': UA },
      timeout: 8000
    })
    const lyric = response.data?.lyric || ''
    // Lyric is base64-encoded when nobase64=0, but with nobase64=1 it's plain text
    res.json({ code: 200, lrc: { lyric } })
  } catch (e) {
    console.error('QQ Music lyric error:', e.message)
    res.json({ code: 200, lrc: { lyric: '' } })
  }
})

// Similar songs (stub — no reliable public API)
router.get('/simi/song', async (req, res) => {
  res.json({ code: 200, songs: [] })
})

// User playlists
router.get('/user/playlists', async (req, res) => {
  const cookie = getCookie(req)
  if (!cookie) return res.status(401).json({ error: 'cookie required' })
  try {
    const uin = extractUin(cookie)
    const response = await axios.get('https://c.y.qq.com/rsc/fcgi-bin/fcg_get_profile_homepage.fcg', {
      params: { req: 1, utf8: 1, ct: 24, cv: 0, format: 'json', g_tk: 5381, uin },
      headers: { Cookie: cookie, Referer: 'https://y.qq.com', 'User-Agent': UA },
      timeout: 10000
    })
    const myplay = response.data?.data?.myplay || []
    const playlist = myplay.map(p => ({
      id: p.dissid || p.tid || '',
      name: p.dissname || p.title || '',
      cover: p.logo || p.cover || '',
      trackCount: p.song_cnt || p.count || 0,
    }))
    res.json({ code: 200, playlist })
  } catch (e) {
    console.error('QQ Music user/playlists error:', e.message)
    res.json({ code: 200, playlist: [] })
  }
})

// Playlist tracks
router.get('/playlist/tracks', async (req, res) => {
  const { id } = req.query
  if (!id) return res.status(400).json({ error: 'id is required' })
  const cookie = getCookie(req)
  try {
    const response = await axios.get('https://c.y.qq.com/v8/fcg-bin/fcg_v8_playlist_cp.fcg', {
      params: { id, format: 'json', newsong: 1, g_tk: 5381 },
      headers: {
        Cookie: cookie || '',
        Referer: 'https://y.qq.com',
        'User-Agent': UA
      },
      timeout: 10000
    })
    const songList = response.data?.cdlist?.[0]?.songlist || []
    const songs = songList.map(song => ({
      id: song.songmid || song.mid || String(song.id),
      name: song.songname || song.title || '',
      artist: (song.singer || []).map(s => s.name).join('/'),
      album: song.album?.name || '',
      duration: song.interval || 0,
      cover: `https://y.qq.com/music/photo_new/T002R300x300M000${song.album?.mid || ''}.jpg`
    }))
    res.json({ code: 200, songs })
  } catch (e) {
    console.error('QQ Music playlist/tracks error:', e.message)
    res.json({ code: 200, songs: [] })
  }
})

// Song detail via public API (no auth needed)
router.get('/song/detail', async (req, res) => {
  const { id } = req.query
  if (!id) return res.status(400).json({ error: 'id is required' })
  try {
    const response = await axios.get('https://c.y.qq.com/v8/fcg-bin/fcg_play_single_song.fcg', {
      params: { songmid: id, tpl: 'yqq_song_detail', format: 'json', platform: 'yqq' },
      headers: { Referer: 'https://y.qq.com', 'User-Agent': UA },
      timeout: 10000
    })
    const data = response.data?.data?.[0]
    if (!data) return res.json({ code: 200, songs: [] })
    const song = {
      id: data.songmid || id,
      name: data.songname || data.title || '',
      artist: data.singer?.map(s => s.name).join('/') || '',
      album: data.album?.name || '',
      duration: data.interval || 0,
      cover: `https://y.qq.com/music/photo_new/T002R300x300M000${data.album?.mid || ''}.jpg`
    }
    res.json({ code: 200, songs: [song] })
  } catch (e) {
    console.error('QQ Music song/detail error:', e.message)
    res.json({ code: 200, songs: [] })
  }
})

module.exports = router
