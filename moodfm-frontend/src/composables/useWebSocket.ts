import { ref, onUnmounted } from 'vue'
import { Client, type IMessage } from '@stomp/stompjs'
import { usePlayerStore } from '@/stores/player'
import type { Song } from '@/types'

interface PlayEvent {
  type: string
  songId: string
  timestamp: number
}

export function useWebSocket() {
  const playerStore = usePlayerStore()
  const isConnected = ref(false)

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
      onConnect() {
        isConnected.value = true
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
            } catch {
              // Ignore malformed frames
            }
          },
        )
      },
      onDisconnect() {
        isConnected.value = false
      },
      onStompError(frame) {
        console.error('[WS] STOMP error', frame)
        isConnected.value = false
      },
    })

    client.activate()
  }

  function disconnect() {
    if (client) {
      client.deactivate()
      client = null
    }
    isConnected.value = false
    currentSessionId = null
  }

  function sendPlayEvent(event: PlayEvent) {
    if (!client?.active || !currentSessionId) return
    client.publish({
      destination: `/app/radio/${currentSessionId}/event`,
      body: JSON.stringify(event),
    })
  }

  onUnmounted(() => {
    disconnect()
  })

  return {
    isConnected,
    connect,
    disconnect,
    sendPlayEvent,
  }
}
