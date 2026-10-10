import { useQuery, useQueryClient } from '@tanstack/react-query';
import * as ImagePicker from 'expo-image-picker';
import { useState } from 'react';
import { Alert, Image, Pressable, ScrollView, Text, View } from 'react-native';
import { coinsApi, configApi, getApiErrorMessage, screenshotsApi, walletApi, withdrawalsApi } from '@/api';
import { DepositAccounts } from '@/components/DepositAccounts';
import { AppTextInput, Button, Card, Screen, ScreenBackButton, ScreenHeader } from '@/components/ui';
import { useTranslate } from '@/hooks/useTranslate';
import { useTheme } from '@/lib/theme';
import { useAuthStore } from '@/store/auth.store';

const QUICK_AMOUNTS = [100, 250, 500, 1000];
const WITHDRAW_QUICK_AMOUNTS = [100, 200, 500, 1000];
const DEFAULT_MIN_WITHDRAWAL = 100;

type PickedProof = { uri: string; name: string; type: string };

export default function WalletScreen() {
  const t = useTranslate();
  const qc = useQueryClient();
  const { colors } = useTheme();
  const user = useAuthStore((s) => s.user);

  const { data: walletData } = useQuery({ queryKey: ['wallet'], queryFn: () => walletApi.get() });
  const { data: configData } = useQuery({ queryKey: ['player/config'], queryFn: () => configApi.get() });

  const balance = walletData?.data.balance ?? 0;
  const frozen = walletData?.data.frozenBalance ?? 0;
  const minWithdrawal = Number(configData?.data?.minWithdrawal) || DEFAULT_MIN_WITHDRAWAL;
  // A withdrawal must leave at least the minimum behind; spending in games is
  // not restricted, so the wallet can still be played down to zero.
  const maxWithdrawable = Math.max(0, balance - minWithdrawal);

  const [buyAmount, setBuyAmount] = useState('');
  const [proof, setProof] = useState<PickedProof | null>(null);
  const [busy, setBusy] = useState(false);
  const [showDeposit, setShowDeposit] = useState(false);
  const [showWithdraw, setShowWithdraw] = useState(false);
  const [wdAmount, setWdAmount] = useState('');
  const [wdDetails, setWdDetails] = useState('');
  const [wdBusy, setWdBusy] = useState(false);

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

  const requestWithdraw = async () => {
    const value = Number(wdAmount);
    if (!value || !wdDetails.trim()) return;
    if (value < minWithdrawal) {
      Alert.alert(
        t('common.error') ?? 'Error',
        t('player.withdrawMin', { amount: String(minWithdrawal) }) ?? `Minimum withdrawal is ${minWithdrawal} birr`
      );
      return;
    }
    if (value > maxWithdrawable) {
      Alert.alert(
        t('common.error') ?? 'Error',
        t('player.withdrawKeepMin', { amount: String(minWithdrawal), max: String(maxWithdrawable) }) ??
          `It's not possible to withdraw if your wallet would drop below ${minWithdrawal} birr. You can withdraw up to ${maxWithdrawable} birr.`
      );
      return;
    }
    setWdBusy(true);
    try {
      const res = await withdrawalsApi.create({ amount: value, payoutDetails: wdDetails.trim() });
      Alert.alert(
        t('player.payoutSuccessTitle') ?? 'Thank you!',
        t('player.payoutSuccessMessage', {
          amount: String(res.data.amount),
          details: res.data.payoutDetails ?? wdDetails.trim(),
        }) ??
          `Your withdrawal of ${res.data.amount} birr has been submitted for review. Keep an eye on your notifications.`
      );
      setWdAmount('');
      setWdDetails('');
      setShowWithdraw(false);
      void qc.invalidateQueries({ queryKey: ['wallet'] });
      void qc.invalidateQueries({ queryKey: ['wallet', 'withdrawals'] });
    } catch (e) {
      Alert.alert(t('common.error') ?? 'Error', getApiErrorMessage(e));
    } finally {
      setWdBusy(false);
    }
  };

  return (
    <Screen>
      <ScreenHeader title={t('mobile.navWallet') ?? 'Wallet'} left={<ScreenBackButton />} />

      <ScrollView contentContainerClassName="gap-6 pb-8">
        <Card className="w-full items-center justify-center gap-4 p-5">
          <View className="flex-row items-center justify-center gap-6">
            <View className="items-center">
              <Text className="text-[10px] font-medium uppercase tracking-wider" style={{ color: colors.textSecondary }}>
                {t('mobile.yourBalance') ?? 'Balance'}
              </Text>
              <Text className="text-xl font-bold" style={{ color: colors.textPrimary }}>
                {balance.toLocaleString()}
              </Text>
            </View>
            <View className="items-center">
              <Text className="text-[10px] font-medium uppercase tracking-wider" style={{ color: colors.textSecondary }}>
                {t('mobile.frozen') ?? 'Frozen'}
              </Text>
              <Text className="text-xl font-bold" style={{ color: colors.textPrimary }}>
                {frozen.toLocaleString()}
              </Text>
            </View>
          </View>

          <View className="flex-row gap-3 w-full pt-2">
            <Button
              onPress={() => {
                setShowWithdraw(false);
                setShowDeposit((v) => !v);
              }}
              variant="gold"
              style={{ flex: 1 }}
            >
              {t('mobile.buyCoins') ?? 'Deposit'}
            </Button>
            <Button
              onPress={() => {
                setShowDeposit(false);
                setShowWithdraw((v) => !v);
              }}
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

            {user?.depositAccounts && user.depositAccounts.length > 0 && (
              <DepositAccounts accounts={user.depositAccounts} />
            )}

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

        {showWithdraw && (
          <Card className="gap-3">
            <Text className="font-semibold" style={{ color: colors.textPrimary }}>
              {t('mobile.withdraw') ?? 'Withdraw'}
            </Text>
            <Text className="text-xs" style={{ color: colors.textSecondary }}>
              {t('player.minWithdrawalInfo', { amount: String(minWithdrawal) }) ??
                `Minimum withdrawal: ${minWithdrawal} birr`}
            </Text>

            <View className="flex-row flex-wrap gap-2">
              {WITHDRAW_QUICK_AMOUNTS.map((q) => (
                <Button
                  key={q}
                  variant="outline"
                  disabled={wdBusy || q > maxWithdrawable}
                  onPress={() => setWdAmount(String(q))}
                  style={{ paddingVertical: 8, paddingHorizontal: 14 }}
                >
                  {q.toLocaleString()}
                </Button>
              ))}
            </View>

            <AppTextInput
              value={wdAmount}
              onChangeText={setWdAmount}
              placeholder={t('mobile.amount') ?? 'Amount (birr)'}
              keyboardType="numeric"
            />
            <AppTextInput
              value={wdDetails}
              onChangeText={setWdDetails}
              placeholder={t('mobile.payoutDetails') ?? 'Bank account or Telebirr number'}
            />

            <Text className="text-xs" style={{ color: colors.textSecondary }}>
              {t('player.withdrawHint') ?? 'e.g. CBE 1000987654321 or Telebirr +251912345678'}
            </Text>

            <Button onPress={requestWithdraw} disabled={wdBusy || !wdAmount || !wdDetails.trim()}>
              {wdBusy
                ? (t('player.submitting') ?? 'Submitting…')
                : (t('player.requestWithdrawal') ?? 'Request withdrawal')}
            </Button>
          </Card>
        )}
      </ScrollView>
    </Screen>
  );
}
