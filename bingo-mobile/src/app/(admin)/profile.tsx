import { useQuery } from '@tanstack/react-query';
import * as Clipboard from 'expo-clipboard';
import { useState } from 'react';
import { Pressable, ScrollView, Text, View } from 'react-native';
import { authApi, inviteApi } from '@/api';
import { AppTextInput, Button, Card, FieldLabel, Screen, ScreenHeader } from '@/components/ui';
import { useTranslate } from '@/hooks/useTranslate';
import { getClientLocale, setClientLocale } from '@/lib/clientTranslations';
import { useAuthStore } from '@/store/auth.store';

const LOCALES = ['en', 'am', 'ti'] as const;

export default function AdminProfileScreen() {
  const t = useTranslate();
  const user = useAuthStore((s) => s.user);
  const setUser = useAuthStore((s) => s.setUser);

  const [businessName, setBusinessName] = useState(user?.businessName ?? '');
  const [depositAccountInfo, setDepositAccountInfo] = useState(user?.depositAccountInfo ?? '');
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [showInvite, setShowInvite] = useState(false);
  const [copied, setCopied] = useState(false);

  const { data: inviteLink } = useQuery({
    queryKey: ['invite/link', user?.id],
    queryFn: () => inviteApi.getMyLink(),
  });
  const { data: inviteStats } = useQuery({
    queryKey: ['invite/stats', user?.id],
    queryFn: () => inviteApi.getMyStats(),
  });

  const link = inviteLink?.data;
  const stats = inviteStats?.data;

  const handleCopy = async () => {
    if (!link) return;
    await Clipboard.setStringAsync(link);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const save = async () => {
    setSaving(true);
    setSaved(false);
    setError(null);
    try {
      const res = await authApi.updateProfile({
        businessName: businessName.trim() || undefined,
        depositAccountInfo: depositAccountInfo.trim() || undefined,
      });
      setUser(res.data);
      setSaved(true);
    } catch (e) {
      setError((e as { userMessage?: string }).userMessage ?? (t('admin.failedToSave') ?? 'Failed to save'));
    } finally {
      setSaving(false);
    }
  };

  const switchLanguage = async (locale: (typeof LOCALES)[number]) => {
    setClientLocale(locale as any);
    if (user) setUser({ ...user, preferredLanguage: locale as any });
    try {
      const res = await authApi.updateProfile({ preferredLanguage: locale as any });
      setUser(res.data);
    } catch {
      // language switch still applies locally
    }
  };

  return (
    <Screen>
      <ScreenHeader title={t('admin.profileTitle') ?? 'Profile'} />

      <ScrollView contentContainerClassName="gap-4 pb-8">
        <Card className="gap-3">
          <FieldLabel>{t('admin.businessName') ?? 'Business name'}</FieldLabel>
          <AppTextInput
            value={businessName}
            onChangeText={setBusinessName}
            placeholder={t('admin.businessNamePlaceholder') ?? 'e.g. Bingo Agent Addis'}
          />

          <FieldLabel>{t('admin.depositAccountInfo') ?? 'Deposit account'} · {t('admin.depositAccountDesc') ?? ''}</FieldLabel>
          <AppTextInput
            value={depositAccountInfo}
            onChangeText={setDepositAccountInfo}
            placeholder="e.g. TeleBirr: 0911234567&#10;CBE: 1000123456789"
            multiline
            numberOfLines={4}
            textAlignVertical="top"
            style={{ minHeight: 100 }}
          />
          <Text className="text-bp-textSecondary text-xs">
            {t('admin.depositAccountInfoHint') ?? 'TeleBirr number, bank account, or any payment details players should send to'}
          </Text>

          {error ? <Text className="text-bp-dangerInk text-sm">✕ {error}</Text> : null}
          {saved ? <Text className="text-bp-accentInk text-sm">{t('admin.savedSuccessfully') ?? 'Saved successfully.'}</Text> : null}

          <Button disabled={saving} onPress={() => void save()}>
            {saving ? (t('admin.saving') ?? 'Saving…') : (t('admin.save') ?? 'Save')}
          </Button>
        </Card>

        <Card>
          <Pressable
            onPress={() => setShowInvite((v) => !v)}
            className="flex-row items-center justify-between active:opacity-80"
          >
            <Text className="text-bp-textPrimary font-semibold">
              {t('admin.invitePlayerTitle') ?? 'Invite a Player'}
            </Text>
            <Text className="text-bp-primary">{showInvite ? (t('admin.hideInvite') ?? '−') : '›'}</Text>
          </Pressable>

          {showInvite && (
            <View className="gap-2 mt-3">
              <Text className="text-bp-textSecondary text-xs">
                {t('admin.inviteShareDesc') ?? 'Share your link — players who register through it will join your room.'}
              </Text>
              {stats && (
                <View className="flex-row gap-2 mt-1">
                  <View className="flex-1 rounded-xl bg-bp-surfaceAlt px-3 py-2 items-center">
                    <Text className="text-bp-textPrimary text-lg font-bold">{stats.totalRegistrations ?? 0}</Text>
                    <Text className="text-bp-textSecondary text-[10px]">{t('admin.playersJoined') ?? 'Players joined'}</Text>
                  </View>
                  <View className="flex-1 rounded-xl bg-bp-surfaceAlt px-3 py-2 items-center">
                    <Text className="text-bp-textPrimary text-lg font-bold">{stats.activeCodes ?? 0}</Text>
                    <Text className="text-bp-textSecondary text-[10px]">{t('admin.activeLinks') ?? 'Active links'}</Text>
                  </View>
                </View>
              )}
              {link ? (
                <View className="rounded-xl bg-bp-surfaceAlt border border-bp-borderInactive px-3 py-2">
                  <Text className="text-bp-textSecondary text-xs" numberOfLines={3}>
                    {link}
                  </Text>
                </View>
              ) : (
                <Text className="text-bp-textSecondary text-xs">
                  {t('admin.noPlayersYet') ?? 'No players yet. Share your invite link!'}
                </Text>
              )}
              <View className="flex-row gap-2">
                <Button variant="primary" disabled={!link} onPress={() => void handleCopy()} style={{ flex: 1 }}>
                  {copied ? (t('admin.copied') ?? '✓ Copied!') : (t('admin.copyLink') ?? 'Copy Link')}
                </Button>
                <Button variant="outline" disabled={!link} onPress={() => void handleCopy()} style={{ flex: 1 }}>
                  {t('admin.share') ?? 'Share'}
                </Button>
              </View>
            </View>
          )}
        </Card>

        <Card>
          <Text className="text-bp-textPrimary font-semibold mb-3">{t('player.language') ?? 'Language'}</Text>
          <View className="flex-row gap-2">
            {LOCALES.map((locale) => {
              const active = locale === getClientLocale();
              return (
                <Button
                  key={locale}
                  variant={active ? 'primary' : 'outline'}
                  onPress={() => void switchLanguage(locale)}
                  style={{ flex: 1, paddingVertical: 10 }}
                >
                  <Text className={active ? 'text-white' : 'text-bp-textSecondary'}>
                    {locale === 'en'
                      ? (t('player.english') ?? 'English')
                      : locale === 'am'
                        ? (t('player.amharic') ?? 'አማርኛ')
                        : (t('player.tigrinya') ?? 'ትግርኛ')}
                  </Text>
                </Button>
              );
            })}
          </View>
        </Card>
      </ScrollView>
    </Screen>
  );
}