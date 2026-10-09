import { Stack, useLocalSearchParams, useRouter } from 'expo-router';
import { useState } from 'react';
import { KeyboardAvoidingView, Platform, Pressable, Text, View } from 'react-native';
import { AppTextInput, Button, Card, FieldLabel, Screen, Subtitle, Title } from '@/components/ui';
import { useTranslate } from '@/hooks/useTranslate';
import { useTheme } from '@/lib/theme';
import { authApi } from '@/api/auth.api';
import { useAuthStore } from '@/store/auth.store';
import { Role } from '@/types';

/**
 * Step 2 of forgot-password: the 6-digit one-time code from Telegram plus the
 * new password. Success returns a fresh JWT exactly like login, so the user is
 * signed straight in — the code is consumed once and burned.
 */
export default function ResetPasswordScreen() {
  const { colors } = useTheme();
  const t = useTranslate();
  const router = useRouter();
  const setSession = useAuthStore((s) => s.setSession);
  const { phone } = useLocalSearchParams<{ phone?: string }>();

  const [code, setCode] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [resending, setResending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [sentAgain, setSentAgain] = useState(false);

  /** Localized text for a reset error code, falling back to the server's message. */
  const friendlyError = (e: unknown) => {
    const err = e as { code?: string | null; userMessage?: string };
    const code = err.code ?? null;
    const localized = code ? t(`errors.${code}`) : null;
    if (localized && localized !== `errors.${code}`) {
      return localized;
    }
    return err.userMessage ?? (t('errors.generic') ?? 'Something went wrong. Please try again.');
  };

  const handleReset = async () => {
    const currentPhone = phone ?? '';
    setError(null);
    if (code.trim().length !== 6) {
      setError(t('auth.invalidCode') ?? 'Enter the 6-digit code');
      return;
    }
    if (newPassword.length < 4) {
      setError(t('auth.weakPassword') ?? 'Password must be at least 4 characters');
      return;
    }
    if (newPassword !== confirmPassword) {
      setError(t('auth.passwordMismatch') ?? 'Passwords do not match');
      return;
    }
    setLoading(true);
    try {
      const res = await authApi.confirmPasswordReset({
        phone: currentPhone,
        code: code.trim(),
        newPassword,
      });
      const { jwt, user } = res.data;
      setSession(jwt, user);
      if (user.role === Role.SUPER_ADMIN) {
        router.replace('/(super-admin)');
        return;
      }
      router.replace(user.role === Role.ADMIN ? '/(admin)' : '/(player)');
    } catch (e) {
      setError(friendlyError(e));
    } finally {
      setLoading(false);
    }
  };

  const handleResend = async () => {
    const currentPhone = phone ?? '';
    setError(null);
    setSentAgain(false);
    setResending(true);
    try {
      await authApi.requestPasswordReset({ phone: currentPhone });
      setSentAgain(true);
    } catch (e) {
      const err = e as { response?: { status: number } };
      if (err.response?.status === 421) {
        router.replace('/(auth)/no-password');
        return;
      }
      setError(friendlyError(e));
    } finally {
      setResending(false);
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
          <Title className="mt-1">{t('auth.resetPasswordTitle') ?? 'Enter the reset code'}</Title>
          <Subtitle>
            {t('auth.resetPasswordHint') ??
              'Enter the 6-digit code from your Telegram and choose a new password.'}
          </Subtitle>
        </View>

        <Card className="gap-4">
          <View>
            <FieldLabel>{t('auth.resetCode') ?? 'Reset code'}</FieldLabel>
            <AppTextInput
              value={code}
              onChangeText={setCode}
              placeholder="••••••"
              keyboardType="number-pad"
              maxLength={6}
            />
          </View>
          <View>
            <FieldLabel>{t('auth.newPassword') ?? 'New password'}</FieldLabel>
            <AppTextInput
              value={newPassword}
              onChangeText={setNewPassword}
              placeholder="••••••"
              secureTextEntry
            />
          </View>
          <View>
            <FieldLabel>{t('auth.confirmNewPassword') ?? 'Confirm new password'}</FieldLabel>
            <AppTextInput
              value={confirmPassword}
              onChangeText={setConfirmPassword}
              placeholder="••••••"
              secureTextEntry
            />
          </View>

          <Button onPress={handleReset} disabled={loading}>
            {loading ? t('common.loading') ?? 'Loading…' : t('auth.resetPassword') ?? 'Reset password'}
          </Button>

          {sentAgain ? (
            <Text className="text-sm" style={{ color: '#059669' }}>
              {t('auth.resetCodeSentHint') ?? 'A new code was sent to your Telegram.'}
            </Text>
          ) : null}

          <Pressable onPress={handleResend} disabled={resending} hitSlop={8}>
            <Text className="text-sm text-center" style={{ color: colors.primary }}>
              {resending
                ? t('auth.resending') ?? 'Resending…'
                : t('auth.resendCode') ?? 'Resend code'}
            </Text>
          </Pressable>

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