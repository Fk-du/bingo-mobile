import { useQuery } from '@tanstack/react-query';
import { Tabs } from 'expo-router';
import { ViewStyle } from 'react-native';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';
import { notificationsApi } from '@/api';
import { useTranslate } from '@/hooks/useTranslate';
import { useTheme } from '@/lib/theme';
import { IconProfile, IconSettings } from '@/components/ui/icons';

/** Matches bp-brand-primary. Kept raw so the pill and the icon agree exactly. */
const BRAND = '#6B5BFF';
/** Alpha-baked brand tint, the same value as the bp-primary20 token. */
const BRAND_TINT = '#6B5BFF33';

/**
 * The bar's own metrics. The bar is as tall as one item needs to be, so the
 * label under an icon is never clipped: the icon, one line of label, and the
 * padding the button adds around its contents. It used to be a flat 44, which
 * is a bottom tab bar's height — with a label on top of the icon in a bar this
 * shallow, that cut the label in half.
 */
const BAR_ICON = 26;
const BAR_LABEL_LINE = 14;
const BAR_BUTTON_PADDING = 6;
const BAR_HEIGHT = BAR_ICON + BAR_LABEL_LINE + BAR_BUTTON_PADDING * 2;

/**
 * Air between the top of the screen and the bar. The platform reports no top
 * inset when it has already offset the window, which leaves the bar sitting
 * flush against the top edge; this keeps it off the edge either way.
 */
const BAR_TOP_CLEARANCE = 8;

/**
 * How every item in the bar is drawn: it hugs its icon instead of stretching
 * into a wide button, and sits in a rounded pill that tints when active.
 */
const BAR_ITEM_STYLE: ViewStyle = {
  // `flex` has to be spelled out here, not left to the trio below it: the tab
  // item flattens this style and hands the `flex` key to the button inside it,
  // which would otherwise inherit the navigator's `flex: 1`, collapse to the
  // width of the wrapper, and draw the icon outside its own pill.
  flex: 0,
  flexGrow: 0,
  flexShrink: 0,
  flexBasis: 'auto',
  borderRadius: 999,
  paddingHorizontal: 6,
  // Vertical padding here would be added on top of the button's own, and the bar
  // is sized for exactly one of them.
  paddingVertical: 0,
};

/**
 * The bar's items are grouped at its trailing edge, and the declarations below
 * are the order they appear in: the first one declared carries the auto margin
 * that swallows the slack in the row, so it is the one furthest left of the
 * group, and the rest follow with a gap between them.
 *
 * A row rather than fixed offsets: offsets would have to know the width of
 * every item, which depends on its label and its language.
 */
const BAR_ITEM_GROUPED: ViewStyle = { marginLeft: 'auto' };
/** The gap between two items in the group. */
const BAR_ITEM_GAP = 6;
/** The gutter between the last item and the edge of the screen. */
const BAR_ITEM_GUTTER = 12;

/**
 * `profile` and `settings` occupy the bar, and `profile` is declared first,
 * which would make it the landing route. The app must still open straight into
 * the game, so the anchor is pinned to `index` explicitly. It is `href: null`
 * below and reachable from the profile menu.
 */
export const unstable_settings = { initialRouteName: 'index' };

