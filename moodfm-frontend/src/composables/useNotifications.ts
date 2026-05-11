import { ref, onUnmounted } from 'vue'
import { Client, type IMessage } from '@stomp/stompjs'

export interface Notification {
  type: string
  platform?: string
  message?: string
}

export function useNotifications() {
  const notifications = ref<Notification[]>([])
  const seenIds = new Set<string>()
  let client: Client | null = null

  function connect(userId: number | string) {
    if (client?.active) client.deactivate()

    const brokerURL =
      (import.meta.env.VITE_WS_URL as string | undefined) ??
      `ws://${window.location.host}/ws`

    client = new Client({
      brokerURL,
      reconnectDelay: 10000,
      onConnect() {
        client!.subscribe(`/topic/notify/${userId}`, (message: IMessage) => {
          try {
            const payload = JSON.parse(message.body) as Notification
            const dedupeKey = (payload as any).id ?? `${payload.type}:${payload.platform}:${payload.message}`
            if (seenIds.has(dedupeKey)) return
            seenIds.add(dedupeKey)
            notifications.value.push(payload)
            // Auto-dismiss after 8 seconds
            setTimeout(() => {
              const idx = notifications.value.indexOf(payload)
              if (idx >= 0) notifications.value.splice(idx, 1)
            }, 8000)
          } catch {
            // ignore
          }
        })
      },
      onStompError(frame) {
        console.error('[Notify WS] STOMP error', frame)
      },
    })

    client.activate()
  }

  function dismiss(idx: number) {
    notifications.value.splice(idx, 1)
  }

  function disconnect() {
    client?.deactivate()
    client = null
    notifications.value = []
    seenIds.clear()
  }

  onUnmounted(() => disconnect())

  return { notifications, connect, disconnect, dismiss }
}
