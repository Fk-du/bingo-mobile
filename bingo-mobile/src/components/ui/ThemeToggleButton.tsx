import { Pressable, View } from 'react-native';
import { useTheme } from '@/lib/theme';
import { IconMoon, IconSun } from '@/components/ui/icons';

export function ThemeToggleButton() {
  const { isDark, toggle } = useTheme();
  return (
    <Pressable onPress={toggle} className="active:opacity-70">
      <View className="h-9 w-9 items-center justify-center rounded-full border border-bp-borderInactive bg-bp-surface">
        {isDark ? <IconSun size={18} /> : <IconMoon size={18} />}
      </View>
    </Pressable>
  );
}