export default function PlayerLayout() {
  const t = useTranslate();
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();
  const label = (key: string, fallback: string) => t(key) ?? fallback;

  const { data: unreadData } = useQuery({
    queryKey: ['notifications', 'unread'],
    queryFn: () => notificationsApi.unreadCount(),
    refetchInterval: 30_000,
  });
  const unread = unreadData?.data.count ?? 0;

  // The bar is sized around the top inset (status bar / notch) rather than a
  // home indicator, with a floor under it so it never sits flush against the
  // top edge. It is exactly one item tall, and the navigator reads this height
  // back to push the body below it, so the two cannot disagree.
  const topInset = Math.max(insets.top, BAR_TOP_CLEARANCE);
  const barHeight = BAR_HEIGHT + topInset;

  // The tabs have no native header of their own. The top inset is deliberately
  // left off here: the bar already absorbs it as padding, so applying it twice
  // would push the bar down by a second status-bar height. The bottom inset is
  // still needed, because nothing sits at the bottom of the screen any more.
  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: colors.background }} edges={['bottom', 'left', 'right']}>
      <Tabs
        screenOptions={{
          headerShown: false,
          // Two icons sit in the top bar, so the screen reads like an app
          // header rather than a tab strip. Everything else is reached from the
          // profile menu.
          tabBarPosition: 'top',
          tabBarVariant: 'uikit',
          tabBarActiveTintColor: BRAND,
          tabBarInactiveTintColor: colors.textSecondary,
          // A tinted pill behind the active item, so the current tab is
          // obvious at a glance instead of relying on colour alone.
          tabBarActiveBackgroundColor: BRAND_TINT,
          // Fixed label metrics. Without a line height the label's box is left
          // to the platform, which is what let it grow past the bottom of the
          // bar, and a scaled system font would do the same again — so the bar's
          // labels do not scale with it. The body text below is unaffected.
          tabBarLabelStyle: {
            fontSize: 11,
            lineHeight: BAR_LABEL_LINE,
          },
          tabBarAllowFontScaling: false,
          tabBarStyle: {
            backgroundColor: colors.surface,
            // The bar is at the top, so the divider belongs on its bottom edge.
            borderBottomColor: colors.borderInactive,
            borderBottomWidth: 1,
            borderTopWidth: 0,
            height: barHeight,
            // The inset is inside the height already, so it is padding — not
            // extra height — or the bar would be taller than the space allowed.
            paddingTop: topInset,
            paddingBottom: 0,
          },
          // Every item hugs its icon and sits in a pill. The grouping that puts
          // them at the trailing edge is per item, below.
          tabBarItemStyle: BAR_ITEM_STYLE,
          tabBarBadgeStyle: {
            backgroundColor: colors.danger,
            color: '#FFFFFF',
            fontSize: 10,
            fontWeight: '700',
            minWidth: 16,
            height: 16,
            lineHeight: 15,
          },
          // No keyboard hiding: the bar is at the top now, so it can never
          // cover a field the player is typing into.
        }}
      >
        {/* Declared before the profile icon so the gear sits inboard of it: the
            first item declared is the left one of the group. */}
        <Tabs.Screen
          name="settings"
          options={{
            title: label('mobile.navSettings', 'Settings'),
            tabBarIcon: ({ color }) => <IconSettings color={color} size={BAR_ICON} />,
            tabBarItemStyle: { ...BAR_ITEM_STYLE, ...BAR_ITEM_GROUPED },
          }}
        />
        <Tabs.Screen
          name="profile"
          options={{
            title: label('mobile.navProfile', 'Profile'),
            tabBarIcon: ({ color }) => <IconProfile color={color} size={BAR_ICON} />,
            tabBarItemStyle: {
              ...BAR_ITEM_STYLE,
              marginLeft: BAR_ITEM_GAP,
              marginRight: BAR_ITEM_GUTTER,
            },
            // The badge rides the profile icon now, since alerts moved into the
            // profile menu. Without it an unread notification would be
            // invisible: the screen it lives on is no longer in the bar.
            tabBarBadge: unread > 0 ? unread : undefined,
          }}
        />
        {/* The game board is the main content, not a tab destination. It keeps
            the bar so the player can leave the game, but it declares no tab. */}
        <Tabs.Screen name="index" options={{ href: null }} />
        <Tabs.Screen name="game/[id]" options={{ href: null }} />
        <Tabs.Screen name="history" options={{ href: null }} />
        <Tabs.Screen name="wallet" options={{ href: null }} />
        <Tabs.Screen name="notifications" options={{ href: null }} />
        <Tabs.Screen name="my-games" options={{ href: null }} />
        <Tabs.Screen name="withdraw" options={{ href: null }} />
      </Tabs>
    </SafeAreaView>
  );
}
