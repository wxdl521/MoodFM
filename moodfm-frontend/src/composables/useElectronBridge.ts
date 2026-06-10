import { watch } from 'vue'
import { usePlayerStore } from '@/stores/player'
import { useAudioPlayer } from '@/composables/useAudioPlayer'

interface ElectronAPI {
  playerUpdate: (state: { title: string; artist: string; isPlaying: boolean }) => void
  onPlayPause: (cb: () => void) => void
  onNext: (cb: () => void) => void
  onPrev: (cb: () => void) => void
}

declare global {
  interface Window {
    electronAPI?: ElectronAPI
  }
}

export function useElectronBridge() {
  const electronAPI = window.electronAPI
  if (!electronAPI) return

  const player = usePlayerStore()
  const audio = useAudioPlayer()

  // Push player state to Electron (for taskbar / media session integration)
  watch(
    () => ({
      title: player.currentSong?.title ?? '',
      artist: player.currentSong?.artist ?? '',
      isPlaying: player.isPlaying,
    }),
    (state) => {
      electronAPI.playerUpdate(state)
    },
    { deep: true },
  )

  // Desktop notification on song change
  watch(
    () => player.currentSong?.id,
    (id) => {
      if (!id) return
      const song = player.currentSong
      if (!song) return
      if (Notification.permission === 'granted') {
        new Notification(`${song.title} - ${song.artist}`, { silent: true })
      } else if (Notification.permission !== 'denied') {
        Notification.requestPermission().then((perm) => {
          if (perm === 'granted') {
            new Notification(`${song.title} - ${song.artist}`, { silent: true })
          }
        })
      }
    },
  )

  // Listen for media key commands from Electron
  electronAPI.onPlayPause(() => {
    if (player.isPlaying) {
      audio.pause()
    } else {
      audio.play()
    }
  })

  electronAPI.onNext(() => {
    player.next()
    const song = player.currentSong
    if (song?.audioUrl) {
      audio.load(song.audioUrl).then(() => audio.play())
    }
  })

  electronAPI.onPrev(() => {
    player.prev()
    const song = player.currentSong
    if (song?.audioUrl) {
      audio.load(song.audioUrl).then(() => audio.play())
    }
  })
}
