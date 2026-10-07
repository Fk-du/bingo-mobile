import { useQuery } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { View } from 'react-native';
import { walletApi } from '@/api';
import { Button, Card, Screen, ScreenBackButton, ScreenHeader, Subtitle, Title } from '@/components/ui';
import { useTranslate } from '@/hooks/useTranslate';
import { useTheme } from '@/lib/theme';

export default function WalletScreen() {
  const t = useTranslate();
  const router = useRouter();
  const { colors } = useTheme();

  const { data: walletData } = useQuery({ queryKey: ['wallet'], queryFn: () => walletApi.get() });

  const balance = walletData?.data.balance ?? 0;
  const frozen = walletData?.data.frozenBalance ?? 0;

  return (
    <Screen>
      <ScreenHeader title={t('mobile.navWallet') ?? 'Wallet'} left={<ScreenBackButton />} />

      <View className="flex-1 justify-center">
        <Card className="w-full items-center gap-6 p-8">
          <View className="items-center gap-1">
            <Subtitle className="text-xs uppercase tracking-wider" style={{ color: colors.textSecondary }}>
              {t('mobile.yourBalance') ?? 'Available Balance'}
            </Subtitle>
            <Title className="text-4xl">{balance.toLocaleString()}</Title>
          </View>

          <View className="items-center gap-1">
            <Subtitle className="text-xs uppercase tracking-wider" style={{ color: colors.textSecondary }}>
              {t('mobile.frozen') ?? 'Frozen'}
            </Subtitle>
            <Title className="text-2xl">{frozen.toLocaleString()}</Title>
          </View>

          <View className="flex-row gap-4 pt-2">
            <Button
              onPress={() => router.push('/(player)/deposit')}
              variant="gold"
              style={{ flex: 1 }}
            >
              {t('mobile.buyCoins') ?? 'Deposit'}
            </Button>
            <Button
              onPress={() => router.push('/(player)/withdraw')}
              variant="primary"
              style={{ flex: 1 }}
            >
              {t('mobile.withdraw') ?? 'Withdraw'}
            </Button>
          </View>
        </Card>
      </View>
    </Screen>
  );
}
