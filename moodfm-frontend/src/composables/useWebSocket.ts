import { onUnmounted } from 'vue'
import { Client, type IMessage } from '@stomp/stompjs'
import { usePlayerStore } from '@/stores/player'
import type { Song } from '@/types'
import { logger } from '@/utils/logger'

function isSong(v: unknown): v is Song {
  if (!v || typeof v !== 'object') return false
  const r = v as Record<string, unknown>
  return typeof r.id === 'string' && typeof r.title === 'string' && typeof r.artist === 'string'
}

export function useWebSocket() {
  const playerStore = usePlayerStore()

  let client: Client | null = null

  function connect(sessionId: string) {
    if (client?.active) {
      client.deactivate()
    }


    const brokerURL =
      (import.meta.env.VITE_WS_URL as string | undefined) ??
      `ws://${window.location.host}/ws/radio`

    client = new Client({
      brokerURL,
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect() {
        client!.subscribe(
          `/topic/radio/${sessionId}`,
          (message: IMessage) => {
            try {
              const payload: unknown = JSON.parse(message.body)
              // Expect payload to be a Song or array of Song
              if (Array.isArray(payload)) {
                const songs = payload.filter(isSong)
                if (songs.length) playerStore.addToQueue(songs)
              } else if (isSong(payload)) {
                playerStore.addToQueue([payload])
              }
            } catch (err) {
              // silent: 服务器偶发畸形帧无需骚扰用户
              logger.warn('ws:malformed-frame', err)
            }
          },
        )
      },
      onDisconnect() {
        console.warn('[WS] disconnected');
      },
      onStompError(frame) {
        console.error('[WS] STOMP error', frame)
      },
    })

    client.activate()
  }

  function disconnect() {
    if (client) {
      client.deactivate()
      client = null
    }
  }

  onUnmounted(() => {
    disconnect()
  })

  return {
    connect,
    disconnect,
  }
}
