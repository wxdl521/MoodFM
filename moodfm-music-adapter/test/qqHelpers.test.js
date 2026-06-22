const { describe, it } = require('node:test')
const assert = require('node:assert/strict')
const {
  extractUin,
  getGtk,
  mapQqSong,
  findLikedPlaylistEntry,
} = require('../lib/qqHelpers')

describe('qqHelpers', () => {
  it('extractUin parses uin cookie', () => {
    assert.equal(extractUin('uin=o12345; qm_keyst=abc'), '12345')
    assert.equal(extractUin('uin=67890'), '67890')
  })

  it('getGtk is deterministic', () => {
    assert.equal(getGtk('test'), getGtk('test'))
    assert.notEqual(getGtk(''), getGtk('x'))
  })

  it('mapQqSong normalizes QQ song objects', () => {
    const mapped = mapQqSong({
      songmid: '001ABC',
      songname: 'Test Song',
      singer: [{ name: 'Artist A' }, { name: 'Artist B' }],
      album: { name: 'Album', mid: 'ALB123' },
      interval: 240,
    })
    assert.equal(mapped.songmid, '001ABC')
    assert.equal(mapped.songname, 'Test Song')
    assert.equal(mapped.singer.length, 2)
    assert.equal(mapped.interval, 240)
  })

  it('fetchDailyRecommend returns unsupported when APIs fail', async () => {
    const { fetchDailyRecommend } = require('../lib/qqHelpers')
    const result = await fetchDailyRecommend('uin=o1; qm_keyst=invalid')
    assert.equal(result.unsupported, true)
    assert.ok(result.reason)
    assert.deepEqual(result.songs, [])
  })

  it('findLikedPlaylistEntry matches dirid 201 or name', () => {
    const playlists = [
      { dissid: 100, dissname: '自建歌单' },
      { dissid: 999, dirid: 201, dissname: '我喜欢' },
    ]
    const liked = findLikedPlaylistEntry(playlists)
    assert.equal(liked.dissid, 999)
  })
})