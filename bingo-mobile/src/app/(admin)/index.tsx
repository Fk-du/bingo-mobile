import { useQuery } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { Alert, FlatList, Pressable, RefreshControl, Text, View } from 'react-native';
import { useEffect, useState } from 'react';
import { gamesApi, reportsApi } from '@/api';
import { AppTextInput, Button, Card, FieldLabel, Screen, ScreenHeader, Subtitle, Title } from '@/components/ui';
import { useTranslate } from '@/hooks/useTranslate';
import { getClientLocale } from '@/lib/clientTranslations';
import { AdminGameResponse, GameStatus } from '@/types';

export default function AdminDashboardScreen() {
  const t = useTranslate();
  const router = useRouter();
  const [busyId, setBusyId] = useState<number | null>(null);

  const gamesQuery = useQuery({ queryKey: ['admin/games'], queryFn: () => gamesApi.getActive() });
  const metricsQuery = useQuery({ queryKey: ['admin/metrics'], queryFn: () => reportsApi.dashboard() });

  const games: AdminGameResponse[] = gamesQuery.data?.data ?? [];
  const metrics = metricsQuery.data?.data as
    | Record<string, unknown>
    | undefined;

  const pendingCount = games.reduce(
    (sum, g) => sum + (Number((g as { pendingClaims?: number }).pendingClaims) || 0),
    0
  );

  const start = async (id: number) => {
    setBusyId(id);
    try {
      await gamesApi.start(id);
      await gamesQuery.refetch();
    } catch (e) {
      Alert.alert((e as { userMessage?: string }).userMessage ?? 'Failed to start');
    } finally {
      setBusyId(null);
    }
  };

  /**
   * Registration is open, so the pot keeps growing. The admin picks the prize
   * here, once they can see who actually turned up, and Start stays locked
   * until they have.
   */
  const savePrize = async (id: number, amount: number) => {
    setBusyId(id);
    try {
      await gamesApi.updateSettings(id, { prizeAmount: amount });
      await gamesQuery.refetch();
    } catch (e) {
      Alert.alert((e as { userMessage?: string }).userMessage ?? 'Failed to save the prize');
    } finally {
      setBusyId(null);
    }
  };

  return (
    <Screen>
      <ScreenHeader title={t('admin.gamesManageTitle') ?? 'Game management'} />

      <View className="flex-row gap-3 mb-4">
        <QuickStat label={t('admin.totalGamesMetric') ?? 'Games'} value={String(metrics?.totalGames ?? games.length)} />
        <QuickStat
          label={t('admin.pendingClaims') ?? 'Claims'}
          value={String(pendingCount)}
          accent
        />
        <QuickStat label={t('admin.playersMetric') ?? 'Players'} value={String(metrics?.players ?? '—')} />
      </View>

      <FlatList
        data={games}
        extraData={getClientLocale()}
        keyExtractor={(g) => String(g.id)}
        refreshControl={
          <RefreshControl refreshing={gamesQuery.isFetching} onRefresh={() => gamesQuery.refetch()} tintColor="#6B5BFF" />
        }
        contentContainerClassName="gap-3 pb-8"
        ListHeaderComponent={
          <View className="gap-3 mb-2">
            <Pressable
              onPress={() => router.push('/(admin)/new-game')}
              className="active:opacity-80"
            >
              <Card className="flex-row justify-between items-center bg-bp-primary15 border-bp-primary40">
                <Text className="text-bp-textPrimary font-semibold">
                  {t('admin.gamesManageTitle') ?? 'Game management'}
                </Text>
                <Text className="text-bp-primary">›</Text>
              </Card>
            </Pressable>
            <Pressable
              onPress={() => router.push('/(admin)/players')}
              className="active:opacity-80"
            >
              <Card className="flex-row justify-between items-center">
                <Text className="text-bp-textPrimary font-semibold">{t('admin.playersTitle') ?? 'Player registry'}</Text>
                <Text className="text-bp-primary">›</Text>
              </Card>
            </Pressable>
            <Pressable
              onPress={() => router.push('/(admin)/coins')}
              className="active:opacity-80"
            >
              <Card className="flex-row justify-between items-center">
                <Text className="text-bp-textPrimary font-semibold">{t('admin.topUpTitle') ?? 'Top-up approvals'}</Text>
                <Text className="text-bp-primary">›</Text>
              </Card>
            </Pressable>
            <Pressable
              onPress={() => router.push('/(admin)/withdrawals')}
              className="active:opacity-80"
            >
              <Card className="flex-row justify-between items-center">
                <Text className="text-bp-textPrimary font-semibold">{t('admin.wdTitle') ?? 'Payout requests'}</Text>
                <Text className="text-bp-primary">›</Text>
              </Card>
            </Pressable>
            <Pressable
              onPress={() => router.push('/(admin)/cards')}
              className="active:opacity-80"
            >
              <Card className="flex-row justify-between items-center">
                <Text className="text-bp-textPrimary font-semibold">{t('admin.cardsTitle') ?? 'Card pool'}</Text>
                <Text className="text-bp-primary">›</Text>
              </Card>
            </Pressable>
            <Pressable
              onPress={() => router.push('/(admin)/broadcast')}
              className="active:opacity-80"
            >
              <Card className="flex-row justify-between items-center">
                <Text className="text-bp-textPrimary font-semibold">{t('admin.broadcastTitle') ?? 'Broadcast'}</Text>
                <Text className="text-bp-primary">›</Text>
              </Card>
            </Pressable>
            <Pressable
              onPress={() => router.push('/(admin)/profile')}
              className="active:opacity-80"
            >
              <Card>
                <View className="flex-row justify-between items-center">
                  <View className="gap-0.5">
                    <Text className="text-bp-textPrimary font-semibold">
                      {t('admin.profileTitle') ?? 'Profile'}
                    </Text>
                    <Text className="text-bp-textSecondary text-xs">
                      {t('admin.profileMenuDesc') ?? 'Invite players · Deposit account · Language'}
                    </Text>
                  </View>
                  <Text className="text-bp-primary">›</Text>
                </View>
              </Card>
            </Pressable>
            <Pressable
              onPress={() => router.push('/(admin)/owner-fees')}
              className="active:opacity-80"
            >
              <Card className="flex-row justify-between items-center">
                <Text className="text-bp-textPrimary font-semibold">{t('admin.ofTitle') ?? 'Owner fees'}</Text>
                <Text className="text-bp-primary">›</Text>
              </Card>
            </Pressable>
            <Pressable
              onPress={() => router.push('/(admin)/notifications')}
              className="active:opacity-80"
            >
              <Card className="flex-row justify-between items-center">
                <Text className="text-bp-textPrimary font-semibold">{t('mobile.notifications') ?? 'Notifications'}</Text>
                <Text className="text-bp-primary">›</Text>
              </Card>
            </Pressable>
            <Text className="text-bp-textSecondary text-xs uppercase tracking-wider mt-2">
              {t('admin.activeGames') ?? 'Active games'}
            </Text>
          </View>
        }
        ListEmptyComponent={
          <Card>
            <Text className="text-bp-textSecondary text-center">
              {t('admin.noGamesYet') ?? 'No games yet. Create your first game!'}
            </Text>
          </Card>
        }
        renderItem={({ item }) => {
          const open = item.status === GameStatus.REGISTRATION_OPEN;
          return (
            <Card className="gap-2">
              <View className="flex-row justify-between items-center">
                <Text className="text-bp-textPrimary font-semibold text-lg">
                  {t('game.gameNumber', { id: String(item.id) }) ?? `Game #${item.id}`}
                </Text>
                <Text className="text-bp-accentInk">{t(`status.${item.status}`) ?? item.status}</Text>
              </View>
              {open ? (
                <>
                  <View className="flex-row justify-between">
                    <Subtitle>
                      {t('admin.entryFee') ?? 'Entry'}: {item.entryFee}
                    </Subtitle>
                    <Subtitle>
                      {t('admin.prizePoolLabel') ?? 'Collected'}: {item.prizePool}
                    </Subtitle>
                    <Subtitle>
                      {t('nav.admin.players') ?? 'Players'}: {item.registeredPlayers ?? 0}
                    </Subtitle>
                  </View>
                  <PrizeEditor
                    gameId={item.id}
                    pool={item.prizePool}
                    minPrize={item.minPrize}
                    maxPrize={item.maxPrize}
                    currentPrize={item.prizeAmount}
                    busy={busyId != null}
                    onSave={savePrize}
                  />
                </>
              ) : null}
              <View className="flex-row gap-2">
                {open && (
                  <Button
                    variant="primary"
                    onPress={() => void start(item.id)}
                    disabled={busyId != null || item.prizeAmount == null}
                  >
                    {busyId === item.id
                      ? (t('admin.working') ?? 'Working…')
                      : item.prizeAmount == null
                        ? (t('admin.setPrizeFirst') ?? 'Set the prize to start')
                        : (t('admin.start') ?? 'Start Game')}
                  </Button>
                )}
                <Button
                  variant="outline"
                  onPress={() => router.push({ pathname: '/(admin)/game/[id]', params: { id: String(item.id) } })}
                >
                  {t('admin.manage') ?? 'Manage'}
                </Button>
              </View>
            </Card>
          );
        }}
      />
    </Screen>
  );
}

