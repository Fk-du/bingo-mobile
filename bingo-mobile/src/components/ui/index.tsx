import {
  Modal as RNModal,
  Pressable,
  PressableProps,
  StyleProp,
  StyleSheet,
  Text,
  TextInput,
  TextInputProps,
  TextProps,
  View,
  ViewProps,
  ViewStyle,
} from 'react-native';
import { Children, ReactNode } from 'react';
import { Href, useRouter } from 'expo-router';
import { useTheme } from '@/lib/theme';
import { translateClientMessage } from '@/lib/clientTranslations';
import { useTranslate } from '@/hooks/useTranslate';
import { IconBack } from './icons';

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'outline' | 'success' | 'gold' | 'neutral' | 'green';

const VARIANT_STYLES: Record<Variant, { bg?: string; text: string; border?: string }> = {
  primary: { bg: '#6B5BFF', text: '#FFFFFF' },
  secondary: { bg: '#FFB454', text: '#241a00' },
  ghost: { text: 'textSecondary' },
  danger: { bg: '#FF5C6C', text: '#FFFFFF' },
  outline: { border: '#6B5BFF', text: '#6B5BFF' },
  success: { bg: '#27ae60', text: '#FFFFFF' },
  gold: { bg: '#8B5E3C', text: '#1a1500' },
  neutral: { bg: 'elevated', border: 'borderInactive', text: 'textPrimary' },
  green: { border: '#27ae60', text: '#27ae60' },
};

export function Button({
  variant = 'primary',
  disabled,
  onPress,
  children,
  style,
  compact,
  className,
  ...rest
}: PressableProps & {
  variant?: Variant;
  children?: ReactNode;
  compact?: boolean;
  className?: string;
  style?: any;
}) {
  const { colors } = useTheme();
  const parts = Children.toArray(children);
  const isTextLike = parts.length > 0 && parts.every((p) => typeof p === 'string' || typeof p === 'number');
  const v = VARIANT_STYLES[variant];
  const bgColor = v.bg === 'elevated' ? colors.elevated : v.bg;
  const textColor = v.text === 'textPrimary' ? colors.textPrimary : v.text === 'textSecondary' ? colors.textSecondary : v.text;
  const borderColor = v.border === 'borderInactive' ? colors.borderInactive : v.border;

  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      className={`rounded-xl px-4 py-3 items-center justify-center active:opacity-80 ${compact ? 'px-2 py-1.5 rounded-lg' : ''} ${className ?? ''}`}
      style={[
        { backgroundColor: bgColor, borderColor, borderWidth: v.border ? 1 : 0 },
        disabled && { opacity: 0.4 },
        style,
      ]}
      {...rest}
    >
      {isTextLike ? (
        <Text className={`${compact ? 'text-xs' : 'text-base'}`} style={{ color: textColor }}>{parts}</Text>
      ) : (
        children
      )}
    </Pressable>
  );
}

export function Card({
  children,
  className = '',
  style,
}: {
  children: ReactNode;
  className?: string;
  style?: StyleProp<ViewStyle>;
}) {
  const { colors } = useTheme();
  return (
    <View
      className={`rounded-2xl p-4 ${className}`}
      style={[{ backgroundColor: colors.surface, borderColor: colors.borderInactive, borderWidth: 1 }, style]}
    >
      {children}
    </View>
  );
}

export function Screen({ children, className = '', style, ...rest }: ViewProps & { className?: string }) {
  const { colors } = useTheme();
  return (
    <View className={`flex-1 px-4 pt-4 ${className}`} style={[{ backgroundColor: colors.background }, style]} {...rest}>
      {children}
    </View>
  );
}

export function Title({ children, className = '', style, ...rest }: TextProps & { className?: string }) {
  const { colors } = useTheme();
  return (
    <Text className={`text-2xl font-bold ${className}`} style={[{ color: colors.textPrimary }, style]} {...rest}>
      {children}
    </Text>
  );
}

export function Subtitle({ children, className = '', style, ...rest }: TextProps & { className?: string }) {
  const { colors } = useTheme();
  return (
    <Text className={`text-base ${className}`} style={[{ color: colors.textSecondary }, style]} {...rest}>
      {children}
    </Text>
  );
}

