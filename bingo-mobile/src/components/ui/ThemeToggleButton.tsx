import { Pressable, View } from 'react-native';
import { useTheme } from '@/lib/theme';
import { IconMoon, IconSun } from '@/components/ui/icons';

export function ThemeToggleButton() {
  const { isDark, toggle } = useTheme();
  const { colors } = useTheme();
  return (
    <Pressable onPress={toggle} className="active:opacity-70">
      <View className="h-9 w-9 items-center justify-center rounded-full" style={{ backgroundColor: colors.surface, borderColor: colors.borderInactive, borderWidth: 1 }}>
        {isDark ? <IconSun size={18} color={colors.textSecondary} /> : <IconMoon size={18} color={colors.textSecondary} />}
      </View>
    </Pressable>
  );
}