import { ref, onUnmounted } from 'vue'
import { Client, type IMessage } from '@stomp/stompjs'

export interface Notification {
  id: string
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
            const raw = JSON.parse(message.body) as Omit<Notification, 'id'>
            const dedupeKey = `${raw.type}:${raw.platform}:${raw.message}`
            if (seenIds.has(dedupeKey)) return
            seenIds.add(dedupeKey)
            const notification: Notification = {
              ...raw,
              id: `${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
            }
            notifications.value.push(notification)
            // Auto-dismiss after 8 seconds
            setTimeout(() => {
              const idx = notifications.value.indexOf(notification)
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

  function dismiss(id: string) {
    const idx = notifications.value.findIndex(n => n.id === id)
    if (idx >= 0) notifications.value.splice(idx, 1)
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
