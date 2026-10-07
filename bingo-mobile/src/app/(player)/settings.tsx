import { useState } from 'react';
import { Pressable, ScrollView, Text, View } from 'react-native';
import { Screen, ScreenBackButton, ScreenHeader } from '@/components/ui';
import { useTranslate } from '@/hooks/useTranslate';
import { useTheme } from '@/lib/theme';
import { CARD_SORTS, MarkColor, MARK_COLORS, useGameSettings } from '@/store/gameSettings.store';
import { useGameStore } from '@/store/game.store';

const MARK_OPTIONS: MarkColor[] = ['red', 'green', 'blue', 'purple', 'orange', 'black'];

type OptionKey = 'markColor' | 'cardSort';

/**
 * Everything about how the board behaves, all of it saved on this device and
 * applying to every game rather than one table: how the board looks, and
 * whether the cards mark themselves or the numbers are spoken. Marking and
 * sound used to be switched per game from the board itself, which meant a
 * player who prefers marking by hand had to ask again in every game — and the
 * board lost two controls to get them back.
 *
 * The one-panel-at-a-time rule stays: opening a second panel would grow the
 * screen again, which is what this layout exists to avoid.
 */
export default function GameSettingsScreen() {
  const t = useTranslate();
  const { colors } = useTheme();
  const activeGameId = useGameStore((s) => s.activeGameId);
  const markColor = useGameSettings((s) => s.markColor);
  const cardSort = useGameSettings((s) => s.cardSort);
  const autoMark = useGameSettings((s) => s.autoMark);
  const soundEnabled = useGameSettings((s) => s.soundEnabled);
  const setMarkColor = useGameSettings((s) => s.setMarkColor);
  const setCardSort = useGameSettings((s) => s.setCardSort);
  const setAutoMark = useGameSettings((s) => s.setAutoMark);
  const setSoundEnabled = useGameSettings((s) => s.setSoundEnabled);

  const [open, setOpen] = useState<OptionKey | null>(null);
  const toggle = (key: OptionKey) => setOpen((current) => (current === key ? null : key));

  return (
    <Screen>
      <ScreenHeader
        title={t('gameSettings.title') ?? 'Game settings'}
        left={
          <ScreenBackButton
            to={
              activeGameId == null
                ? '/(player)'
                : { pathname: '/(player)/game/[id]', params: { id: String(activeGameId) } }
            }
          />
        }
      />
      <ScrollView contentContainerClassName="gap-2 pb-8">
        <Text className="px-1 pb-1 text-xs" style={{ color: colors.textSecondary }}>
          {t('gameSettings.subtitle') ?? 'These choices are saved on this device.'}
        </Text>

        <Dropdown
          open={open === 'markColor'}
          onToggle={() => toggle('markColor')}
          title={t('gameSettings.markColor') ?? 'Called number colour'}
          value={t(`gameSettings.colors.${markColor}`) ?? markColor}
          colors={colors}
        >
          <View className="flex-row flex-wrap" style={{ gap: 10 }}>
            {MARK_OPTIONS.map((option) => {
              const active = option === markColor;
              return (
                <Pressable
                  key={option}
                  onPress={() => setMarkColor(option)}
                  accessibilityRole="radio"
                  accessibilityState={{ selected: active }}
                  accessibilityLabel={t(`gameSettings.colors.${option}`) ?? option}
                  className="items-center gap-1.5"
                >
                  <View
                    className="h-11 w-11 items-center justify-center rounded-xl border-2"
                    style={{
                      backgroundColor: MARK_COLORS[option].fill,
                      borderColor: active ? colors.primary : 'transparent',
                    }}
                  >
                    {active ? <Text className="text-base leading-none text-white">✓</Text> : null}
                  </View>
                  <Text
                    className={`text-[10px] ${active ? 'font-bold' : ''}`}
                    style={{ color: active ? colors.primary : colors.textSecondary }}
                  >
                    {t(`gameSettings.colors.${option}`) ?? option}
                  </Text>
                </Pressable>
              );
            })}
          </View>
        </Dropdown>

        <Dropdown
          open={open === 'cardSort'}
          onToggle={() => toggle('cardSort')}
          title={t('gameSettings.cardSort') ?? 'Card order'}
          value={t(`gameSettings.sorts.${cardSort}`) ?? cardSort}
          colors={colors}
        >
          <View className="gap-1.5">
            {CARD_SORTS.map((option) => (
              <Choice
                key={option}
                active={option === cardSort}
                label={t(`gameSettings.sorts.${option}`) ?? option}
                onPress={() => setCardSort(option)}
                colors={colors}
              />
            ))}
          </View>
        </Dropdown>

        <SwitchRow
          label={t('gameSettings.autoMark') ?? 'Auto-mark numbers'}
          enabled={autoMark}
          onToggle={() => setAutoMark(!autoMark)}
          colors={colors}
        />

        <SwitchRow
          label={t('gameSettings.sound') ?? 'Enable call sound'}
          enabled={soundEnabled}
          onToggle={() => setSoundEnabled(!soundEnabled)}
          colors={colors}
        />
      </ScrollView>
    </Screen>
  );
}

