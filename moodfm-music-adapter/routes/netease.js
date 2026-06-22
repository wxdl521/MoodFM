const express = require('express')
const router = express.Router()
const crypto = require('crypto')

let api
function getApi() {
  if (!api) api = require('NeteaseCloudMusicApi')
  return api
}

function getCookie(req) {
  return req.headers['x-cookie'] || ''
}

// In-memory phone sessions: ticket -> { phone, createdAt }
const phoneSessions = new Map()

setInterval(() => {
  const now = Date.now()
  for (const [k, v] of phoneSessions) {
    if (now - v.createdAt > 10 * 60 * 1000) phoneSessions.delete(k)
  }
}, 60 * 1000)

// --------- 二维码登录 ---------

router.get('/qr/key', async (req, res) => {
  try {
    const result = await getApi().login_qr_key({ timestamp: Date.now() })
    res.json({ code: 200, data: result.body.data })
  } catch (e) {
    res.status(500).json({ error: e.message })
  }
})

router.get('/qr/create', async (req, res) => {
  try {
    const { key } = req.query
    if (!key) return res.status(400).json({ error: 'key is required' })
    const result = await getApi().login_qr_create({ key, qrimg: true, timestamp: Date.now() })
    res.json({ code: 200, data: result.body.data })
  } catch (e) {
    res.status(500).json({ error: e.message })
  }
})

router.get('/qr/check', async (req, res) => {
  try {
    const { key } = req.query
    if (!key) return res.status(400).json({ error: 'key is required' })
    const result = await getApi().login_qr_check({ key, timestamp: Date.now() })
    res.json(result.body)
  } catch (e) {
    // NeteaseCloudMusicApi v4.x known bug: on code 803 (login success), library throws
    // ReferenceError while processing response cookies. Try to recover cookie from
    // the error object itself (some versions attach it), or from the library's last response.
    if (e instanceof ReferenceError || e.message?.includes('is not defined') || e.message?.includes('cookie')) {
      // Check if cookie is attached to the error object by the library
      const rawCookie = e?.cookie ||
        (Array.isArray(e?.headers?.['set-cookie']) ? e.headers['set-cookie'].join('; ') : null) ||
        e?.body?.cookie ||
        ''
      if (rawCookie) {
        return res.json({ code: 803, cookie: rawCookie, message: 'login success' })
      }
      // cookie not recoverable from error — backend will ask user to switch to Cookie method
      return res.json({ code: 803, cookie: '', message: 'login success (cookie unavailable)' })
    }
    res.status(500).json({ error: e.message })
  }
})

// --------- 手机号登录 ---------

router.post('/phone/code', async (req, res) => {
  try {
    const { phone } = req.body
    if (!phone) return res.status(400).json({ error: 'phone is required' })

    const result = await getApi().captcha_sent({ phone, ctcode: 86, timestamp: Date.now() })
    if (result.body.code !== 200) {
      return res.status(400).json({ error: result.body.message || '短信发送失败' })
    }

    const ticket = crypto.randomBytes(16).toString('hex')
    phoneSessions.set(ticket, { phone, createdAt: Date.now() })
    res.json({ code: 200, data: { ticket } })
  } catch (e) {
    console.error('Netease phone/code error:', e.message)
    res.status(500).json({ error: e.message })
  }
})

router.post('/phone/verify', async (req, res) => {
  try {
    const { phone, code, ticket } = req.body
    if (!phone || !code || !ticket) {
      return res.status(400).json({ error: 'phone, code and ticket are required' })
    }

    const session = phoneSessions.get(ticket)
    if (!session || session.phone !== phone) {
      return res.status(400).json({ error: '会话已过期，请重新获取验证码' })
    }

    const result = await getApi().login_cellphone({
      phone,
      captcha: code,
      countrycode: 86,
      timestamp: Date.now(),
    })

    if (result.body.code !== 200) {
      return res.status(400).json({ error: result.body.message || '验证码错误或已过期' })
    }

    const cookie = result.body.cookie || (Array.isArray(result.cookie) ? result.cookie.join(';') : '')
    const username = result.body.profile?.nickname
      || result.body.account?.userName
      || result.body.bindings?.[0]?.user?.nickname
      || ''

    phoneSessions.delete(ticket)
    res.json({ code: 803, cookie, account: username })
  } catch (e) {
    console.error('Netease phone/verify error:', e.message)
    res.status(500).json({ error: e.message })
  }
})

// --------- 用户数据 ---------

