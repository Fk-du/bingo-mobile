import { useState } from 'react';
import { Pressable, ScrollView, Text, View } from 'react-native';
import { Screen, ScreenHeader } from '@/components/ui';
import { useTranslate } from '@/hooks/useTranslate';
import { CARD_SORTS, MarkColor, MARK_COLORS, useGameSettings } from '@/store/gameSettings.store';

const MARK_OPTIONS: MarkColor[] = ['red', 'green', 'blue', 'purple', 'orange'];

type OptionKey = 'markColor' | 'cardSort';

/**
 * How the board looks, not how it plays. Sound and auto mark stay on the game
 * screen: both change what the player is doing right now, and auto mark is
 * server state per card, so putting it behind a settings route would hide a
 * control the player needs mid-game.
 *
 * Every setting is collapsed to one row showing its current value. Opening a
 * panel is the only thing that adds height, so the screen stays short.
 */
export default function GameSettingsScreen() {
  const t = useTranslate();
  const markColor = useGameSettings((s) => s.markColor);
  const cardSort = useGameSettings((s) => s.cardSort);
  const setMarkColor = useGameSettings((s) => s.setMarkColor);
  const setCardSort = useGameSettings((s) => s.setCardSort);

  // Only one panel open at a time: opening a second would grow the screen
  // again, which is the thing this layout exists to avoid.
  const [open, setOpen] = useState<OptionKey | null>(null);
  const toggle = (key: OptionKey) => setOpen((current) => (current === key ? null : key));

  return (
    <Screen>
      <ScreenHeader title={t('gameSettings.title') ?? 'Game settings'} />
      <ScrollView contentContainerClassName="gap-2 pb-8">
        <Text className="px-1 pb-1 text-xs text-bp-textSecondary">
          {t('gameSettings.subtitle') ?? 'These choices are saved on this device.'}
        </Text>

        <Dropdown
          open={open === 'markColor'}
          onToggle={() => toggle('markColor')}
          title={t('gameSettings.markColor') ?? 'Called number colour'}
          value={t(`gameSettings.colors.${markColor}`) ?? markColor}
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
                      borderColor: active ? '#6B5BFF' : 'transparent',
                    }}
                  >
                    {active ? <Text className="text-base leading-none text-white">✓</Text> : null}
                  </View>
                  <Text
                    className={`text-[10px] ${active ? 'font-bold text-bp-primary' : 'text-bp-textSecondary'}`}
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
        >
          <View className="gap-1.5">
            {CARD_SORTS.map((option) => {
              const active = option === cardSort;
              return (
                <Choice
                  key={option}
                  active={active}
                  label={t(`gameSettings.sorts.${option}`) ?? option}
                  onPress={() => setCardSort(option)}
                />
              );
            })}
          </View>
        </Dropdown>
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
}: {
  open: boolean;
  onToggle: () => void;
  title: string;
  value: string;
  children: React.ReactNode;
}) {
  return (
    <View className="gap-2">
      <Pressable
        onPress={onToggle}
        accessibilityRole="button"
        accessibilityState={{ expanded: open }}
        accessibilityLabel={title}
        className="flex-row items-center justify-between rounded-2xl border border-bp-borderInactive bg-bp-surface px-4 py-3 active:opacity-80"
      >
        <Text className="font-semibold text-bp-textPrimary">{title}</Text>
        <View className="flex-row items-center gap-2">
          <Text className="text-sm text-bp-textSecondary">{value}</Text>
          <Text className="text-bp-primary">{open ? '−' : '›'}</Text>
        </View>
      </Pressable>
      {open ? <View className="px-1 pb-1">{children}</View> : null}
    </View>
  );
}

/** One selectable value inside an open panel. */
function Choice({
  active,
  label,
  onPress,
}: {
  active: boolean;
  label: string;
  onPress: () => void;
}) {
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="radio"
      accessibilityState={{ selected: active }}
      className="flex-row items-center justify-between rounded-xl border px-3 py-2.5 active:opacity-80"
      style={{
        borderColor: active ? '#6B5BFF' : 'transparent',
        backgroundColor: active ? 'rgba(107,91,255,0.10)' : 'transparent',
      }}
    >
      <Text className={`text-sm ${active ? 'font-semibold text-bp-textPrimary' : 'text-bp-textSecondary'}`}>
        {label}
      </Text>
      {active ? <Text className="text-sm text-bp-primary">✓</Text> : null}
    </Pressable>
  );
}