/**
 * A collapsed row showing the setting and its current value. Tapping it opens
 * the panel below; only one is open at a time.
 */
function Dropdown({
  open,
  onToggle,
  title,
  value,
  children,
  colors,
}: {
  open: boolean;
  onToggle: () => void;
  title: string;
  value: string;
  children: React.ReactNode;
  colors: { borderInactive: string; surface: string; textPrimary: string; textSecondary: string; primary: string };
}) {
  return (
    <View className="gap-2">
      <Pressable
        onPress={onToggle}
        accessibilityRole="button"
        accessibilityState={{ expanded: open }}
        accessibilityLabel={title}
        className="flex-row items-center justify-between rounded-2xl border px-4 py-3 active:opacity-80"
        style={{ borderColor: colors.borderInactive, backgroundColor: colors.surface }}
      >
        <Text className="font-semibold" style={{ color: colors.textPrimary }}>{title}</Text>
        <View className="flex-row items-center gap-2">
          <Text className="text-sm" style={{ color: colors.textSecondary }}>{value}</Text>
          <Text style={{ color: colors.primary }}>{open ? '−' : '›'}</Text>
        </View>
      </Pressable>
      {open ? <View className="px-1 pb-1">{children}</View> : null}
    </View>
  );
}

/**
 * A toggle switch for a boolean setting. Modern iOS/Android style toggle.
 * Displays with smooth animation between enabled and disabled states.
 */
function SwitchRow({
  label,
  enabled,
  onToggle,
  colors,
}: {
  label: string;
  enabled: boolean;
  onToggle: () => void;
  colors: { borderInactive: string; surface: string; textPrimary: string; textSecondary: string; primary: string };
}) {
  return (
    <Pressable
      onPress={onToggle}
      accessibilityRole="switch"
      accessibilityState={{ checked: enabled }}
      accessibilityLabel={label}
      className="flex-row items-center justify-between rounded-2xl border px-4 py-3 active:opacity-80"
      style={{ borderColor: colors.borderInactive, backgroundColor: colors.surface }}
    >
      <Text className="font-semibold" style={{ color: colors.textPrimary }}>{label}</Text>
      <View
        className="h-7 w-12 items-center justify-start rounded-full"
        style={{ backgroundColor: enabled ? colors.primary : colors.borderInactive }}
      >
        <View
          className="m-1 h-5 w-5 rounded-full bg-white"
          style={{ marginLeft: enabled ? 20 : 4 }}
        />
      </View>
    </Pressable>
  );
}

/** One selectable value inside an open panel. */
function Choice({
  active,
  label,
  onPress,
  colors,
}: {
  active: boolean;
  label: string;
  onPress: () => void;
  colors: { borderInactive: string; surface: string; textPrimary: string; textSecondary: string; primary: string };
}) {
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="radio"
      accessibilityState={{ selected: active }}
      className="flex-row items-center justify-between rounded-xl border px-3 py-2.5 active:opacity-80"
      style={{
        borderColor: active ? colors.primary : 'transparent',
        backgroundColor: active ? colors.primary + '15' : 'transparent',
      }}
    >
      <Text className={`text-sm ${active ? 'font-semibold' : ''}`} style={{ color: active ? colors.textPrimary : colors.textSecondary }}>
        {label}
      </Text>
      {active ? <Text className="text-sm" style={{ color: colors.primary }}>✓</Text> : null}
    </Pressable>
  );
}
