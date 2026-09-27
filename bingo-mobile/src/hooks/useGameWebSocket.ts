import { useEffect, useRef } from 'react';
import { Client, IMessage } from '@stomp/stompjs';
import { useGameStore } from '@/store/game.store';
import { CalledNumberResponse, GameStatus, BingoClaimResponse } from '@/types';
import { getWsBaseUrl } from '@/lib/backend';
import { tokenStorage } from '@/store/tokenStorage';
import { translateClientMessage } from '@/lib/clientTranslations';

interface GameEvent {
  type: 'NUMBER_CALLED' | 'GAME_STATUS_CHANGED' | 'CLAIM_PENDING' | 'CLAIM_RESOLVED' | 'GAME_RESTARTED';
  data: Record<string, unknown>;
}

let activeSubscribers = 0;

/**
 * Live game events for a single game, over a raw WebSocket (no SockJS).
 * Auth is injected on the STOMP CONNECT header as `Authorization: Bearer <jwt>`
 * (the backend STOMP and handshake interceptors both accept it).
 */
export function useGameWebSocket(gameId: number | null) {
  const clientRef = useRef<Client | null>(null);
  const { setConnecting, reset } = useGameStore();

  useEffect(() => {
    if (!gameId) return;

    let cancelled = false;
    // The store is a singleton shared by the player and admin live screens.
    // Only the last one to unmount may clear it, otherwise leaving one screen
    // wipes the other's live numbers.
    activeSubscribers += 1;

    (async () => {
      const token = await tokenStorage.get();
      if (cancelled) return;
      if (!token) {
        setConnecting(true);
        return;
      }

      setConnecting(true);

      const client = new Client({
        webSocketFactory: () => new WebSocket(`${getWsBaseUrl()}?token=${encodeURIComponent(token)}`),
        connectHeaders: {
          Authorization: `Bearer ${token}`,
        },
        reconnectDelay: 5000,
        onConnect: () => {
          if (cancelled) return;
          setConnecting(false);
          client.subscribe(`/topic/game/${gameId}`, (message: IMessage) => {
            try {
              const event: GameEvent = JSON.parse(message.body);
              handleGameEvent(event);
            } catch {
              console.error('Failed to parse game event');
            }
          });
        },
        onDisconnect: () => {
          if (!cancelled) setConnecting(true);
        },
      });

      client.activate();
      clientRef.current = client;
    })();

    return () => {
      cancelled = true;
      activeSubscribers -= 1;
      if (activeSubscribers <= 0) {
        activeSubscribers = 0;
        reset();
      }
      clientRef.current?.deactivate();
    };
  }, [gameId, setConnecting, reset]);
}

function handleGameEvent(event: GameEvent) {
  const store = useGameStore.getState();

  switch (event.type) {
    case 'NUMBER_CALLED':
      store.addCalledNumber(event.data as unknown as CalledNumberResponse);
      break;
    case 'GAME_STATUS_CHANGED':
      store.setGameStatus(event.data.status as GameStatus);
      if (event.data.startTime) {
        store.setStartTime(event.data.startTime as string);
      } else if (event.data.status !== 'STARTING') {
        store.setStartTime(null);
      }
      // A countdown tells every player what is about to happen, so players are never
      // left wondering why a resumed game went quiet.
      if (event.data.status === 'STARTING') {
        store.setStartReason((event.data.reason as string | undefined) ?? 'start');
      } else {
        store.setStartReason(null);
      }
      if (event.data.status === 'IN_PROGRESS') {
        store.setClaimPending(null);
        store.setRestartNotice(null);
      }
      break;
    case 'GAME_RESTARTED': {
      const key = event.data.messageKey as string | undefined;
      const localized = key ? translateClientMessage(`ws.${key}`) : null;
      store.setRestartNotice(
        localized ??
          (event.data.message as string) ??
          translateClientMessage('ws.game.restarted.multiClaim') ??
          'The game is restarting because more than 3 players claimed Bingo at once. All registered players keep their cards and can play again — dealing a fresh set of numbers.'
      );
      store.setGameStatus(GameStatus.STARTING);
      store.setStartReason('restart');
      store.setCalledNumbers([]);
      store.setTotalNumbersCalled(0);
      break;
    }
    case 'CLAIM_PENDING':
      store.setGameStatus(GameStatus.CLAIM_PENDING);
      store.setClaimPending(event.data as unknown as BingoClaimResponse);
      break;
    case 'CLAIM_RESOLVED':
      store.setGameStatus(event.data.status as GameStatus);
      store.setClaimPending(null);
      break;
  }
}