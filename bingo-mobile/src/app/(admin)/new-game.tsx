import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Pressable, ScrollView, Text, View } from 'react-native';
import { gamesApi } from '@/api';
import { AppTextInput, Button, Card, FieldLabel, Modal, Screen, ScreenHeader } from '@/components/ui';
import { useTranslate } from '@/hooks/useTranslate';
import { AutomationConfig, CreateGameRequest } from '@/types';

const WINNING_PATTERNS = [
  'SINGLE_LINE', 'DOUBLE_LINE', 'TRIPLE_LINE', 'FULL_HOUSE', 'FOUR_CORNERS',
  'X_SHAPE', 'T_SHAPE', 'L_SHAPE', 'POSTAGE_STAMP', 'PLUS', 'FRAME', 'DIAMOND',
  'Z_SHAPE', 'CUSTOM',
];

const DEFAULTS = {
  entryFee: '10',
  maxPlayers: '50',
  callInterval: '5',
  commissionPercent: '10',
  registrationWindow: '180',
  cooldown: '15',
};

export default function AdminCreateGameScreen() {
  const t = useTranslate();

  const { data: automationData, refetch: refetchAutomation } = useQuery({
    queryKey: ['admin/automation'],
    queryFn: () => gamesApi.getAutomation(),
  });
  const automation = automationData?.data;

  return (
    <Screen>
      <ScreenHeader title={t('admin.gamesManageTitle') ?? 'Game management'} />
      <ScrollView contentContainerClassName="gap-4 pb-10">
        <AutomationCard
          automation={automation}
          onSaved={() => refetchAutomation()}
          t={t}
        />
        {automation?.enabled ? null : <CreateGameForm t={t} />}
      </ScrollView>
    </Screen>
  );
}