export function FieldLabel({ children }: { children: ReactNode }) {
  const { colors } = useTheme();
  return <Text className="text-sm mb-1" style={{ color: colors.textSecondary }}>{children}</Text>;
}

export function AppTextInput(props: TextInputProps & { className?: string }) {
  const { className = '', style, ...rest } = props;
  const { colors } = useTheme();
  return (
    <TextInput
      placeholderTextColor={colors.textInactive}
      className={`rounded-xl px-4 py-3 text-base ${className}`}
      style={[{ backgroundColor: colors.surfaceAlt, borderColor: colors.borderInactive, borderWidth: 1, color: colors.textPrimary }, style]}
      {...rest}
    />
  );
}

/**
 * The label every screen starts with: a quiet uppercase eyebrow naming the
 * area, then the page title. Keeping it on every screen means content never
 * begins flush against the top edge with nothing to orient the reader.
 */
export function ScreenHeader({
  title,
  eyebrow,
  description,
  left,
  right,
}: {
  title: string;
  eyebrow?: string;
  description?: string;
  left?: ReactNode;
  right?: ReactNode;
}) {
  const t = useTranslate();
  const { colors } = useTheme();
  return (
    <View className="flex-row items-start gap-3 pt-3 pb-5">
      {left ? <View className="shrink-0">{left}</View> : null}
      <View className="min-w-0 flex-1">
        <Text className="text-[11px] font-medium uppercase tracking-[0.2em]" style={{ color: colors.textInactive }}>
          {eyebrow ?? t('common.appName') ?? 'BingoPlus'}
        </Text>
        <Title className="mt-1">{title}</Title>
        {description ? <Subtitle className="mt-1 text-sm">{description}</Subtitle> : null}
      </View>
      {right ? <View className="shrink-0">{right}</View> : null}
    </View>
  );
}

/**
 * The leading back control for a screen reached from a menu. The nav bar holds
 * a single icon now, so a screen below it has to offer its own way out: back
 * returns to whatever sent the player there, and a screen opened cold from a
 * link has nothing to go back to, so it falls back to the player home rather
 * than dropping out of the app.
 *
 * Back always goes one step first. `router.back()` pops the previous screen on
 * the stack — the settings screen reached from the game board's gear returns
 * to the game, not to the profile. `to` is only consulted when there is nothing
 * behind to go back to (a cold start, say), and then it navigates there.
 */
export function ScreenBackButton({ to, fallback = '/(player)' }: { to?: Href; fallback?: Href }) {
  const t = useTranslate();
  const router = useRouter();
  const { colors } = useTheme();

  const goBack = () => {
    if (router.canGoBack()) {
      router.back();
      return;
    }
    if (to) router.navigate(to);
    else router.replace(fallback);
  };

  return (
    <Pressable
      onPress={goBack}
      hitSlop={8}
      accessibilityRole="button"
      accessibilityLabel={t('common.back') ?? 'Back'}
      className="h-9 w-9 items-center justify-center rounded-full active:opacity-70"
      style={[{ backgroundColor: colors.surface, borderColor: colors.borderInactive, borderWidth: 1 }]}
    >
      <IconBack color={colors.textSecondary} size={18} />
    </Pressable>
  );
}

export function Modal({
  children,
  onClose,
}: {
  children: ReactNode;
  onClose?: () => void;
}) {
  return (
    <RNModal transparent animationType="fade" onRequestClose={onClose}>
      <View className="flex-1 bg-black/60 items-center justify-center px-6">
        <Pressable style={StyleSheet.absoluteFill} onPress={onClose} />
        <View className="w-full">{children}</View>
      </View>
    </RNModal>
  );
}

/** Uppercase status badge with a tone derived from the status value. */
export function StatusPill({
  status,
  label,
}: {
  status: string;
  label?: string;
}) {
  useTranslate();
  const { colors } = useTheme();
  const { style, color } = statusTone(status, colors);
  return (
    <View className="px-2.5 py-0.5 rounded-full border" style={style}>
      <Text className="text-[10px] uppercase font-bold tracking-[0.14em]" style={{ color }}>
        {label ?? translateClientMessage(`status.${status}`) ?? status.replace(/_/g, ' ')}
      </Text>
    </View>
  );
}

