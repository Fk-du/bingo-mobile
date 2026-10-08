import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Pressable, ScrollView, Text, View } from 'react-native';
import { gamesApi } from '@/api';
import { AppTextInput, Button, Card, FieldLabel, Modal, Screen, ScreenHeader } from '@/components/ui';
import { useTranslate } from '@/hooks/useTranslate';
import { useTheme } from '@/lib/theme';
import { AutomationConfig, CreateGameRequest } from '@/types';

const WINNING_PATTERNS = [
  'FULL_HOUSE',
  'HALF_HOUSE',
  'EIGHT_LINES',
  'SEVEN_LINES',
  'SIX_LINES',
  'FIVE_LINES',
  'TWO_VERT_TWO_HORIZ_ONE_DIAG',
  'TWO_VERT_TWO_HORIZ',
  'TWO_VERT_THREE_HORIZ',
  'FIVE_LINES_NO_FREE',
  'FOUR_LINES_NO_FREE',
  'TWO_TOUCH_TWO_NO_TOUCH',
  'FOUR_SQUARES',
  'TWO_LINES_TWO_SQUARES',
  'TWO_LINES_TWO_SEP_SQUARES',
  'THREE_SQUARES_FOUR_DOTS',
  'LARGE_T_TWO_LINES',
  'FOUR_LINES',
  'THREE_LINES_ONE_DIAG',
  'FOUR_LINES_TOUCH_FREE',
  'LARGE_T_THREE_LINES',
  'TWO_HORIZ_TWO_VERT_TWO_DIAG',
  'THREE_LINES_NO_FREE_DISJOINT',
  'FOUR_LINES_NO_FREE_DISJOINT',
  'THREE_SMALL_T',
  'LARGE_CROSS_TWO_SQUARES',
  'THREE_SMALL_CROSSES',
];

const DEFAULTS = {
  entryFee: '10',
  callInterval: '5',
  rakePercent: '10',
  registrationWindow: '180',
  cooldown: '15',
};

export default function AdminCreateGameScreen() {
  const t = useTranslate();

  const { data: automationData, refetch: refetchAutomation } = useQuery({
    queryKey: ['admin/automation'],
    queryFn: () => gamesApi.getAutomation(),
  });
  const { data: activeGamesData } = useQuery({
    queryKey: ['admin/active-games'],
    queryFn: () => gamesApi.getActive(),
  });
  const automation = automationData?.data;
  const hasActiveGame = (activeGamesData?.data ?? []).length > 0;
  const isAuto = automation?.enabled ?? false;

  return (
    <Screen>
      <ScreenHeader title={t('admin.gamesManageTitle') ?? 'Game management'} />
      <ScrollView contentContainerClassName="gap-4 pb-10">
        <AutomationCard
          automation={automation}
          onSaved={() => refetchAutomation()}
          t={t}
          disabled={false}
        />
        {isAuto || hasActiveGame ? null : <CreateGameForm t={t} />}
      </ScrollView>
    </Screen>
  );
}

