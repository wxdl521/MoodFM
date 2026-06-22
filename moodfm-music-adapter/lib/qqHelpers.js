const axios = require('axios')
const crypto = require('crypto')

const UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'

function extractUin(cookieStr) {
  const match = cookieStr.match(/\buin=o?(\d+)/i)
  return match ? match[1] : '0'
}

function extractCookieValue(cookieStr, name) {
  const m = cookieStr.match(new RegExp('(?:^|;\\s*)' + name + '=([^;]+)'))
  return m ? decodeURIComponent(m[1].trim()) : ''
}

function getGtk(skey) {
  if (!skey) return 5381
  let hash = 5381
  for (let i = 0; i < skey.length; i++) {
    hash += (hash << 5) + skey.charCodeAt(i)
    hash = hash & 0x7fffffff
  }
  return hash & 0x7fffffff
}

function randomGuid() {
  return crypto.randomUUID().replace(/-/g, '')
}

function extractAuth(cookie) {
  const uin = extractUin(cookie)
  const skey = extractCookieValue(cookie, 'qm_keyst')
    || extractCookieValue(cookie, 'p_skey')
    || extractCookieValue(cookie, 'qqmusic_key')
    || extractCookieValue(cookie, 'skey')
  return { uin, gtk: getGtk(skey), cookie }
}

function mapQqSong(song) {
  if (!song) return null
  const songmid = song.songmid || song.mid || (song.id ? String(song.id) : '')
  if (!songmid) return null
  return {
    songmid,
    mid: songmid,
    id: song.id || 0,
    songname: song.songname || song.title || song.name || '',
    title: song.songname || song.title || song.name || '',
    singer: song.singer || song.artists || [],
    album: song.album || { name: song.albumname || '', mid: song.albummid || '' },
    interval: song.interval || song.duration || 0,
    albummid: song.albummid || song.album?.mid || '',
  }
}

async function postMusicu(payload, cookie) {
  const response = await axios.post('https://u.y.qq.com/cgi-bin/musicu.fcg', payload, {
    headers: {
      Cookie: cookie || '',
      Referer: 'https://y.qq.com',
      'User-Agent': UA,
      'Content-Type': 'application/json',
    },
    timeout: 10000,
  })
  return response.data
}

async function fetchProfileMyplay(cookie, auth) {
  const response = await axios.get('https://c.y.qq.com/rsc/fcgi-bin/fcg_get_profile_homepage.fcg', {
    params: {
      req: 1,
      utf8: 1,
      ct: 24,
      cv: 0,
      format: 'json',
      g_tk: auth.gtk,
      uin: auth.uin,
    },
    headers: {
      Cookie: cookie,
      Referer: 'https://y.qq.com',
      'User-Agent': UA,
    },
    timeout: 10000,
  })
  if (response.data?.code !== 0) {
    throw new Error(`profile homepage failed: code ${response.data?.code}`)
  }
  return response.data?.data?.myplay || []
}

function findLikedPlaylistEntry(myplay) {
  return myplay.find(p => {
    const dirid = p.dirid ?? p.dir_id
    const name = p.dissname || p.title || ''
    return dirid === 201 || dirid === '201' || name.includes('我喜欢')
  })
}

async function fetchPlaylistSongList(playlistId, cookie) {
  const response = await axios.get('https://c.y.qq.com/v8/fcg-bin/fcg_v8_playlist_cp.fcg', {
    params: { id: playlistId, format: 'json', newsong: 1, g_tk: 5381 },
    headers: {
      Cookie: cookie || '',
      Referer: 'https://y.qq.com',
      'User-Agent': UA,
    },
    timeout: 10000,
  })
  if (response.data?.code !== 0) {
    throw new Error(`playlist fetch failed: code ${response.data?.code}`)
  }
  return response.data?.cdlist?.[0]?.songlist || []
}

async function fetchLikedSongs(cookie) {
  const auth = extractAuth(cookie)
  const myplay = await fetchProfileMyplay(cookie, auth)
  const liked = findLikedPlaylistEntry(myplay)
  if (!liked) return []

  const playlistId = liked.dissid || liked.tid || liked.dirid
  if (!playlistId) return []

  const songlist = await fetchPlaylistSongList(playlistId, cookie)
  return songlist.map(mapQqSong).filter(Boolean)
}

async function fetchDailyRecommend(cookie) {
  const auth = extractAuth(cookie)
  let lastError = null
  const payloads = [
    {
      comm: { ct: 24, cv: 0, uin: auth.uin, g_tk: auth.gtk, format: 'json' },
      req_0: {
        module: 'music.recommend.RecommendFeed',
        method: 'get_daily_recommend',
        param: { count: 30 },
      },
    },
    {
      comm: { ct: 24, cv: 0, uin: auth.uin, g_tk: auth.gtk, format: 'json' },
      req_0: {
        module: 'recommend.RecommendFeed',
        method: 'get_daily_recommend',
        param: { count: 30 },
      },
    },
    {
      comm: { ct: 24, cv: 0, uin: auth.uin, g_tk: auth.gtk, format: 'json' },
      req_0: {
        module: 'NewRecommendComm',
        method: 'daily_recommend_song',
        param: {},
      },
    },
  ]

  for (const payload of payloads) {
    try {
      const data = await postMusicu(payload, cookie)
      const block = data?.req_0?.data
      if (!block) continue

      const rawList = block.songlist
        || block.new_song_list
        || block.shelf?.[0]?.list
        || block.recommend?.v_shelf?.[0]?.v_niche?.[0]?.v_card
        || []

      const songs = rawList
        .map(item => mapQqSong(item?.songinfo || item?.song || item))
        .filter(Boolean)

      if (songs.length > 0) return { songs, unsupported: false }
    } catch (err) {
      lastError = err
    }
  }

  return {
    songs: [],
    unsupported: true,
    reason: lastError
      ? `QQ Music daily recommend API unavailable: ${lastError.message}`
      : 'QQ Music daily recommend API unavailable',
  }
}

module.exports = {
  UA,
  extractUin,
  extractCookieValue,
  getGtk,
  randomGuid,
  extractAuth,
  mapQqSong,
  postMusicu,
  findLikedPlaylistEntry,
  fetchLikedSongs,
  fetchDailyRecommend,
  fetchPlaylistSongList,
}