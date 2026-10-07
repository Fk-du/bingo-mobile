import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Alert, FlatList, RefreshControl, Text, View } from 'react-native';
import { playersApi } from '@/api';
import { AppTextInput, Button, Card, FieldLabel, Modal, Screen, ScreenHeader } from '@/components/ui';
import { useTranslate } from '@/hooks/useTranslate';
import { getClientLocale } from '@/lib/clientTranslations';
import { useTheme } from '@/lib/theme';
import { PlayerResponse } from '@/types';

export default function AdminPlayersScreen() {
  const t = useTranslate();
  const { colors } = useTheme();
  const { data, isFetching, refetch } = useQuery({
    queryKey: ['admin/players'],
    queryFn: () => playersApi.list(),
  });

  const [search, setSearch] = useState('');
  const [funding, setFunding] = useState<PlayerResponse | null>(null);

  const players: PlayerResponse[] = data?.data ?? [];

  const filtered = search.trim()
    ? players.filter(
        (p) =>
          (p.username ?? '').toLowerCase().includes(search.toLowerCase()) ||
          (p.phoneNumber ?? '').includes(search) ||
          String(p.id).includes(search)
      )
    : players;

  return (
    <Screen>
      <ScreenHeader title={t('admin.playersTitle') ?? 'Players'} />
      <AppTextInput
        value={search}
        onChangeText={setSearch}
        placeholder={t('admin.searchPlaceholder') ?? 'Search players…'}
        className="mb-3"
      />
      <FlatList
        data={filtered}
        extraData={getClientLocale()}
        keyExtractor={(p) => String(p.id)}
        refreshControl={
          <RefreshControl refreshing={isFetching} onRefresh={() => refetch()} tintColor="#6B5BFF" />
        }
        contentContainerClassName="gap-3 pb-8"
        ListEmptyComponent={
          <Card>
            <Text className="text-center" style={{ color: colors.textSecondary }}>
              {t('admin.noPlayersFound') ?? 'No players found'}
            </Text>
          </Card>
        }
        renderItem={({ item }) => (
          <Card className="flex-row justify-between items-center">
            <View className="flex-1">
              <Text className="font-semibold" style={{ color: colors.textPrimary }}>
                {item.username ?? `#${item.id}`}
              </Text>
              <Text className="text-sm" style={{ color: colors.textSecondary }}>
                {item.phoneNumber ?? ''} · {item.balance ?? 0}
              </Text>
            </View>
            <Button variant="outline" onPress={() => setFunding(item)}>
              {t('admin.fundPlayer') ?? 'Fund'}
            </Button>
          </Card>
        )}
      />

      {funding && (
        <FundModal player={funding} onClose={() => setFunding(null)} onDone={() => refetch()} t={t} />
      )}
    </Screen>
  );
}

function FundModal({
  player,
  onClose,
  onDone,
  t,
}: {
  player: PlayerResponse;
  onClose: () => void;
  onDone: () => void;
  t: ReturnType<typeof useTranslate>;
}) {
  const { colors } = useTheme();
  const [amount, setAmount] = useState('');
  const [busy, setBusy] = useState(false);

  const fund = async () => {
    const value = Number(amount);
    if (!value || value <= 0) return;
    setBusy(true);
    try {
      await playersApi.fund(player.id, { amount: value });
      onDone();
      onClose();
    } catch (e) {
      Alert.alert((e as { userMessage?: string }).userMessage ?? 'Failed to fund');
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal onClose={onClose}>
      <Card className="gap-3">
        <Text className="font-semibold" style={{ color: colors.textPrimary }}>
          {t('admin.balanceCoins', { name: player.username ?? `#${player.id}`, balance: player.balance ?? 0 })}
        </Text>
        <FieldLabel>{t('admin.enterCoinAmount') ?? 'Enter birr amount'}</FieldLabel>
        <AppTextInput value={amount} onChangeText={setAmount} keyboardType="numeric" />
        <Button onPress={() => void fund()} disabled={busy}>
          {busy ? t('admin.funding') ?? 'Funding…' : t('admin.fund') ?? 'Fund'}
        </Button>
      </Card>
    </Modal>
  );
}