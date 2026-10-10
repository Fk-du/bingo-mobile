import { useQuery } from '@tanstack/react-query';
import * as Clipboard from 'expo-clipboard';
import { useState } from 'react';
import { Pressable, ScrollView, Text, View } from 'react-native';
import { authApi, inviteApi } from '@/api';
import { AppTextInput, Button, Card, FieldLabel, Screen, ScreenHeader } from '@/components/ui';
import { useTranslate } from '@/hooks/useTranslate';
import { getClientLocale, setClientLocale } from '@/lib/clientTranslations';
import { useTheme } from '@/lib/theme';
import { useAuthStore } from '@/store/auth.store';
import type { DepositAccount } from '@/types';

const LOCALES = ['en', 'am', 'ti'] as const;

export default function AdminProfileScreen() {
  const t = useTranslate();
  const { colors } = useTheme();
  const user = useAuthStore((s) => s.user);
  const setUser = useAuthStore((s) => s.setUser);

  const [businessName, setBusinessName] = useState(user?.businessName ?? '');
  const [accounts, setAccounts] = useState<DepositAccount[]>(
    () => (user?.depositAccounts ?? []).map((a) => ({
      bank: a.bank ?? '',
      accountNumber: a.accountNumber ?? '',
      ownerName: a.ownerName ?? '',
    }))
  );
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [editingAccounts, setEditingAccounts] = useState(false);
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

  const updateAccount = (index: number, field: keyof DepositAccount, value: string) => {
    setAccounts((current) => current.map((a, i) => (i === index ? { ...a, [field]: value } : a)));
  };

  const addAccount = () => {
    setAccounts((current) => [...current, { bank: '', accountNumber: '', ownerName: '' }]);
  };

  const removeAccount = (index: number) => {
    setAccounts((current) => current.filter((_, i) => i !== index));
  };

  const startEditAccounts = () => {
    setAccounts(
      (user?.depositAccounts ?? []).map((a) => ({
        bank: a.bank ?? '',
        accountNumber: a.accountNumber ?? '',
        ownerName: a.ownerName ?? '',
      }))
    );
    setEditingAccounts(true);
  };

  const accountLine = (a: DepositAccount) =>
    [a.bank, a.accountNumber, a.ownerName].filter((v) => v && v.trim()).join('   ');

  const save = async () => {
    setSaving(true);
    setSaved(false);
    setError(null);
    try {
      const payload = editingAccounts
        ? accounts
            .map((a) => ({
              bank: a.bank?.trim() || null,
              accountNumber: a.accountNumber.trim(),
              ownerName: a.ownerName?.trim() || null,
            }))
            .filter((a) => a.accountNumber.length > 0)
        : undefined;
      const res = await authApi.updateProfile({
        businessName: businessName.trim() || undefined,
        depositAccounts: payload,
      });
      setUser(res.data);
      setAccounts((res.data.depositAccounts ?? []).map((a) => ({
        bank: a.bank ?? '',
        accountNumber: a.accountNumber ?? '',
        ownerName: a.ownerName ?? '',
      })));
      setEditingAccounts(false);
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

          <FieldLabel>{t('admin.depositAccountTitle') ?? 'Deposit account'}</FieldLabel>
          <Text className="text-xs" style={{ color: colors.textSecondary }}>
            {t('admin.depositAccountDesc') ?? 'Set the account details players see when depositing.'}
          </Text>

          {editingAccounts ? (
            <>
              {accounts.map((account, index) => (
                <View
                  key={index}
                  className="gap-2 rounded-xl border p-3"
                  style={{ backgroundColor: colors.surfaceAlt, borderColor: colors.borderInactive }}
                >
                  <View className="flex-row items-center justify-between">
                    <Text className="text-xs font-bold" style={{ color: colors.textSecondary }}>
                      #{index + 1}
                    </Text>
                    <Pressable onPress={() => removeAccount(index)} className="active:opacity-80">
                      <Text className="text-xs" style={{ color: colors.danger }}>
                        {t('admin.removeDepositAccount') ?? 'Remove'}
                      </Text>
                    </Pressable>
                  </View>

                  <FieldLabel>{t('admin.depositAccountBank') ?? 'Bank / method'}</FieldLabel>
                  <AppTextInput
                    value={account.bank ?? ''}
                    onChangeText={(v) => updateAccount(index, 'bank', v)}
                    placeholder={t('admin.depositAccountBankPlaceholder') ?? 'e.g. TeleBirr, CBE'}
                  />

                  <FieldLabel>{t('admin.depositAccountNumber') ?? 'Account number'}</FieldLabel>
                  <AppTextInput
                    value={account.accountNumber}
                    onChangeText={(v) => updateAccount(index, 'accountNumber', v)}
                    placeholder={t('admin.depositAccountNumberPlaceholder') ?? '0911234567'}
                    keyboardType="number-pad"
                  />

                  <FieldLabel>{t('admin.depositAccountOwner') ?? 'Account name'}</FieldLabel>
                  <AppTextInput
                    value={account.ownerName ?? ''}
                    onChangeText={(v) => updateAccount(index, 'ownerName', v)}
                    placeholder={t('admin.depositAccountOwnerPlaceholder') ?? 'Name shown on the account'}
                  />
                </View>
              ))}

              <Button variant="outline" onPress={addAccount}>
                {t('admin.addDepositAccount') ?? '+ Add another account'}
              </Button>
              <Text className="text-xs" style={{ color: colors.textSecondary }}>
                {t('admin.depositAccountInfoHint') ?? 'TeleBirr number, bank account, or any payment details players should send to'}
              </Text>
            </>
          ) : (
            <>
              {(user?.depositAccounts?.length ?? 0) === 0 ? (
                <Text className="text-xs" style={{ color: colors.textSecondary }}>
                  {t('admin.noDepositAccountsYet') ?? 'No accounts yet. Add the details players should send money to.'}
                </Text>
              ) : (
                (user?.depositAccounts ?? []).map((account, index) => (
                  <View
                    key={index}
                    className="rounded-xl border px-3 py-2.5"
                    style={{ backgroundColor: colors.surfaceAlt, borderColor: colors.borderInactive }}
                  >
                    <Text className="text-sm" style={{ color: colors.textSecondary }}>
                      {index + 1}. {accountLine(account)}
                    </Text>
                  </View>
                ))
              )}
              <Button variant="outline" onPress={startEditAccounts}>
                {t('admin.editDepositAccounts') ?? 'Edit accounts'}
              </Button>
            </>
          )}

          {error ? <Text className="text-sm" style={{ color: colors.danger }}>✕ {error}</Text> : null}
          {saved ? <Text className="text-sm" style={{ color: colors.accent }}>{t('admin.savedSuccessfully') ?? 'Saved successfully.'}</Text> : null}

          {editingAccounts ? (
            <View className="flex-row gap-3">
              <Button variant="outline" className="flex-1" disabled={saving} onPress={() => setEditingAccounts(false)}>
                {t('common.cancel') ?? 'Cancel'}
              </Button>
              <Button className="flex-1" disabled={saving} onPress={() => void save()}>
                {saving ? (t('admin.saving') ?? 'Saving…') : (t('admin.save') ?? 'Save')}
              </Button>
            </View>
          ) : (
            <Button disabled={saving} onPress={() => void save()}>
              {saving ? (t('admin.saving') ?? 'Saving…') : (t('admin.save') ?? 'Save')}
            </Button>
          )}
        </Card>

        <Card>
          <Pressable
            onPress={() => setShowInvite((v) => !v)}
            className="flex-row items-center justify-between active:opacity-80"
          >
             <Text className="font-semibold" style={{ color: colors.textPrimary }}>
               {t('admin.invitePlayerTitle') ?? 'Invite a Player'}
             </Text>
             <Text style={{ color: colors.primary }}>{showInvite ? (t('admin.hideInvite') ?? '−') : '›'}</Text>
           </Pressable>

           {showInvite && (
             <View className="gap-2 mt-3">
               <Text className="text-xs" style={{ color: colors.textSecondary }}>
                 {t('admin.inviteShareDesc') ?? 'Share your link — players who register through it will join your room.'}
               </Text>
               {stats && (
                 <View className="flex-row gap-2 mt-1">
                   <View className="flex-1 rounded-xl px-3 py-2 items-center" style={{ backgroundColor: colors.surfaceAlt }}>
                     <Text className="text-lg font-bold" style={{ color: colors.textPrimary }}>{stats.totalRegistrations ?? 0}</Text>
                     <Text className="text-[10px]" style={{ color: colors.textSecondary }}>{t('admin.playersJoined') ?? 'Players joined'}</Text>
                   </View>
                   <View className="flex-1 rounded-xl px-3 py-2 items-center" style={{ backgroundColor: colors.surfaceAlt }}>
                     <Text className="text-lg font-bold" style={{ color: colors.textPrimary }}>{stats.activeCodes ?? 0}</Text>
                     <Text className="text-[10px]" style={{ color: colors.textSecondary }}>{t('admin.activeLinks') ?? 'Active links'}</Text>
                   </View>
                 </View>
               )}
               {link ? (
                 <View className="rounded-xl border px-3 py-2" style={{ backgroundColor: colors.surfaceAlt, borderColor: colors.borderInactive }}>
                   <Text className="text-xs" style={{ color: colors.textSecondary }} numberOfLines={3}>
                     {link}
                   </Text>
                 </View>
               ) : (
                 <Text className="text-xs" style={{ color: colors.textSecondary }}>
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
          <Text className="font-semibold mb-3" style={{ color: colors.textPrimary }}>{t('player.language') ?? 'Language'}</Text>
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
                  <Text style={{ color: active ? '#FFFFFF' : colors.textSecondary }}>
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