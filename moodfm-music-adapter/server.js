const express = require('express')
const morgan = require('morgan')
const neteaseRoutes = require('./routes/netease')
const qqmusicRoutes = require('./routes/qqmusic')

const app = express()
const PORT = process.env.PORT || 3000

app.use(morgan('dev'))
app.use(express.json())

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

app.listen(PORT, () => {
  console.log(`MoodFM Music Adapter running on port ${PORT}`)
})