function statusTone(status: string, colors: { surfaceAlt: string; textSecondary: string }): { style: any; color: string } {
  const s = status.toLowerCase();
  if (/(approved|open|active)/.test(s)) return { style: { borderColor: '#27ae6040', backgroundColor: '#27ae6015' }, color: '#059669' };
  if (/(pending|review)/.test(s)) return { style: { borderColor: '#f2994a40', backgroundColor: '#f2994a15' }, color: '#d97706' };
  if (/(reject|inactive|ended|cancelled|canceled)/.test(s)) return { style: { borderColor: '#FF5C6C40', backgroundColor: '#FF5C6C15' }, color: '#dc2626' };
  if (/(progress|running|live)/.test(s)) return { style: { borderColor: '#FF5C6C40', backgroundColor: '#FF5C6C10' }, color: '#dc2626' };
  return { style: { borderColor: '#6B5BFF', backgroundColor: colors.surfaceAlt }, color: colors.textSecondary };
}

/** Stat card matching the mini app's MetricCard: tinted container, plain value, optional note. */
export function Metric({
  label,
  value,
  tone = 'default',
  note,
}: {
  label: string;
  value: string | number;
  tone?: 'default' | 'gold' | 'primary' | 'success' | 'warning' | 'danger';
  note?: string;
}) {
  const { colors } = useTheme();
  const toneStyle: Record<string, { border: string; bg: string }> = {
    default: { border: colors.borderInactive, bg: colors.surfaceAlt },
    gold: { border: '#8B5E3C30', bg: '#8B5E3C10' },
    primary: { border: '#6B5BFF30', bg: '#6B5BFF10' },
    success: { border: '#27ae6030', bg: '#27ae6010' },
    warning: { border: '#f2994a30', bg: '#f2994a10' },
    danger: { border: '#FF5C6C30', bg: '#FF5C6C10' },
  };
  const t = toneStyle[tone];
  return (
    <View className="flex-1 rounded-2xl border p-4" style={{ borderColor: t.border, backgroundColor: t.bg }}>
      <Text className="text-[11px] font-medium uppercase tracking-[0.18em]" style={{ color: colors.textSecondary }} numberOfLines={2}>
        {label}
      </Text>
      <Text
        className="mt-1.5 text-2xl font-bold"
        style={{ color: colors.textPrimary }}
        numberOfLines={1}
        adjustsFontSizeToFit
        minimumFontScale={0.6}
      >
        {value}
      </Text>
      {note ? <Text className="mt-1 text-xs" style={{ color: colors.textSecondary }}>{note}</Text> : null}
    </View>
  );
}

/** Mini app SectionHeader: eyebrow + bold title + muted description, optional action. */
export function SectionHeader({
  eyebrow,
  title,
  description,
  action,
}: {
  eyebrow?: string;
  title: string;
  description?: string;
  action?: ReactNode;
}) {
  const { colors } = useTheme();
  return (
    <View className="flex-row flex-wrap items-end justify-between gap-3 pt-4 pb-2">
      <View className="min-w-0 flex-1">
        {eyebrow ? (
          <Text className="text-[11px] font-medium uppercase tracking-[0.2em]" style={{ color: colors.textInactive }}>
            {eyebrow}
          </Text>
        ) : null}
        <Title className="mt-0.5 text-xl">{title}</Title>
        {description ? (
          <Text className="mt-1 text-sm" style={{ color: colors.textSecondary }}>{description}</Text>
        ) : null}
      </View>
      {action}
    </View>
  );
}

/** Centered empty-state card. */
export function EmptyState({ title, description }: { title: string; description?: string }) {
  const { colors } = useTheme();
  return (
    <Card className="items-center p-8">
      <Text className="font-semibold text-center" style={{ color: colors.textPrimary }}>{title}</Text>
      {description ? (
        <Text className="text-sm text-center mt-1" style={{ color: colors.textSecondary }}>{description}</Text>
      ) : null}
    </Card>
  );
}