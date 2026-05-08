// src/hooks/useWebSocket.js
import { useEffect, useRef, useCallback } from 'react';
import { useAuthStore } from '../store/authStore';

/**
 * Opens a WebSocket connection authenticated via JWT query param.
 * Reconnects automatically on unexpected close (max 3 retries).
 *
 * @param {string} path - Socket path, e.g. '/ws/session'
 * @param {(msg: object) => void} onMessage - Handler for parsed JSON messages
 * @returns {{ send: (data: object) => void }}
 */
export function useWebSocket(path, onMessage) {
  const token      = useAuthStore(s => s.token);
  const wsRef      = useRef(null);
  const retriesRef = useRef(0);
  const onMsgRef   = useRef(onMessage);

  // Keep the message handler ref current without reopening the socket
  useEffect(() => { onMsgRef.current = onMessage; });

  const connect = useCallback(() => {
    if (!token) return;

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const host     = import.meta.env.VITE_WS_HOST || window.location.host;
    const url      = `${protocol}//${host}${path}?token=${token}`;

    const ws = new WebSocket(url);
    wsRef.current = ws;

    ws.onopen    = () => { retriesRef.current = 0; };
    ws.onmessage = (e) => {
      try {
        const msg = JSON.parse(e.data);
        onMsgRef.current?.(msg);
      } catch {
        // ignore non-JSON frames
      }
    };
    ws.onerror = (e) => console.warn('[useWebSocket] error', e);
    ws.onclose = (e) => {
      // Reconnect on unexpected close (not manual close code 1000)
      if (e.code !== 1000 && retriesRef.current < 3) {
        retriesRef.current++;
        setTimeout(connect, 1000 * retriesRef.current); // back-off: 1s, 2s, 3s
      }
    };
  }, [token, path]);

  useEffect(() => {
    connect();
    return () => {
      retriesRef.current = 3; // prevent reconnect on unmount
      wsRef.current?.close(1000, 'unmount');
    };
  }, [connect]);

  const send = useCallback((data) => {
    if (wsRef.current?.readyState === WebSocket.OPEN) {
      wsRef.current.send(JSON.stringify(data));
    }
  }, []);

  return { send };
}
