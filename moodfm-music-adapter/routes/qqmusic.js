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
      const newJar = { ...session.cookieJar, ...parseCookies(response.headers['set-cookie']) }
      sessions.delete(key)
      return res.json({ code: 803, cookie: jarToString(newJar) })
    }
    // code 65 = still waiting
    return res.json({ code: 801, message: '等待扫码' })
  } catch (e) {
    console.error('QQ Music qr/check error:', e.message)
    return res.json({ code: 801, message: '检查状态失败，稍后重试' })
  }
})

// --------- User data (cookie required) ---------

function getCookie(req) {
  return req.headers['x-cookie'] || ''
}

router.get('/user/liked-songs', async (req, res) => {
  const cookie = getCookie(req)
  if (!cookie) return res.status(401).json({ error: 'cookie required' })
  // QQ Music open API is restricted; return empty until a proper solution is integrated
  res.json({ code: 200, songs: [] })
})

router.get('/recommend/songs', async (req, res) => {
  const cookie = getCookie(req)
  if (!cookie) return res.status(401).json({ error: 'cookie required' })
  res.json({ code: 200, songs: [] })
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

router.get('/song/url', async (req, res) => {
  const { id } = req.query
  if (!id) return res.status(400).json({ error: 'id is required' })
  // Full URL retrieval requires auth; return empty for now
  res.json({ code: 200, data: [] })
})

module.exports = router
