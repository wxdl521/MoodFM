const express = require('express')
const morgan = require('morgan')
const axios = require('axios')
const neteaseRoutes = require('./routes/netease')
const qqmusicRoutes = require('./routes/qqmusic')

axios.defaults.timeout = 10000 // 10 秒全局超时

const app = express()
const PORT = process.env.PORT || 3000

const morganFormat = process.env.NODE_ENV === 'production' ? 'combined' : 'dev'
app.use(morgan(morganFormat))
app.use(express.json())

const ADAPTER_SECRET = process.env.ADAPTER_SECRET
if (!ADAPTER_SECRET) {
  console.error('FATAL: ADAPTER_SECRET env var is required')
  process.exit(1)
}

app.use((req, res, next) => {
  if (req.path === '/health') return next()
  if (req.headers['x-adapter-secret'] !== ADAPTER_SECRET) {
    return res.status(401).json({ error: 'Unauthorized' })
  }
  next()
})

// 健康检查
app.get('/health', (req, res) => {
  res.json({ status: 'ok', service: 'moodfm-music-adapter' })
})

// 各平台路由
app.use('/netease', neteaseRoutes)
app.use('/qqmusic', qqmusicRoutes)

// 错误处理
app.use((err, req, res, next) => {
  console.error(err.stack)
  res.status(500).json({ error: err.message || 'Internal error' })
})

const server = app.listen(PORT, () => {
  console.log(`MoodFM Music Adapter running on port ${PORT}`)
})
server.timeout = 30000 // 30 秒防慢请求
