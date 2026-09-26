import { Stack } from 'expo-router';
import { Linking, View } from 'react-native';
import { Button, Card, Screen, Subtitle, Title } from '@/components/ui';
import { useQuery } from '@tanstack/react-query';
import { configApi } from '@/api';
import { useTranslate } from '@/hooks/useTranslate';

/**
 * Shown after the phone/password login returns HTTP 421 (no password set).
 * Directs the user to the registration bot to create their password.
 */
export default function NoPasswordScreen() {
  const t = useTranslate();

  const { data } = useQuery({
    queryKey: ['auth/public-config'],
    queryFn: () => configApi.publicInfo(),
    staleTime: Infinity,
  });

  const botUsername = data?.data?.registrationBotUsername;

  const openBot = () => {
    const username = typeof botUsername === 'string' && botUsername ? botUsername : null;
    if (username) {
      void Linking.openURL(`https://t.me/${username}`);
      return;
    }
    void Linking.openURL('https://t.me');
  };

  return (
    <Screen className="justify-center">
      <Stack.Screen options={{ headerShown: false }} />
      <View className="gap-6">
        <View className="gap-2">
          <Title>{t('auth.noPasswordTitle') ?? 'No password yet'}</Title>
          <Subtitle>
            {t('auth.noPasswordHint') ??
              'This account has no password. Open the BingoPlus bot in Telegram, tap your invite link, and use “Create Password” to set one — then log in here.'}
          </Subtitle>
        </View>

        <Card className="gap-4">
          {typeof botUsername === 'string' && botUsername ? (
            <Subtitle className="text-center">{botUsername}</Subtitle>
          ) : null}
          <Button onPress={openBot}>
            {t('auth.openBot') ?? 'Open Telegram bot'}
          </Button>
        </Card>
      </View>
    </Screen>
  );
}