function AutomationCard({
  automation,
  onSaved,
  t,
}: {
  automation: AutomationConfig | undefined;
  onSaved: () => void;
  t: ReturnType<typeof useTranslate>;
}) {
  const [busy, setBusy] = useState<'auto' | 'manual' | 'save' | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [enabled, setEnabled] = useState(automation?.enabled ?? false);
  const [form, setForm] = useState({
    entryFee: String(automation?.entryFee ?? DEFAULTS.entryFee),
    maxPlayers: String(automation?.maxPlayers ?? DEFAULTS.maxPlayers),
    callInterval: String(automation?.callInterval ?? DEFAULTS.callInterval),
    commissionPercent: String(automation?.commissionPercent ?? DEFAULTS.commissionPercent),
    autoMark: automation?.autoMark ?? true,
    registrationWindowSeconds: String(automation?.registrationWindowSeconds ?? DEFAULTS.registrationWindow),
    cooldownSeconds: String(automation?.cooldownSeconds ?? DEFAULTS.cooldown),
    startWhenFull: automation?.startWhenFull ?? false,
  });

  const buildPayload = (autoEnabled: boolean) => ({
    entryFee: Number(form.entryFee) || 10,
    maxPlayers: Number(form.maxPlayers) || 50,
    callInterval: Number(form.callInterval) || 5,
    commissionPercent: Number(form.commissionPercent) || 10,
    autoMark: form.autoMark,
    registrationWindowSeconds: Number(form.registrationWindowSeconds) || 180,
    cooldownSeconds: Number(form.cooldownSeconds) || 15,
    startWhenFull: form.startWhenFull,
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
        <Text className="text-bp-textSecondary text-[11px] uppercase tracking-wider">{t('admin.gameMode') ?? 'Game mode'}</Text>
        <Text className="text-bp-textPrimary font-semibold">{t('admin.manualOrAutomatic') ?? 'Manual or automatic'}</Text>
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
        <Text className="text-bp-accentInk text-sm">✓ {notice}</Text>
      ) : null}
      {error ? <Text className="text-bp-dangerInk text-sm">✕ {error}</Text> : null}

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
          <FieldRow label={t('admin.maxPlayers') ?? 'Max players'}>
            <AppTextInput
              value={form.maxPlayers}
              onChangeText={(v) => setForm((f) => ({ ...f, maxPlayers: v }))}
              keyboardType="numeric"
            />
          </FieldRow>
          <FieldRow label={t('admin.callInterval') ?? 'Call interval (s)'}>
            <AppTextInput
              value={form.callInterval}
              onChangeText={(v) => setForm((f) => ({ ...f, callInterval: v }))}
              keyboardType="numeric"
            />
          </FieldRow>
          <FieldRow label={t('admin.commission') ?? 'Commission %'}>
            <AppTextInput
              value={form.commissionPercent}
              onChangeText={(v) => setForm((f) => ({ ...f, commissionPercent: v }))}
              keyboardType="numeric"
            />
          </FieldRow>
          <Pressable
            onPress={() => setForm((f) => ({ ...f, autoMark: !f.autoMark }))}
            className="flex-row items-center gap-2"
          >
            <View className={`w-9 h-5 rounded-full px-0.5 justify-center ${form.autoMark ? 'bg-bp-primary' : 'bg-bp-textInactive'}`}>
              <View className={`h-4 w-4 rounded-full bg-white ${form.autoMark ? 'self-end' : ''}`} />
            </View>
            <Text className="text-bp-textSecondary text-sm">{t('admin.cardsMarkThemselves') ?? 'Cards mark themselves'}</Text>
          </Pressable>
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
          <Pressable
            onPress={() => setForm((f) => ({ ...f, startWhenFull: !f.startWhenFull }))}
            className="flex-row items-center gap-2"
          >
            <View className={`w-9 h-5 rounded-full px-0.5 justify-center ${form.startWhenFull ? 'bg-bp-primary' : 'bg-bp-textInactive'}`}>
              <View className={`h-4 w-4 rounded-full bg-white ${form.startWhenFull ? 'self-end' : ''}`} />
            </View>
            <Text className="text-bp-textSecondary text-sm">{t('admin.startWhenFull') ?? 'Start when full'}</Text>
          </Pressable>
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
  const [entryFee, setEntryFee] = useState(DEFAULTS.entryFee);
  const [maxPlayers, setMaxPlayers] = useState(DEFAULTS.maxPlayers);
  const [callInterval, setCallInterval] = useState(DEFAULTS.callInterval);
  const [commission, setCommission] = useState(DEFAULTS.commissionPercent);
  const [winningPattern, setWinningPattern] = useState('SINGLE_LINE');
  const [autoMark, setAutoMark] = useState(true);
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
      maxPlayers: Number(maxPlayers),
      callInterval: Number(callInterval),
      commissionPercent: Number(commission),
      winningPattern,
      autoMark,
    };
    try {
      await gamesApi.create(payload);
      setSuccess(true);
      setEntryFee(DEFAULTS.entryFee);
      setMaxPlayers(DEFAULTS.maxPlayers);
    } catch (e) {
      setError((e as { userMessage?: string }).userMessage ?? 'Could not create game');
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card className="gap-3">
      <View>
        <Text className="text-bp-textSecondary text-[11px] uppercase tracking-wider">{t('admin.createGame') ?? 'Create game'}</Text>
        <Text className="text-bp-textPrimary font-semibold">{t('admin.newTableSetup') ?? 'New table setup'}</Text>
      </View>

      {success ? (
        <Text className="text-bp-accentInk text-sm">✓ {t('admin.gameCreatedNotice') ?? 'Game created'}</Text>
      ) : null}
      {error ? <Text className="text-bp-dangerInk text-sm">✕ {error}</Text> : null}

      <FieldRow label={t('admin.entryFee') ?? 'Entry fee'}>
        <AppTextInput value={entryFee} onChangeText={setEntryFee} keyboardType="numeric" placeholder="10" />
      </FieldRow>
      <FieldRow label={t('admin.maxPlayers') ?? 'Max players'}>
        <AppTextInput value={maxPlayers} onChangeText={setMaxPlayers} keyboardType="numeric" />
      </FieldRow>
      <FieldRow label={t('admin.callInterval') ?? 'Call interval (s)'}>
        <AppTextInput value={callInterval} onChangeText={setCallInterval} keyboardType="numeric" />
      </FieldRow>
      <FieldRow label={t('admin.commission') ?? 'Commission %'}>
        <AppTextInput value={commission} onChangeText={setCommission} keyboardType="numeric" />
      </FieldRow>
      <FieldRow label={t('admin.winningPattern') ?? 'Winning pattern'}>
        <Pressable onPress={() => setShowPattern(true)} className="active:opacity-80">
          <View className="bg-bp-surfaceAlt border border-bp-borderInactive rounded-xl px-4 py-3">
            <Text className="text-bp-textPrimary">{t(`patterns.${winningPattern}`) ?? winningPattern}</Text>
          </View>
        </Pressable>
      </FieldRow>
      <Pressable onPress={() => setAutoMark((v) => !v)} className="flex-row items-center gap-2">
        <View className={`w-9 h-5 rounded-full px-0.5 justify-center ${autoMark ? 'bg-bp-primary' : 'bg-bp-textInactive'}`}>
          <View className={`h-4 w-4 rounded-full bg-white ${autoMark ? 'self-end' : ''}`} />
        </View>
        <Text className="text-bp-textSecondary text-sm">{t('admin.autoMark') ?? 'Auto-mark'}</Text>
      </Pressable>
      <Button disabled={busy} onPress={() => void submit()}>
        {busy ? (t('admin.creating') ?? 'Creating…') : (t('admin.createGame') ?? 'Create game')}
      </Button>

      {showPattern && (
        <Modal onClose={() => setShowPattern(false)}>
          <Card className="max-h-[70%]">
            <Text className="text-bp-textPrimary font-semibold mb-2">{t('admin.winningPattern') ?? 'Winning pattern'}</Text>
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
                    className={`rounded-xl border px-4 py-3 ${selected ? 'border-bp-primary bg-bp-primary15' : 'border-bp-borderInactive'}`}
                  >
                    <Text className={selected ? 'text-bp-primary font-semibold' : 'text-bp-textPrimary'}>
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