function AutomationCard({
  automation,
  onSaved,
  t,
  disabled = false,
}: {
  automation: AutomationConfig | undefined;
  onSaved: () => void;
  t: ReturnType<typeof useTranslate>;
  disabled?: boolean;
}) {
  const { colors } = useTheme();
  const [busy, setBusy] = useState<'auto' | 'manual' | 'save' | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [enabled, setEnabled] = useState(automation?.enabled ?? false);
  const [form, setForm] = useState({
    entryFee: String(automation?.entryFee ?? DEFAULTS.entryFee),
    callInterval: String(automation?.callInterval ?? DEFAULTS.callInterval),
    rakePercent: String(automation?.rakePercent ?? DEFAULTS.rakePercent),
    autoMark: false,
    registrationWindowSeconds: String(automation?.registrationWindowSeconds ?? DEFAULTS.registrationWindow),
    cooldownSeconds: String(automation?.cooldownSeconds ?? DEFAULTS.cooldown),
  });

  const buildPayload = (autoEnabled: boolean) => ({
    entryFee: Number(form.entryFee) || 10,
    callInterval: Number(form.callInterval) || 5,
    rakePercent: Number(form.rakePercent) || 10,
    autoMark: form.autoMark,
    registrationWindowSeconds: Number(form.registrationWindowSeconds) || 180,
    cooldownSeconds: Number(form.cooldownSeconds) || 15,
    enabled: autoEnabled,
  });

  const save = async (autoEnabled: boolean, message: string, kind: 'auto' | 'manual' | 'save') => {
    setBusy(kind);
    setError(null);
    setNotice(null);
    try {
      await gamesApi.saveAutomation(buildPayload(autoEnabled));
      setEnabled(autoEnabled);
      setNotice(message);
      onSaved();
    } catch (e) {
      setError((e as { userMessage?: string }).userMessage ?? 'Failed to save');
    } finally {
      setBusy(null);
    }
  };

  return (
    <Card className="gap-3">
      <View>
        <Text className="text-[11px] uppercase tracking-wider" style={{ color: colors.textSecondary }}>{t('admin.gameMode') ?? 'Game mode'}</Text>
        <Text className="font-semibold" style={{ color: colors.textPrimary }}>
          {disabled ? (t('admin.oneGameAtATime') ?? 'Finish your current game to create another') : (t('admin.manualOrAutomatic') ?? 'Manual or automatic')}
        </Text>
      </View>

      <View className="flex-row gap-2">
        <Button
          variant={!enabled ? 'primary' : 'outline'}
          disabled={busy != null}
          onPress={() => void save(false, t('admin.automodeOff') ?? 'Automatic mode off', 'manual')}
          style={{ flex: 1 }}
        >
          {t('admin.manual') ?? 'Manual'}
        </Button>
        <Button
          variant={enabled ? 'primary' : 'outline'}
          disabled={busy != null}
          onPress={() => void save(true, t('admin.automodeEnable') ?? 'Automatic mode on', 'auto')}
          style={{ flex: 1 }}
        >
          {t('admin.automatic') ?? 'Automatic'}
        </Button>
      </View>

      {notice ? (
        <Text className="text-sm" style={{ color: colors.accent }}>✓ {notice}</Text>
      ) : null}
      {error ? <Text className="text-sm" style={{ color: colors.danger }}>✕ {error}</Text> : null}

      {enabled && (
        <View className="gap-3 mt-1">
          <FieldRow label={t('admin.entryFee') ?? 'Entry fee'} keyboard="numeric">
            <AppTextInput
              value={form.entryFee}
              onChangeText={(v) => setForm((f) => ({ ...f, entryFee: v }))}
              keyboardType="numeric"
              placeholder="10"
            />
          </FieldRow>
          <FieldRow label={t('admin.callInterval') ?? 'Call interval (s)'}>
            <AppTextInput
              value={form.callInterval}
              onChangeText={(v) => setForm((f) => ({ ...f, callInterval: v }))}
              keyboardType="numeric"
            />
          </FieldRow>
          <FieldRow label={t('admin.rakePercent') ?? 'Preferred rake %'}>
            <AppTextInput
              value={form.rakePercent}
              onChangeText={(v) => setForm((f) => ({ ...f, rakePercent: v }))}
              keyboardType="numeric"
            />
          </FieldRow>
          <Text className="text-xs" style={{ color: colors.textSecondary }}>
            {t('admin.rakePercentHint') ??
              'Used to suggest a prize for automated games. You still set the prize on each game before it starts.'}
          </Text>
          {/*
          Auto-mark is off by default for players; the admin toggle is removed per
          request ("completely remove this option from the admin side"). The form
          still tracks the value if needed, but the control itself is hidden.
          */}
          <FieldRow label={t('admin.registrationWindow') ?? 'Registration window (s)'}>
            <AppTextInput
              value={form.registrationWindowSeconds}
              onChangeText={(v) => setForm((f) => ({ ...f, registrationWindowSeconds: v }))}
              keyboardType="numeric"
            />
          </FieldRow>
          <FieldRow label={t('admin.gapBetweenGames') ?? 'Gap between games (s)'}>
            <AppTextInput
              value={form.cooldownSeconds}
              onChangeText={(v) => setForm((f) => ({ ...f, cooldownSeconds: v }))}
              keyboardType="numeric"
            />
          </FieldRow>
          <View className="flex-row gap-2">
            <Button variant="primary" disabled={busy != null} onPress={() => void save(true, t('admin.templateUpdated') ?? 'Template updated', 'save')} style={{ flex: 1 }}>
              {busy === 'save' ? t('admin.creating') ?? 'Saving…' : t('admin.saveTemplateKeepAuto') ?? 'Save & keep auto'}
            </Button>
            <Button variant="outline" disabled={busy != null} onPress={() => void save(false, t('admin.templateSaved') ?? 'Template saved', 'save')} style={{ flex: 1 }}>
              {t('admin.saveTemplateOnly') ?? 'Save template only'}
            </Button>
          </View>
        </View>
      )}
    </Card>
  );
}

function FieldRow({ label, children }: { label: string; children: React.ReactNode; keyboard?: 'numeric' }) {
  return (
    <View className="gap-1">
      <FieldLabel>{label}</FieldLabel>
      {children}
    </View>
  );
}

