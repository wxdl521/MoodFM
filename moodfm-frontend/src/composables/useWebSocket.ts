import { onUnmounted } from 'vue'
import { Client, type IMessage } from '@stomp/stompjs'
import { usePlayerStore } from '@/stores/player'
import type { Song } from '@/types'
import { logger } from '@/utils/logger'

export function useWebSocket() {
  const playerStore = usePlayerStore()

  let client: Client | null = null
  let currentSessionId: string | null = null

  function connect(sessionId: string) {
    if (client?.active) {
      client.deactivate()
    }

    currentSessionId = sessionId

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
              const payload = JSON.parse(message.body)
              // Expect payload to be a Song or array of Song
              if (Array.isArray(payload)) {
                playerStore.addToQueue(payload as Song[])
              } else if (payload && typeof payload === 'object' && 'id' in payload) {
                playerStore.addToQueue([payload as Song])
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
    currentSessionId = null
  }

  onUnmounted(() => {
    disconnect()
  })

  return {
    connect,
    disconnect,
  }
}