// 获取用户喜欢的歌曲（返回完整歌曲对象）
router.get('/user/liked-songs', async (req, res) => {
  try {
    const cookie = getCookie(req)
    if (!cookie) return res.status(401).json({ error: 'cookie required' })

    const loginStatus = await getApi().login_status({ cookie })
    const uid = loginStatus.body.data?.profile?.userId
    if (!uid) return res.status(401).json({ error: 'invalid cookie' })

    // 获取用户歌单列表，第一个是"我喜欢的音乐"
    const playlists = await getApi().user_playlist({ uid, cookie, limit: 1 })
    const likedPlaylist = playlists.body.playlist?.[0]
    if (!likedPlaylist) return res.json({ code: 200, songs: [] })

    // 获取歌单中所有歌曲（完整对象）
    const tracks = await getApi().playlist_track_all({ id: likedPlaylist.id, cookie })
    res.json({ code: 200, songs: tracks.body.songs || [] })
  } catch (e) {
    console.error('liked-songs error:', e.message)
    res.status(500).json({ error: e.message })
  }
})

// 获取每日推荐歌曲
router.get('/recommend/songs', async (req, res) => {
  try {
    const cookie = getCookie(req)
    if (!cookie) return res.status(401).json({ error: 'cookie required' })
    const result = await getApi().recommend_songs({ cookie })
    res.json({ code: 200, songs: result.body.data?.dailySongs || [] })
  } catch (e) {
    res.status(500).json({ error: e.message })
  }
})

// 搜索歌曲（返回完整歌曲对象）
router.get('/search', async (req, res) => {
  try {
    const { keywords, limit = 30 } = req.query
    if (!keywords) return res.status(400).json({ error: 'keywords is required' })
    const result = await getApi().search({ keywords, limit: parseInt(limit), type: 1 })
    res.json({ code: 200, songs: result.body.result?.songs || [] })
  } catch (e) {
    res.status(500).json({ error: e.message })
  }
})

// 红心/取消红心歌曲 (like=1 红心, like=0 取消)
router.get('/song/like', async (req, res) => {
  try {
    const { id, like = '1' } = req.query
    const cookie = getCookie(req)
    if (!id) return res.status(400).json({ error: 'id is required' })
    if (!cookie) return res.status(401).json({ error: 'cookie required' })
    const result = await getApi().like({ id, like: parseInt(like), cookie })
    res.json({ code: 200, data: result.body })
  } catch (e) {
    console.error('song/like error:', e.message)
    res.status(500).json({ error: e.message })
  }
})

// 获取歌曲播放 URL
router.get('/song/url', async (req, res) => {
  try {
    const { id } = req.query
    const cookie = getCookie(req)
    if (!id) return res.status(400).json({ error: 'id is required' })
    const result = await getApi().song_url_v1({ id, level: 'exhigh', cookie })
    res.json({ code: 200, data: result.body.data || [] })
  } catch (e) {
    res.status(500).json({ error: e.message })
  }
})

// 获取歌词
router.get('/lyric', async (req, res) => {
  try {
    const { id } = req.query
    if (!id) return res.status(400).json({ error: 'id is required' })
    const result = await getApi().lyric({ id })
    res.json({ code: 200, lrc: result.body.lrc, tlyric: result.body.tlyric })
  } catch (e) {
    res.status(500).json({ error: e.message })
  }
})

// 获取歌曲详情
router.get('/song/detail', async (req, res) => {
  try {
    const { ids } = req.query
    if (!ids) return res.status(400).json({ error: 'ids is required' })
    const cookie = getCookie(req)
    const result = await getApi().song_detail({ ids, cookie })
    res.json({ code: 200, songs: result.body.songs || [], privileges: result.body.privileges || [] })
  } catch (e) {
    res.status(500).json({ error: e.message })
  }
})

// 获取相似歌曲
router.get('/simi/song', async (req, res) => {
  try {
    const { id } = req.query
    if (!id) return res.status(400).json({ error: 'id is required' })
    const cookie = getCookie(req)
    const result = await getApi().simi_song({ id, cookie })
    res.json({ code: 200, songs: result.body.songs || [] })
  } catch (e) {
    res.status(500).json({ error: e.message })
  }
})

// 获取用户歌单列表
router.get('/user/playlists', async (req, res) => {
  try {
    const cookie = getCookie(req)
    if (!cookie) return res.status(401).json({ error: 'cookie required' })

    const loginStatus = await getApi().login_status({ cookie })
    const uid = loginStatus.body.data?.profile?.userId
    if (!uid) return res.status(401).json({ error: 'invalid cookie' })

    const result = await getApi().user_playlist({ uid, cookie, limit: 50 })
    res.json({ code: 200, playlist: result.body.playlist || [] })
  } catch (e) {
    console.error('user/playlists error:', e.message)
    res.status(500).json({ error: e.message })
  }
})

// 获取歌单所有曲目
router.get('/playlist/tracks', async (req, res) => {
  try {
    const { id } = req.query
    if (!id) return res.status(400).json({ error: 'id is required' })
    const cookie = getCookie(req)
    const result = await getApi().playlist_track_all({ id, cookie })
    res.json({ code: 200, songs: result.body.songs || [] })
  } catch (e) {
    console.error('playlist/tracks error:', e.message)
    res.status(500).json({ error: e.message })
  }
})

module.exports = router
