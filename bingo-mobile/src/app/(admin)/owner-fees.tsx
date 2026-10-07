import { useQuery } from '@tanstack/react-query';
import * as ImagePicker from 'expo-image-picker';
import { useState } from 'react';
import { Alert, FlatList, Pressable, ScrollView, Text, View } from 'react-native';
import { agentsApi, screenshotsApi } from '@/api';
import { AppTextInput, Button, Card, FieldLabel, Modal, Screen, ScreenHeader, Title } from '@/components/ui';
import { useTranslate } from '@/hooks/useTranslate';
import { getClientLocale } from '@/lib/clientTranslations';
import { useTheme } from '@/lib/theme';
import { OwnerFeeSettlementResponse } from '@/types';

export default function AdminOwnerFeesScreen() {
  const t = useTranslate();
  const { colors } = useTheme();
  const summaryQuery = useQuery({
    queryKey: ['admin/fee-summary'],
    queryFn: () => agentsApi.getFeeSummary(),
  });
  const settlementsQuery = useQuery({
    queryKey: ['admin/fee-settlements'],
    queryFn: () => agentsApi.getFeeSettlements(),
  });

  const summary = summaryQuery.data?.data;
  const settlements: OwnerFeeSettlementResponse[] = settlementsQuery.data?.data ?? [];
  const [amount, setAmount] = useState('');
  const [proof, setProof] = useState<{ uri: string; name: string; type: string } | null>(null);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [zoom, setZoom] = useState<string | null>(null);

  const pickProof = async () => {
    const perm = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!perm.granted) {
      Alert.alert(t('mobile.photosDenied') ?? 'Photos permission needed');
      return;
    }
    const res = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ['images'],
      quality: 0.7,
    });
    if (!res.canceled && res.assets[0]) {
      const a = res.assets[0];
      setProof({ uri: a.uri, name: a.fileName ?? 'proof.jpg', type: a.mimeType ?? 'image/jpeg' });
    }
  };

  const submit = async () => {
    const value = Number(amount);
    if (!value || value <= 0) return;
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      let screenshotUrl: string | undefined;
      if (proof) {
        const up = await screenshotsApi.upload(proof);
        screenshotUrl = up.data;
      }
      await agentsApi.createFeeSettlement({ amount: value, screenshotUrl });
      setNotice(t('admin.feeSettlementSubmitted') ?? 'Payment recorded');
      setAmount('');
      setProof(null);
      await settlementsQuery.refetch();
      await summaryQuery.refetch();
    } catch (e) {
      setError((e as { userMessage?: string }).userMessage ?? 'Could not submit payment');
    } finally {
      setBusy(false);
    }
  };

  return (
    <Screen>
      <ScreenHeader title={t('admin.ofTitle') ?? 'Owner fees'} />

      <ScrollView contentContainerClassName="gap-4 pb-8">
        <View className="flex-row gap-3">
          <Metric label={t('admin.cashOwedToOwner') ?? 'Owed'} value={String(summary?.owed ?? 0)} accent />
          <Metric label={t('admin.accruedCollected') ?? 'Accrued'} value={String(summary?.accrued ?? 0)} />
          <Metric label={t('admin.settled') ?? 'Settled'} value={String(summary?.settled ?? 0)} />
        </View>

        <Card className="gap-3">
          <Text className="font-semibold" style={{ color: colors.textPrimary }}>{t('admin.payOwnerFees') ?? 'Record a payment'}</Text>
          <FieldLabel>{t('admin.amount') ?? 'Amount'}</FieldLabel>
          <AppTextInput value={amount} onChangeText={setAmount} keyboardType="numeric" placeholder={t('admin.enterAmountPaid') ?? 'Amount paid'} />
          <FieldLabel>{t('admin.transferScreenshot') ?? 'Transfer proof'}</FieldLabel>
          <Button variant="outline" onPress={() => void pickProof()}>
            {proof ? (t('admin.attachedFile', { name: proof.name }) ?? 'Proof attached') : (t('player.attachScreenshot') ?? 'Attach screenshot')}
          </Button>
          {proof ? (
            <Pressable onPress={() => setZoom(proof.uri)} className="active:opacity-80">
              <Card className="p-0 overflow-hidden" style={{ width: 80, height: 80 }}>
                <View />
              </Card>
            </Pressable>
          ) : null}
          {error ? <Text className="text-sm" style={{ color: colors.danger }}>✕ {error}</Text> : null}
          {notice ? <Text className="text-sm" style={{ color: colors.accent }}>✓ {notice}</Text> : null}
          <Button disabled={busy} onPress={() => void submit()}>
            {busy ? (t('admin.submitting') ?? 'Submitting…') : (t('admin.submitCashPayment') ?? 'Submit payment')}
          </Button>
        </Card>

        <FlatList
          data={settlements}
          extraData={getClientLocale()}
          keyExtractor={(s) => String(s.id)}
          contentContainerClassName="gap-3"
          scrollEnabled={false}
          ListEmptyComponent={
            <Card>
              <Text className="text-center" style={{ color: colors.textSecondary }}>
                {t('admin.noSettlements') ?? 'No payments yet'}
              </Text>
            </Card>
          }
          renderItem={({ item }) => (
            <Card className="gap-1">
              <Text className="font-semibold" style={{ color: colors.textPrimary }}>
                {t('admin.cashPaid', { amount: String(item.amount) }) ?? `${item.amount} birr paid`}
              </Text>
              <Text className="text-xs" style={{ color: colors.textSecondary }}>{t(`status.${item.status}`) ?? item.status}</Text>
              {item.rejectionReason ? (
                <Text className="text-xs" style={{ color: colors.danger }}>
                  {t('admin.wdReason', { reason: item.rejectionReason }) ?? `Reason: ${item.rejectionReason}`}
                </Text>
              ) : null}
            </Card>
          )}
        />
      </ScrollView>

      {zoom && <ZoomModal url={zoom} onClose={() => setZoom(null)} />}
    </Screen>
  );
}

function Metric({ label, value, accent }: { label: string; value: string; accent?: boolean }) {
  const { colors } = useTheme();
  return (
    <Card className="flex-1 p-3 items-center">
      <Title className={`text-xl ${accent ? '' : ''}`} style={accent ? { color: colors.gold } : undefined}>{value}</Title>
      <Text className="text-xs text-center" style={{ color: colors.textSecondary }}>{label}</Text>
    </Card>
  );
}

function ZoomModal({ url, onClose }: { url: string; onClose: () => void }) {
  const { colors } = useTheme();
  return (
    <Modal onClose={onClose}>
      <Card className="items-center gap-2">
        <Text className="text-xs" style={{ color: colors.textSecondary }}>Proof</Text>
        <Button variant="outline" onPress={onClose}>
          Close
        </Button>
      </Card>
    </Modal>
  );
}