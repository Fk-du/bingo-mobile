import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useFocusEffect, useRouter } from 'expo-router';
import { useCallback } from 'react';
import { RefreshControl, ScrollView, Text, View } from 'react-native';
import { gamesApi, walletApi } from '@/api';
import { Button, EmptyState, Screen, ScreenHeader, Subtitle, Title } from '@/components/ui';
import { ThemeToggleButton } from '@/components/ui/ThemeToggleButton';
import { useTranslate } from '@/hooks/useTranslate';
import { getClientLocale } from '@/lib/clientTranslations';
import { PlayerGameResponse } from '@/types';

/**
 * The player's landing screen.
 *
 * An admin can only run one game at a time, so there is never a list to
 * choose between: this screen resolves the single live game and hands the
 * player straight to it. All it keeps for itself is the no-game case, which
 * still needs somewhere to show the balance and a way to deposit.
 */
export default function PlayerHomeScreen() {
  const t = useTranslate();
  const router = useRouter();
  const qc = useQueryClient();

  // This screen stays mounted as the first tab, so the lookup is re-run on
  // every focus: the admin may have opened registration while the player was
  // on another tab, and they should land straight on the game.
  const gamesQuery = useQuery({
    queryKey: ['player/games'],
    queryFn: () => gamesApi.getActive(),
    refetchInterval: 10_000,
  });
  const walletQuery = useQuery({ queryKey: ['wallet'], queryFn: () => walletApi.get() });

  const games: PlayerGameResponse[] = gamesQuery.data?.data ?? [];
  const liveGame = games[0];

  const goGame = useCallback(
    (g: PlayerGameResponse) => {
      router.push({ pathname: '/(player)/game/[id]', params: { id: String(g.id) } });
    },
    [router]
  );

  useFocusEffect(
    useCallback(() => {
      // The first load has nothing to redirect away from, so it stays on the
      // home screen to show the balance until an id is known.
      if (liveGame) goGame(liveGame);
    }, [liveGame, goGame])
  );

  const refresh = useCallback(async () => {
    await qc.invalidateQueries({ queryKey: ['player/games'] });
    const res = await gamesApi.getActive();
    const found: PlayerGameResponse[] = res.data ?? [];
    if (found[0]) goGame(found[0]);
  }, [qc, goGame]);

  return (
    <Screen>
      <ScreenHeader
        title={t('common.appName') ?? 'BingoPlus'}
        right={<ThemeToggleButton />}
      />

      <View className="mb-4 rounded-2xl border border-bp-borderInactive bg-bp-surface p-4">
        <Subtitle>{t('player.yourBalance') ?? 'Your Balance'}</Subtitle>
        <Title className="text-2xl">
          {walletQuery.data?.data.balance?.toLocaleString() ?? '—'}
        </Title>
        <Subtitle className="text-xs">{t('player.coinsAvailable') ?? 'birr available'}</Subtitle>
        <Button
          variant="outline"
          className="mt-3 self-start"
          onPress={() => router.push('/(player)/wallet')}
        >
          {t('player.buyCoins') ?? 'Buy Birr'}
        </Button>
      </View>

      <ScrollView
        style={{ flex: 1 }}
        refreshControl={
          <RefreshControl refreshing={gamesQuery.isFetching} onRefresh={refresh} tintColor="#6B5BFF" />
        }
      >
        <EmptyState
          title={t('mobile.noGames') ?? 'No games available right now'}
          description={t('player.waitingForNextGame') ?? 'Your agent will open a new game soon.'}
        />
      </ScrollView>

      <Text className="mt-4 text-center text-xs text-bp-textInactive" key={getClientLocale()}>
        {t('player.myGames') ?? 'My Games'}{' '}
        <Text
          className="text-bp-primary font-semibold"
          onPress={() => router.push('/(player)/my-games')}
        >
          {t('player.viewAll') ?? 'View all'}
        </Text>
      </Text>
    </Screen>
  );
}