/**
 * Set the prize for a game whose registration is still open. The platform caps
 * the band, so the input is pre-filled from a suggestion that respects both the
 * cap and the admin's preferred rake.
 */
function PrizeEditor({
  gameId,
  pool,
  minPrize,
  maxPrize,
  currentPrize,
  busy,
  onSave,
}: {
  gameId: number;
  pool: number;
  minPrize?: number | null;
  maxPrize?: number | null;
  currentPrize?: number | null;
  busy: boolean;
  onSave: (id: number, amount: number) => Promise<void>;
}) {
  const t = useTranslate();
  const [amount, setAmount] = useState(currentPrize == null ? '' : String(currentPrize));
  const [suggestion, setSuggestion] = useState<{ min: number; max: number; prize: number } | null>(null);

  // The band moves as players keep joining, so it is re-read whenever the
  // collected amount changes rather than pinned when the card first rendered.
  useEffect(() => {
    let cancelled = false;
    gamesApi
      .getPrizeSuggestion(gameId)
      .then((res) => {
        if (cancelled) return;
        setSuggestion({ min: res.data.minPrize, max: res.data.maxPrize, prize: res.data.suggestedPrize });
      })
      .catch(() => {
        /* the band is advisory here; the server is the authority on save */
      });
    return () => {
      cancelled = true;
    };
  }, [gameId, pool]);

  const min = suggestion?.min ?? minPrize ?? 0;
  const max = suggestion?.max ?? maxPrize ?? 0;
  const parsed = Number(amount);
  const valid = amount.trim() !== '' && Number.isFinite(parsed) && parsed >= min && parsed <= max;
  const saved = currentPrize != null && Number(currentPrize) === parsed;

  if (min === 0 && max === 0) {
    // Nothing has been collected yet, so there is no band to set a prize in.
    return (
      <Text className="text-bp-textSecondary text-xs">
        {t('admin.prizeAfterFirstPlayer') ?? 'The prize can be set once the first player joins.'}
      </Text>
    );
  }

  return (
    <View className="gap-2 rounded-xl border border-bp-border p-3">
      <FieldLabel>
        {t('admin.prizeAmount') ?? 'Prize for the winners'}{' '}
        {t('admin.prizeRange', { min: String(min), max: String(max) }) ?? `(${min} – ${max})`}
      </FieldLabel>
      <View className="flex-row items-center gap-2">
        <AppTextInput
          className="flex-1"
          value={amount}
          onChangeText={setAmount}
          keyboardType="decimal-pad"
          placeholder={String(suggestion?.prize ?? min)}
        />
        <Button
          variant="outline"
          disabled={busy || suggestion == null}
          onPress={() => setAmount(String(suggestion?.prize ?? min))}
        >
          {t('admin.useSuggestion') ?? 'Suggest'}
        </Button>
      </View>
      {!valid && amount.trim() !== '' ? (
        <Text className="text-bp-danger text-xs">
          {t('admin.prizeOutOfRange', { min: String(min), max: String(max) }) ??
            `Enter a prize between ${min} and ${max}.`}
        </Text>
      ) : null}
      <Button
        variant="secondary"
        disabled={!valid || saved || busy}
        onPress={() => void onSave(gameId, parsed)}
      >
        {saved ? (t('admin.prizeSaved') ?? 'Prize set') : (t('admin.savePrize') ?? 'Set prize')}
      </Button>
    </View>
  );
}

function QuickStat({ label, value, accent }: { label: string; value: string; accent?: boolean }) {
  return (
    <Card className="flex-1 p-3 items-center">
      <Title className={`text-xl ${accent ? 'text-bp-primary' : ''}`}>{value}</Title>
      <Subtitle className="text-xs text-center">{label}</Subtitle>
    </Card>
  );
}