function CreateGameForm({ t }: { t: ReturnType<typeof useTranslate> }) {
  const { colors } = useTheme();
  const [entryFee, setEntryFee] = useState(DEFAULTS.entryFee);
  const [callInterval, setCallInterval] = useState(DEFAULTS.callInterval);
  const [winningPattern, setWinningPattern] = useState(WINNING_PATTERNS[0]);
  const [autoMark, setAutoMark] = useState(false);
  const [showPattern, setShowPattern] = useState(false);
  const [busy, setBusy] = useState(false);
  const [success, setSuccess] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async () => {
    setBusy(true);
    setSuccess(false);
    setError(null);
    const payload: CreateGameRequest = {
      entryFee: Number(entryFee),
      callInterval: Number(callInterval),
      winningPattern,
      autoMark,
    };
    try {
      await gamesApi.create(payload);
      setSuccess(true);
      setEntryFee(DEFAULTS.entryFee);
    } catch (e) {
      setError((e as { userMessage?: string }).userMessage ?? 'Could not create game');
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card className="gap-3">
      <View>
        <Text className="text-[11px] uppercase tracking-wider" style={{ color: colors.textSecondary }}>{t('admin.createGame') ?? 'Create game'}</Text>
        <Text className="font-semibold" style={{ color: colors.textPrimary }}>{t('admin.newTableSetup') ?? 'New table setup'}</Text>
      </View>

      {success ? (
        <Text className="text-sm" style={{ color: colors.accent }}>✓ {t('admin.gameCreatedNotice') ?? 'Game created'}</Text>
      ) : null}
      {error ? <Text className="text-sm" style={{ color: colors.danger }}>✕ {error}</Text> : null}

      <FieldRow label={t('admin.entryFee') ?? 'Entry fee'}>
        <AppTextInput value={entryFee} onChangeText={setEntryFee} keyboardType="numeric" placeholder="10" />
      </FieldRow>
      <FieldRow label={t('admin.callInterval') ?? 'Call interval (s)'}>
        <AppTextInput value={callInterval} onChangeText={setCallInterval} keyboardType="numeric" />
      </FieldRow>
      <FieldRow label={t('admin.winningPattern') ?? 'Winning pattern'}>
        <Pressable onPress={() => setShowPattern(true)} className="active:opacity-80">
          <View className="rounded-xl border px-4 py-3" style={{ backgroundColor: colors.surfaceAlt, borderColor: colors.borderInactive }}>
            <Text style={{ color: colors.textPrimary }}>{t(`patterns.${winningPattern}`) ?? winningPattern}</Text>
          </View>
        </Pressable>
      </FieldRow>
      <Pressable onPress={() => setAutoMark((v) => !v)} className="flex-row items-center gap-2">
        <View className={`w-9 h-5 rounded-full px-0.5 justify-center ${autoMark ? '' : ''}`} style={{ backgroundColor: autoMark ? colors.primary : colors.textInactive }}>
          <View className={`h-4 w-4 rounded-full bg-white ${autoMark ? 'self-end' : ''}`} />
        </View>
        <Text className="text-sm" style={{ color: colors.textSecondary }}>{t('admin.autoMark') ?? 'Auto-mark'}</Text>
      </Pressable>
      <Button disabled={busy} onPress={() => void submit()}>
        {busy ? (t('admin.creating') ?? 'Creating…') : (t('admin.createGame') ?? 'Create game')}
      </Button>

      {showPattern && (
        <Modal onClose={() => setShowPattern(false)}>
          <Card className="max-h-[70%]">
            <Text className="font-semibold mb-2" style={{ color: colors.textPrimary }}>{t('admin.winningPattern') ?? 'Winning pattern'}</Text>
            <ScrollView contentContainerStyle={{ gap: 8 }}>
              {WINNING_PATTERNS.map((p) => {
                const selected = winningPattern === p;
                return (
                  <Pressable
                     key={p}
                     onPress={() => {
                       setWinningPattern(p);
                       setShowPattern(false);
                     }}
                     className="rounded-xl border px-4 py-3"
                     style={{ borderColor: selected ? colors.primary : colors.borderInactive, backgroundColor: selected ? colors.primary + '15' : 'transparent' }}
                   >
                     <Text className={selected ? 'font-semibold' : ''} style={{ color: selected ? colors.primary : colors.textPrimary }}>
                       {t(`patterns.${p}`) ?? p}
                     </Text>
                   </Pressable>
                 );
               })}
            </ScrollView>
          </Card>
        </Modal>
      )}
    </Card>
  );
}