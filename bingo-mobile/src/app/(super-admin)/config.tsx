import { useQuery } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { ScrollView, Text, View } from 'react-native';
import { configApi } from '@/api';
import { AppTextInput, Button, Card, EmptyState, FieldLabel, Screen, SectionHeader } from '@/components/ui';
import { useTranslate } from '@/hooks/useTranslate';
import { useTheme } from '@/lib/theme';

const LABEL_MAP: Record<string, string> = {
  cardSize: 'labelCardSize',
  numberRange: 'labelNumberRange',
  autoCallInterval: 'labelAutoCallInterval',
  entryFee: 'labelEntryFee',
  minWithdrawal: 'labelMinWithdrawal',
  ownerFeePercent: 'labelOwnerFeePercent',
  minPrizePercent: 'labelMinPrizePercent',
  maxPrizePercent: 'labelMaxPrizePercent',
};

const HINT_MAP: Record<string, string> = {
  cardSize: 'hintCardSize',
  numberRange: 'hintNumberRange',
  autoCallInterval: 'hintAutoCallInterval',
  entryFee: 'hintEntryFee',
  minWithdrawal: 'hintMinWithdrawal',
  ownerFeePercent: 'hintOwnerFeePercent',
  minPrizePercent: 'hintMinPrizePercent',
  maxPrizePercent: 'hintMaxPrizePercent',
};

export default function SuperAdminConfigScreen() {
  const t = useTranslate();
  const { colors } = useTheme();
  const [draft, setDraft] = useState<Record<string, string> | null>(null);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<{ text: string; isError: boolean } | null>(null);

  const { data, isLoading, isError } = useQuery({
    queryKey: ['super/config'],
    queryFn: () => configApi.get(),
  });

  const serverConfig = useMemo(() => {
    const flat: Record<string, string> = {};
    for (const [k, v] of Object.entries(data?.data ?? {})) {
      flat[k] = v == null ? '' : String(v);
    }
    return flat;
  }, [data]);

  const config = draft ?? serverConfig;
  const hasConfig = Object.keys(config).length > 0;

  const update = (key: string, value: string) =>
    setDraft((prev) => ({ ...(prev ?? serverConfig), [key]: value }));

  const save = async () => {
    setSaving(true);
    setMessage(null);
    try {
      const payload: Record<string, unknown> = { ...config };
      await configApi.update({ config: payload });
      setMessage({ text: t('super.configUpdated') ?? 'Config updated successfully.', isError: false });
    } catch {
      setMessage({ text: t('super.configUpdateFailed') ?? 'Failed to update config.', isError: true });
    } finally {
      setSaving(false);
    }
  };

  const labelFor = (key: string) => {
    const mapped = LABEL_MAP[key];
    if (mapped) return t(`super.${mapped}`) ?? mapped;
    return key.replace(/([A-Z])/g, ' $1');
  };
  const hintFor = (key: string) => {
    const mapped = HINT_MAP[key];
    return mapped ? (t(`super.${mapped}`) ?? mapped) : undefined;
  };

  return (
    <Screen>
      <SectionHeader
        eyebrow={t('super.cfgEyebrow') ?? ''}
        title={t('super.cfgTitle') ?? 'Platform settings'}
        description={t('super.cfgDesc') ?? ''}
      />

      <ScrollView contentContainerClassName="gap-4 pb-8">
        {isLoading ? (
          <Card>
            <Text style={{ color: colors.textSecondary }}>{t('common.loading') ?? 'Loading...'}</Text>
          </Card>
        ) : isError ? (
          <EmptyState title={t('super.loadConfigFailedDesc') ?? 'Failed to load config. Check your connection and try again.'} />
        ) : !hasConfig ? (
          <EmptyState title={t('super.noConfigValues') ?? 'No configuration values returned by the server.'} />
        ) : (
          <>
            <Card className="gap-4">
              {Object.keys(config).map((key) => (
                <View key={key}>
                  <FieldLabel>{labelFor(key)}</FieldLabel>
                  <AppTextInput
                    value={config[key]}
                    onChangeText={(v) => update(key, v)}
                  />
                  {hintFor(key) ? (
                    <Text className="text-xs mt-1" style={{ color: colors.textSecondary }}>{hintFor(key)}</Text>
                  ) : null}
                </View>
              ))}
            </Card>
            {message ? (
              <Text className="text-sm" style={{ color: message.isError ? colors.danger : colors.accent }}>
                {message.text}
              </Text>
            ) : null}
            <Button disabled={saving} onPress={() => void save()}>
              {saving ? (t('super.done') ?? '…') : (t('super.saveConfig') ?? 'Save Config')}
            </Button>
          </>
        )}
      </ScrollView>
    </Screen>
  );
}