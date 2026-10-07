import { useState } from 'react';
import { ScrollView, Text } from 'react-native';
import { broadcastApi } from '@/api';
import { AppTextInput, Button, Card, FieldLabel, Screen, ScreenHeader } from '@/components/ui';
import { useTranslate } from '@/hooks/useTranslate';
import { useTheme } from '@/lib/theme';

export default function AdminBroadcastScreen() {
  const t = useTranslate();
  const { colors } = useTheme();
  const [message, setMessage] = useState('');
  const [sending, setSending] = useState(false);
  const [result, setResult] = useState<string | null>(null);
  const [isError, setIsError] = useState(false);

  const send = async () => {
    if (!message.trim()) return;
    setSending(true);
    setResult(null);
    try {
      const res = await broadcastApi.send({ target: 'players', message: message.trim() });
      setResult(res.data ?? (t('admin.broadcastSent') ?? 'Broadcast sent'));
      setIsError(false);
      setMessage('');
    } catch {
      setResult(t('admin.broadcastFailed') ?? 'Broadcast failed');
      setIsError(true);
    } finally {
      setSending(false);
    }
  };

  return (
    <Screen>
      <ScreenHeader title={t('admin.broadcastTitle') ?? 'Broadcast'} />
      <ScrollView contentContainerClassName="gap-4">
        <Card className="gap-3">
          <FieldLabel>{t('admin.message') ?? 'Message'}</FieldLabel>
          <AppTextInput
            value={message}
            onChangeText={setMessage}
            placeholder={t('admin.broadcastPlaceholder') ?? 'Write your message to all players…'}
            multiline
            numberOfLines={5}
            textAlignVertical="top"
            style={{ minHeight: 120 }}
          />
          {result ? (
            <Text className="text-sm" style={{ color: isError ? colors.danger : colors.accent }}>{result}</Text>
          ) : null}
          <Button disabled={sending || !message.trim()} onPress={() => void send()}>
            {sending ? (t('admin.sending') ?? 'Sending…') : (t('admin.sendBroadcast') ?? 'Send broadcast')}
          </Button>
        </Card>
      </ScrollView>
    </Screen>
  );
}