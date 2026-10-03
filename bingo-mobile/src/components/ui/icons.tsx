import { ColorValue } from 'react-native';
import Svg, { Circle, Path } from 'react-native-svg';
import { useTheme } from '@/lib/theme';

type IconProps = { color?: ColorValue; size?: number };

function useDefaultIconColor() {
  return useTheme().colors.textInactive;
}

export function IconLobby({ color, size = 20 }: IconProps) {
  const defaultColor = useDefaultIconColor();
  const c = color ?? defaultColor;
  return (
    <Svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke={c} strokeWidth={1.75} strokeLinecap="round" strokeLinejoin="round">
      <Path d="M3 10.5L12 4l9 6.5V20a1 1 0 01-1 1h-5v-6H9v6H4a1 1 0 01-1-1v-9.5z" />
    </Svg>
  );
}

export function IconHistory({ color, size = 20 }: IconProps) {
  const defaultColor = useDefaultIconColor();
  const c = color ?? defaultColor;
  return (
    <Svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke={c} strokeWidth={1.75} strokeLinecap="round" strokeLinejoin="round">
      <Path d="M3 12a9 9 0 109-9 9.7 9.7 0 00-6.74 2.74L3 8" />
      <Path strokeLinecap="round" d="M12 7v5l3 2" />
      <Path strokeLinecap="round" strokeLinejoin="round" d="M3 4v4h4" />
    </Svg>
  );
}

export function IconWallet({ color, size = 20 }: IconProps) {
  const defaultColor = useDefaultIconColor();
  const c = color ?? defaultColor;
  return (
    <Svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke={c} strokeWidth={1.75} strokeLinecap="round" strokeLinejoin="round">
      <Path d="M3 7a2 2 0 012-2h14a2 2 0 012 2v10a2 2 0 01-2 2H5a2 2 0 01-2-2V7z" />
      <Path strokeLinecap="round" d="M17 12h2" />
      <Circle cx="17" cy="12" r="1" fill={c} stroke="none" />
    </Svg>
  );
}

export function IconBell({ color, size = 20 }: IconProps) {
  const defaultColor = useDefaultIconColor();
  const c = color ?? defaultColor;
  return (
    <Svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke={c} strokeWidth={1.75} strokeLinecap="round" strokeLinejoin="round">
      <Path d="M18 8a6 6 0 10-12 0c0 7-3 9-3 9h18s-3-2-3-9" />
      <Path strokeLinecap="round" d="M10 21h4" />
    </Svg>
  );
}

export function IconProfile({ color, size = 20 }: IconProps) {
  const defaultColor = useDefaultIconColor();
  const c = color ?? defaultColor;
  return (
    <Svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke={c} strokeWidth={1.75} strokeLinecap="round" strokeLinejoin="round">
      <Circle cx="12" cy="8" r="4" />
      <Path strokeLinecap="round" d="M5 20c0-3.314 3.134-6 7-6s7 2.686 7 6" />
    </Svg>
  );
}

export function IconSun({ color, size = 20 }: IconProps) {
  const defaultColor = useDefaultIconColor();
  const c = color ?? defaultColor;
  return (
    <Svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke={c} strokeWidth={1.75} strokeLinecap="round" strokeLinejoin="round">
      <Circle cx="12" cy="12" r="4" />
      <Path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41" />
    </Svg>
  );
}

export function IconMoon({ color, size = 20 }: IconProps) {
  const defaultColor = useDefaultIconColor();
  const c = color ?? defaultColor;
  return (
    <Svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke={c} strokeWidth={1.75} strokeLinecap="round" strokeLinejoin="round">
      <Path d="M21 12.79A9 9 0 1111.21 3 7 7 0 0021 12.79z" />
    </Svg>
  );
}

export function IconBack({ color, size = 20 }: IconProps) {
  const defaultColor = useDefaultIconColor();
  const c = color ?? defaultColor;
  return (
    <Svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke={c} strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
      <Path d="M15 18l-6-6 6-6" />
    </Svg>
  );
}

export function IconSettings({ color, size = 20 }: IconProps) {
  const defaultColor = useDefaultIconColor();
  const c = color ?? defaultColor;
  return (
    <Svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke={c} strokeWidth={1.75} strokeLinecap="round" strokeLinejoin="round">
      <Circle cx="12" cy="12" r="3" />
      <Path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09a1.65 1.65 0 0 0-1.08-1.51 1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1Z" />
    </Svg>
  );
}