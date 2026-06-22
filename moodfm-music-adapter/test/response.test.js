const { describe, it } = require('node:test')
const assert = require('node:assert/strict')
const { ok, fail, unsupported } = require('../lib/response')

function mockRes() {
  let statusCode = 200
  let body = null
  return {
    status(code) { statusCode = code; return this },
    json(payload) { body = payload; return this },
    get statusCode() { return statusCode },
    get body() { return body },
  }
}

describe('response helpers', () => {
  it('ok wraps payload with code 200', () => {
    const res = mockRes()
    ok(res, { songs: [1] })
    assert.equal(res.statusCode, 200)
    assert.deepEqual(res.body, { code: 200, songs: [1] })
  })

  it('fail sets HTTP status and error', () => {
    const res = mockRes()
    fail(res, 500, 'boom')
    assert.equal(res.statusCode, 500)
    assert.equal(res.body.error, 'boom')
  })

  it('unsupported marks capability gap explicitly', () => {
    const res = mockRes()
    unsupported(res, 'not available', { songs: [] })
    assert.equal(res.body.unsupported, true)
    assert.equal(res.body.reason, 'not available')
    assert.deepEqual(res.body.songs, [])
  })
})