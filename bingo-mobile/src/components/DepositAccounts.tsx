import * as Clipboard from 'expo-clipboard';
import { useState } from 'react';
import { Pressable, Text, View } from 'react-native';
import { useTranslate } from '@/hooks/useTranslate';
import { useTheme } from '@/lib/theme';
import type { DepositAccount } from '@/types';

/** The accounts a player should send a deposit to, one copyable card per account. */
export function DepositAccounts({ accounts }: { accounts: DepositAccount[] | null }) {
  const t = useTranslate();
  const { colors } = useTheme();
  const [copiedIndex, setCopiedIndex] = useState<number | null>(null);

  if (!accounts || accounts.length === 0) return null;

  const copy = async (account: DepositAccount, index: number) => {
    await Clipboard.setStringAsync(account.accountNumber);
    setCopiedIndex(index);
    setTimeout(() => setCopiedIndex((current) => (current === index ? null : current)), 2000);
  };

  return (
    <View className="gap-2">
      <Text className="text-xs font-semibold" style={{ color: colors.secondary }}>
        {t('player.sendDepositTo') ?? 'Send deposit to'}
      </Text>
      {accounts.map((account, index) => (
        <View
          key={`${account.accountNumber}-${index}`}
          className="rounded-xl border p-3"
          style={{ backgroundColor: colors.surfaceAlt, borderColor: colors.borderInactive }}
        >
          <View className="flex-row items-center justify-between gap-2">
            <View className="flex-1 gap-0.5">
              {account.bank ? (
                <Text className="text-sm font-semibold" style={{ color: colors.textPrimary }}>
                  {account.bank}
                </Text>
              ) : null}
              {account.ownerName ? (
                <Text className="text-xs" style={{ color: colors.textSecondary }}>
                  {account.ownerName}
                </Text>
              ) : null}
              <Text className="text-base font-bold" style={{ color: colors.textPrimary }}>
                {account.accountNumber}
              </Text>
            </View>
            <Pressable onPress={() => void copy(account, index)} className="active:opacity-80">
              <View className="rounded-lg px-3 py-2" style={{ backgroundColor: colors.primary }}>
                <Text className="text-xs font-semibold text-white">
                  {copiedIndex === index
                    ? (t('player.copied') ?? 'Copied!')
                    : (t('player.copyAccount') ?? 'Copy')}
                </Text>
              </View>
            </Pressable>
          </View>
        </View>
      ))}
      <Text className="text-xs" style={{ color: colors.textSecondary }}>
        {t('player.afterSending') ?? 'After sending, upload your payment screenshot below.'}
      </Text>
    </View>
  );
}