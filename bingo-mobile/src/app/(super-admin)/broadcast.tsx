import { useState } from 'react';
import { Pressable, ScrollView, Text, View } from 'react-native';
import { broadcastApi } from '@/api';
import { AppTextInput, Button, Card, FieldLabel, Screen, SectionHeader } from '@/components/ui';
import { useTranslate } from '@/hooks/useTranslate';
import { useTheme } from '@/lib/theme';

const TARGETS = ['all', 'agents', 'players'] as const;

export default function SuperAdminBroadcastScreen() {
  const { colors } = useTheme();
  const t = useTranslate();
  const [target, setTarget] = useState<(typeof TARGETS)[number]>('all');
  const [message, setMessage] = useState('');
  const [sending, setSending] = useState(false);
  const [result, setResult] = useState<string | null>(null);
  const [isError, setIsError] = useState(false);

  const send = async () => {
    if (!message.trim()) return;
    setSending(true);
    setResult(null);
    try {
      const res = await broadcastApi.send({ target, message: message.trim() });
      setResult(res.data ?? res.message ?? (t('admin.broadcastSent') ?? 'Broadcast sent'));
      setIsError(false);
      setMessage('');
    } catch (e) {
      const msg = (e as { userMessage?: string }).userMessage ?? '';
      if (/timeout|timed out/i.test(msg)) {
        setResult(t('super.broadcastTimedOutSmall') ?? 'Broadcast timed out. Try a smaller target group.');
      } else {
        setResult(msg || (t('admin.broadcastFailed') ?? 'Broadcast failed'));
      }
      setIsError(true);
    } finally {
      setSending(false);
    }
  };

  const targetLabel = (k: string) =>
    k === 'all'
      ? (t('super.allUsers') ?? 'All Users')
      : k === 'agents'
        ? (t('super.agentsOption') ?? 'Agents')
        : (t('super.playersOption') ?? 'Players');

  return (
    <Screen>
      <SectionHeader
        eyebrow={t('super.sbEyebrow') ?? ''}
        title={t('super.sbTitle') ?? 'Send a message'}
        description={t('super.sbDesc') ?? ''}
      />
      <ScrollView contentContainerClassName="gap-4">
        <Card className="gap-3">
          <FieldLabel>{t('super.target') ?? 'Target'}</FieldLabel>
          <View className="flex-row gap-2">
            {TARGETS.map((k) => (
              <Pressable
                key={k}
                onPress={() => setTarget(k)}
                className="flex-1 rounded-full border px-3 py-2 items-center"
                style={
                  target === k
                    ? { backgroundColor: colors.primary, borderColor: colors.primary }
                    : { borderColor: colors.borderInactive }
                }
              >
                <Text
                  className={target === k ? 'text-white' : undefined}
                  style={target === k ? undefined : { color: colors.textSecondary }}
                >
                  {targetLabel(k)}
                </Text>
              </Pressable>
            ))}
          </View>
          <FieldLabel>{t('admin.message') ?? 'Message'}</FieldLabel>
          <AppTextInput
            value={message}
            onChangeText={setMessage}
            placeholder={t('admin.broadcastPlaceholder') ?? 'Write your message…'}
            multiline
            numberOfLines={5}
            textAlignVertical="top"
            style={{ minHeight: 120 }}
          />
          {result ? (
            <Text className="text-sm" style={{ color: isError ? colors.danger : colors.accent }}>{result}</Text>
          ) : null}
          <Button disabled={sending || !message.trim()} onPress={() => void send()}>
            {sending ? (t('admin.sending') ?? 'Sending…') : (t('admin.sendBroadcast') ?? 'Send Broadcast')}
          </Button>
        </Card>
      </ScrollView>
    </Screen>
  );
}