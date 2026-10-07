import { useQuery, useQueryClient } from '@tanstack/react-query';
import * as ImagePicker from 'expo-image-picker';
import { useRouter } from 'expo-router';
import { useState } from 'react';
import { Alert, Image, Pressable, ScrollView, Text, View } from 'react-native';
import { coinsApi, getApiErrorMessage, screenshotsApi, walletApi } from '@/api';
import { AppTextInput, Button, Card, Screen, ScreenBackButton, ScreenHeader } from '@/components/ui';
import { useTranslate } from '@/hooks/useTranslate';
import { useTheme } from '@/lib/theme';
import { useAuthStore } from '@/store/auth.store';

const QUICK_AMOUNTS = [100, 250, 500, 1000];

type PickedProof = { uri: string; name: string; type: string };

export default function WalletScreen() {
  const t = useTranslate();
  const qc = useQueryClient();
  const router = useRouter();
  const { colors } = useTheme();
  const user = useAuthStore((s) => s.user);

  const { data: walletData } = useQuery({ queryKey: ['wallet'], queryFn: () => walletApi.get() });

  const balance = walletData?.data.balance ?? 0;
  const frozen = walletData?.data.frozenBalance ?? 0;

  const [buyAmount, setBuyAmount] = useState('');
  const [proof, setProof] = useState<PickedProof | null>(null);
  const [busy, setBusy] = useState(false);
  const [showDeposit, setShowDeposit] = useState(false);

  const pickProof = async () => {
    const perm = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!perm.granted) {
      Alert.alert(t('mobile.proofUploadFailed') ?? 'Upload failed', t('mobile.photosDenied') ?? '');
      return;
    }
    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ['images'],
      quality: 0.7,
    });
    if (result.canceled) return;
    const asset = result.assets[0];
    if (!asset) return;
    setProof({
      uri: asset.uri,
      name: asset.fileName ?? 'payment-proof.jpg',
      type: asset.mimeType ?? 'image/jpeg',
    });
  };

  const requestTopUp = async () => {
    const amount = Number(buyAmount);
    if (!amount || amount <= 0) return;
    if (!proof) {
      Alert.alert(t('mobile.proofUploadFailed') ?? 'Upload failed', t('player.noScreenshotError') ?? '');
      return;
    }
    setBusy(true);
    try {
      const uploaded = await screenshotsApi.upload(proof);
      await coinsApi.createRequest({ amount, screenshotUrl: uploaded.data });
      Alert.alert(t('mobile.topUpSent') ?? 'Payment request sent', t('mobile.afterSending') ?? '');
      setBuyAmount('');
      setProof(null);
      setShowDeposit(false);
      void qc.invalidateQueries({ queryKey: ['wallet'] });
    } catch (e) {
      Alert.alert(t('common.error') ?? 'Error', getApiErrorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Screen>
      <ScreenHeader title={t('mobile.navWallet') ?? 'Wallet'} left={<ScreenBackButton />} />

      <ScrollView contentContainerClassName="gap-6 pb-8">
        <Card className="w-full items-center gap-6 p-8">
          <View className="items-center gap-1">
            <Text className="text-xs font-medium uppercase tracking-wider" style={{ color: colors.textSecondary }}>
              {t('mobile.yourBalance') ?? 'Available Balance'}
            </Text>
            <Text className="text-4xl font-bold" style={{ color: colors.textPrimary }}>
              {balance.toLocaleString()}
            </Text>
          </View>

          <View className="items-center gap-1">
            <Text className="text-xs font-medium uppercase tracking-wider" style={{ color: colors.textSecondary }}>
              {t('mobile.frozen') ?? 'Frozen'}
            </Text>
            <Text className="text-2xl font-bold" style={{ color: colors.textPrimary }}>
              {frozen.toLocaleString()}
            </Text>
          </View>

          <View className="flex-row gap-4 pt-2 w-full">
            <Button
              onPress={() => setShowDeposit((v) => !v)}
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

        {showDeposit && (
          <Card className="gap-3">
            <Text className="font-semibold" style={{ color: colors.textPrimary }}>
              {t('player.buyCoins') ?? 'Buy Birr'}
            </Text>

            {user?.depositAccountInfo ? (
              <View className="gap-1 rounded-xl border p-3" style={{ backgroundColor: colors.surfaceAlt, borderColor: colors.borderInactive }}>
                <Text className="text-xs font-semibold" style={{ color: colors.secondary }}>
                  {t('player.sendDepositTo') ?? 'Send deposit to'}
                </Text>
                <Text className="text-sm" style={{ color: colors.textPrimary }}>{user.depositAccountInfo}</Text>
                <Text className="text-xs" style={{ color: colors.textSecondary }}>
                  {t('player.afterSending') ?? 'After sending, upload your payment screenshot below.'}
                </Text>
              </View>
            ) : null}

            <View className="flex-row flex-wrap gap-2">
              {QUICK_AMOUNTS.map((q) => (
                <Button
                  key={q}
                  variant="outline"
                  disabled={busy}
                  onPress={() => setBuyAmount(String(q))}
                  style={{ paddingVertical: 8, paddingHorizontal: 14 }}
                >
                  {q.toLocaleString()}
                </Button>
              ))}
            </View>

            <AppTextInput
              value={buyAmount}
              onChangeText={setBuyAmount}
              placeholder={t('mobile.amount') ?? 'Amount (birr)'}
              keyboardType="numeric"
            />

            {proof ? (
              <View className="flex-row items-center gap-3">
                <Image source={{ uri: proof.uri }} className="h-14 w-14 rounded-lg" resizeMode="cover" style={{ backgroundColor: colors.surfaceAlt }} />
                <View className="flex-1">
                  <Text className="text-xs" style={{ color: colors.accent }} numberOfLines={1}>
                    {t('player.attached', { filename: proof.name }) ?? proof.name}
                  </Text>
                </View>
                <Pressable onPress={() => setProof(null)} disabled={busy}>
                  <Text className="text-xs" style={{ color: colors.danger }}>{t('mobile.removeProof') ?? 'Remove'}</Text>
                </Pressable>
              </View>
            ) : (
              <Button variant="outline" onPress={pickProof} disabled={busy}>
                {t('mobile.pickScreenshot') ?? 'Attach payment screenshot'}
              </Button>
            )}

            <Button onPress={requestTopUp} disabled={busy}>
              {t('mobile.requestPayment') ?? 'Request payment'}
            </Button>
          </Card>
        )}
      </ScrollView>
    </Screen>
  );
}
