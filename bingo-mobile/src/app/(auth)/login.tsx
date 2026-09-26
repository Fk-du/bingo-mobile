import { Stack, useRouter } from 'expo-router';
import { useState } from 'react';
import { KeyboardAvoidingView, Platform, Text, View } from 'react-native';
import { AppTextInput, Button, Card, FieldLabel, Screen, Subtitle, Title } from '@/components/ui';
import { useTranslate } from '@/hooks/useTranslate';
import { authApi } from '@/api/auth.api';
import { useAuthStore } from '@/store/auth.store';
import { Role } from '@/types';

export default function LoginScreen() {
  const t = useTranslate();
  const router = useRouter();
  const setSession = useAuthStore((s) => s.setSession);

  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleLogin = async () => {
    const trimmed = phone.trim();
    setError(null);
    if (trimmed.length < 6 || password.length < 4) {
      setError(t('auth.invalidInput') ?? 'Check your phone number and password');
      return;
    }
    setLoading(true);
    try {
      const res = await authApi.phoneLogin({ phone: trimmed, password });
      const { jwt, user } = res.data;

      if (user.role === Role.SUPER_ADMIN) {
        setSession(jwt, user);
        router.replace('/(super-admin)');
        return;
      }
      setSession(jwt, user);
      router.replace(user.role === Role.ADMIN ? '/(admin)' : '/(player)');
    } catch (e) {
      const err = e as {
        response?: { status: number; data?: { code?: string } };
        code?: string | null;
        userMessage?: string;
        message?: string;
      };
      if (err.response?.status === 421) {
        // NO_PASSWORD → show the registration-bot link screen
        router.replace('/(auth)/no-password');
        return;
      }
      const status = err.response?.status ? `${err.response.status} ` : '';
      const code = err.code ?? err.response?.data?.code;
      setError(
        err.userMessage ??
          `${status}${code ? `${code} ` : ''}${err.message ?? (t('auth.authFailed') ?? 'Login failed')}`
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
          <Title>{t('common.appName') ?? 'BingoPlus'}</Title>
          <Subtitle>{t('auth.mobileLogin') ?? 'Log in with your phone number and password'}</Subtitle>
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
          <View>
            <FieldLabel>{t('mobile.password') ?? 'Password'}</FieldLabel>
            <AppTextInput
              value={password}
              onChangeText={setPassword}
              placeholder="••••••"
              secureTextEntry
            />
          </View>

          <Button onPress={handleLogin} disabled={loading}>
            {loading ? t('common.loading') ?? 'Loading…' : t('mobile.login') ?? 'Log in'}
          </Button>

          {error ? (
            <View className="rounded-xl border border-bp-danger40 bg-bp-danger15 px-4 py-3">
              <Text className="text-red-500 text-sm">{error}</Text>
            </View>
          ) : null}

          <Subtitle className="text-center text-xs leading-5">
            {t('auth.mobileHint') ?? 'First time? Open the BingoPlus bot in Telegram, tap your invite link, and create a password to play here.'}
          </Subtitle>
        </Card>
      </KeyboardAvoidingView>
    </Screen>
  );
}