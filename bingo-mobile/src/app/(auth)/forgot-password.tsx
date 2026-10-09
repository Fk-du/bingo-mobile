import { Stack, useRouter } from 'expo-router';
import { useState } from 'react';
import { KeyboardAvoidingView, Platform, Text, View } from 'react-native';
import { AppTextInput, Button, Card, FieldLabel, Screen, Subtitle, Title } from '@/components/ui';
import { useTranslate } from '@/hooks/useTranslate';
import { useTheme } from '@/lib/theme';
import { authApi } from '@/api/auth.api';

/**
 * Step 1 of forgot-password: enter the phone. If an account exists, a
 * one-time 6-digit code is sent to the owner's Telegram. The response is the
 * same whether or not the phone is registered, so numbers can't be harvested;
 * a phone whose account never set a password gets the 421 no_password screen.
 */
export default function ForgotPasswordScreen() {
  const { colors } = useTheme();
  const t = useTranslate();
  const router = useRouter();

  const [phone, setPhone] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleRequest = async () => {
    const trimmed = phone.trim();
    setError(null);
    if (trimmed.length < 6) {
      setError(t('auth.invalidPhone') ?? 'Enter your phone number');
      return;
    }
    setLoading(true);
    try {
      await authApi.requestPasswordReset({ phone: trimmed });
      router.replace({ pathname: '/(auth)/reset-password', params: { phone: trimmed } });
    } catch (e) {
      const err = e as {
        response?: { status: number; data?: { code?: string } };
        code?: string | null;
        userMessage?: string;
        message?: string;
      };
      if (err.response?.status === 421) {
        router.replace('/(auth)/no-password');
        return;
      }
      setError(
        err.userMessage ?? (t('errors.generic') ?? 'Something went wrong. Please try again.')
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <Screen className="justify-center">
      <Stack.Screen options={{ headerShown: false }} />
      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
        className="gap-6"
      >
        <View>
          <Text className="text-[11px] font-medium uppercase tracking-[0.2em]" style={{ color: colors.textInactive }}>
            {t('common.appName') ?? 'BingoPlus'}
          </Text>
          <Title className="mt-1">{t('auth.forgotPasswordTitle') ?? 'Reset your password'}</Title>
          <Subtitle>
            {t('auth.forgotPasswordHint') ??
              'Enter your phone number. If an account exists, a one-time reset code is sent to your Telegram.'}
          </Subtitle>
        </View>

        <Card className="gap-4">
          <View>
            <FieldLabel>{t('mobile.phone') ?? 'Phone number'}</FieldLabel>
            <AppTextInput
              value={phone}
              onChangeText={setPhone}
              placeholder="09•• ••• •••"
              keyboardType="phone-pad"
              autoCapitalize="none"
            />
          </View>

          <Button onPress={handleRequest} disabled={loading}>
            {loading ? t('common.loading') ?? 'Loading…' : t('auth.sendResetCode') ?? 'Send reset code'}
          </Button>

          {error ? (
            <View className="rounded-xl border px-4 py-3" style={{ borderColor: colors.danger + '40', backgroundColor: colors.danger + '15' }}>
              <Text className="text-red-500 text-sm">{error}</Text>
            </View>
          ) : null}
        </Card>
      </KeyboardAvoidingView>
    </Screen>
  );
}