import { useState } from 'react';
import { Pressable, ScrollView, Text, View } from 'react-native';
import { Screen, ScreenBackButton, ScreenHeader } from '@/components/ui';
import { useTranslate } from '@/hooks/useTranslate';
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
  // The gear sits on the game board, so that is where back goes. Asking the
  // store rather than popping keeps it right even after the board has been
  // left behind, and the fallback lands on the home screen, which resolves the
  // live game itself.
  const activeGameId = useGameStore((s) => s.activeGameId);
  const markColor = useGameSettings((s) => s.markColor);
  const cardSort = useGameSettings((s) => s.cardSort);
  const autoMark = useGameSettings((s) => s.autoMark);
  const soundEnabled = useGameSettings((s) => s.soundEnabled);
  const setMarkColor = useGameSettings((s) => s.setMarkColor);
  const setCardSort = useGameSettings((s) => s.setCardSort);
  const setAutoMark = useGameSettings((s) => s.setAutoMark);
  const setSoundEnabled = useGameSettings((s) => s.setSoundEnabled);

  // Only one panel open at a time: opening a second would grow the screen
  // again, which is the thing this layout exists to avoid.
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

        <SwitchRow
          on={autoMark}
          onToggle={() => setAutoMark(!autoMark)}
          title={t('gameSettings.autoMark') ?? 'Auto-mark'}
          value={
            autoMark
              ? (t('admin.automatic') ?? 'Automatic')
              : (t('game.manualMarking') ?? 'Manual')
          }
        />

        <SwitchRow
          on={soundEnabled}
          onToggle={() => setSoundEnabled(!soundEnabled)}
          title={t('gameSettings.sound') ?? 'Call sound'}
          value={soundEnabled ? (t('game.soundOn') ?? 'On') : (t('game.soundOff') ?? 'Off')}
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

/**
 * One on/off setting, in the pill-and-label form the board used to use for
 * these two. A switch rather than a panel: there is nothing to choose inside
 * it, so a second tap target would only add height.
 */
function SwitchRow({
  on,
  onToggle,
  title,
  value,
}: {
  on: boolean;
  onToggle: () => void;
  title: string;
  value: string;
}) {
  return (
    <Pressable
      onPress={onToggle}
      accessibilityRole="switch"
      accessibilityState={{ checked: on }}
      accessibilityLabel={title}
      className="flex-row items-center justify-between rounded-2xl border border-bp-borderInactive bg-bp-surface px-4 py-3 active:opacity-80"
    >
      <Text className="font-semibold text-bp-textPrimary">{title}</Text>
      <View className="flex-row items-center gap-2">
        <Text className="text-sm text-bp-textSecondary">{value}</Text>
        <View
          className={`h-5 w-9 justify-center rounded-full ${on ? 'bg-bp-primary' : 'bg-bp-textInactive'}`}
          style={{ paddingLeft: 2 }}
        >
          <View className={`h-4 w-4 rounded-full bg-white ${on ? 'self-end' : ''}`} />
        </View>
      </View>
    </Pressable>
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
