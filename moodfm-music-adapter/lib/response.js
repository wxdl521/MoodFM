/** Shared JSON response helpers — align with netease.js error semantics. */

function ok(res, body = {}) {
  res.json({ code: 200, ...body })
}

function fail(res, status, error, extra = {}) {
  res.status(status).json({ code: status, error, ...extra })
}

/** Explicit unsupported capability — not a silent empty success. */
function unsupported(res, reason, extra = {}) {
  res.json({ code: 200, unsupported: true, reason, ...extra })
}

module.exports = { ok, fail, unsupported }