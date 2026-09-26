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
import { useTheme } from '@/lib/theme';
import { translateClientMessage } from '@/lib/clientTranslations';
import { useTranslate } from '@/hooks/useTranslate';

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'outline' | 'success' | 'gold' | 'neutral' | 'green';

const VARIANT_STYLES: Record<Variant, string> = {
  primary: 'bg-bp-primary',
  secondary: 'bg-bp-secondary',
  ghost: 'bg-transparent',
  danger: 'bg-bp-danger',
  outline: 'bg-transparent border border-bp-borderActive',
  success: 'bg-bp-success',
  gold: 'bg-bp-gold',
  neutral: 'bg-bp-elevated border border-bp-borderInactive',
  green: 'bg-transparent border border-bp-success',
};

const LABEL_STYLES: Record<Variant, string> = {
  primary: 'text-white',
  secondary: 'text-[#241a00] font-semibold',
  ghost: 'text-bp-textSecondary',
  danger: 'text-white',
  outline: 'text-bp-primary',
  success: 'text-white',
  gold: 'text-[#1a1500] font-bold',
  neutral: 'text-bp-textPrimary',
  green: 'text-bp-successInk',
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
  const parts = Children.toArray(children);
  const isTextLike = parts.length > 0 && parts.every((p) => typeof p === 'string' || typeof p === 'number');

  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      className={`rounded-xl px-4 py-3 items-center justify-center active:opacity-80 ${compact ? 'px-2 py-1.5 rounded-lg' : ''} ${VARIANT_STYLES[variant]} ${disabled ? 'opacity-40' : ''} ${className ?? ''}`}
      style={style}
      {...rest}
    >
      {isTextLike ? (
        <Text className={`${compact ? 'text-xs' : 'text-base'} ${LABEL_STYLES[variant]}`}>{parts}</Text>
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
  return (
    <View
      className={`bg-bp-surface rounded-2xl border border-bp-borderInactive p-4 ${className}`}
      style={style}
    >
      {children}
    </View>
  );
}

export function Screen({ children, className = '', ...rest }: ViewProps & { className?: string }) {
  return (
    <View className={`flex-1 bg-bp-background px-4 ${className}`} {...rest}>
      {children}
    </View>
  );
}

export function Title({ children, className = '', ...rest }: TextProps & { className?: string }) {
  return (
    <Text className={`text-2xl font-bold text-bp-textPrimary ${className}`} {...rest}>
      {children}
    </Text>
  );
}

export function Subtitle({ children, className = '', ...rest }: TextProps & { className?: string }) {
  return (
    <Text className={`text-base text-bp-textSecondary ${className}`} {...rest}>
      {children}
    </Text>
  );
}

export function FieldLabel({ children }: { children: ReactNode }) {
  return <Text className="text-sm text-bp-textSecondary mb-1">{children}</Text>;
}

export function AppTextInput(props: TextInputProps & { className?: string }) {
  const { className = '', style, ...rest } = props;
  const { colors } = useTheme();
  return (
    <TextInput
      placeholderTextColor={colors.textInactive}
      className={`bg-bp-surfaceAlt border border-bp-borderInactive rounded-xl px-4 py-3 text-base text-bp-textPrimary ${className}`}
      style={style}
      {...rest}
    />
  );
}

export function ScreenHeader({ title, right }: { title: string; right?: ReactNode }) {
  return (
    <View className="flex-row items-center justify-between py-4">
      <Title>{title}</Title>
      {right}
    </View>
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
  const tone = statusTone(status);
  const text = toneText(status);
  return (
    <View className={`px-2.5 py-0.5 rounded-full border ${tone}`}>
      <Text className={`text-[10px] uppercase font-bold tracking-[0.14em] ${text}`}>
        {label ?? translateClientMessage(`status.${status}`) ?? status.replace(/_/g, ' ')}
      </Text>
    </View>
  );
}

function statusTone(status: string): string {
  const s = status.toLowerCase();
  if (/(approved|open|active)/.test(s)) return 'border-bp-success40 bg-bp-success15';
  if (/(pending|review)/.test(s)) return 'border-bp-warning40 bg-bp-warning15';
  if (/(reject|inactive|ended|cancelled|canceled)/.test(s)) return 'border-bp-danger40 bg-bp-danger15';
  if (/(progress|running|live)/.test(s)) return 'border-bp-danger40 bg-bp-danger10';
  return 'border-bp-borderActive bg-bp-surfaceAlt';
}

function toneText(status: string): string {
  const s = status.toLowerCase();
  if (/(approved|open|active)/.test(s)) return 'text-emerald-500';
  if (/(pending|review)/.test(s)) return 'text-amber-500';
  if (/(reject|inactive|ended|cancelled|canceled)/.test(s)) return 'text-red-500';
  if (/(progress|running|live)/.test(s)) return 'text-red-500';
  return 'text-bp-textSecondary';
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
  const toneClass: Record<string, string> = {
    default: 'border-bp-borderInactive bg-bp-surfaceAlt',
    gold: 'border-bp-gold30 bg-bp-gold10',
    primary: 'border-bp-primary30 bg-bp-primary10',
    success: 'border-bp-success30 bg-bp-success10',
    warning: 'border-bp-warning30 bg-bp-warning10',
    danger: 'border-bp-danger30 bg-bp-danger10',
  };
  return (
    <View className={`flex-1 rounded-2xl border p-4 ${toneClass[tone]}`}>
      <Text className="text-[11px] font-medium uppercase tracking-[0.18em] text-bp-textSecondary">
        {label}
      </Text>
      <Text className="mt-1.5 text-2xl font-bold text-bp-textPrimary">{value}</Text>
      {note ? <Text className="mt-1 text-xs text-bp-textSecondary">{note}</Text> : null}
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
  return (
    <View className="flex-row flex-wrap items-end justify-between gap-3 pt-4 pb-2">
      <View className="min-w-0 flex-1">
        {eyebrow ? (
          <Text className="text-[11px] font-medium uppercase tracking-[0.2em] text-bp-textInactive">
            {eyebrow}
          </Text>
        ) : null}
        <Title className="mt-0.5 text-xl">{title}</Title>
        {description ? (
          <Text className="mt-1 text-sm text-bp-textSecondary">{description}</Text>
        ) : null}
      </View>
      {action}
    </View>
  );
}

/** Centered empty-state card. */
export function EmptyState({ title, description }: { title: string; description?: string }) {
  return (
    <Card className="items-center p-8">
      <Text className="text-bp-textPrimary font-semibold text-center">{title}</Text>
      {description ? (
        <Text className="text-bp-textSecondary text-sm text-center mt-1">{description}</Text>
      ) : null}
    </Card>
  );
}