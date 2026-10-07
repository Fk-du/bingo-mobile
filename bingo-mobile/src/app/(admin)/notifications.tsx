import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, FlatList, Pressable, RefreshControl, Text, View } from 'react-native';
import { notificationsApi } from '@/api';
import { Card, Screen, ScreenHeader } from '@/components/ui';
import { useTranslate } from '@/hooks/useTranslate';
import { getClientLocale } from '@/lib/clientTranslations';
import { useTheme } from '@/lib/theme';
import { AppNotification } from '@/types';

function relativeTime(iso: string, t: (key: string, params?: Record<string, string | number>) => string): string {
  const diff = Date.now() - new Date(iso).getTime();
  const minutes = Math.floor(diff / 60_000);
  if (minutes < 1) return t('notifications.justNow') ?? 'just now';
  if (minutes < 60) return t('notifications.minutesAgo', { count: minutes }) ?? `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return t('notifications.hoursAgo', { count: hours }) ?? `${hours}h ago`;
  return t('notifications.daysAgo', { count: hours / 24 }) ?? `${Math.floor(hours / 24)}d ago`;
}

export default function AdminNotificationsScreen() {
  const t = useTranslate();
  const { colors } = useTheme();
  const qc = useQueryClient();

  const { data, isFetching, refetch } = useQuery({
    queryKey: ['admin/notifications'],
    queryFn: () => notificationsApi.list(50),
  });

  const items: AppNotification[] = data?.data ?? [];
  const hasUnread = items.some((n) => !n.readAt);

  const invalidateAll = () => {
    void qc.invalidateQueries({ queryKey: ['admin/notifications'] });
    void qc.invalidateQueries({ queryKey: ['notifications'] });
    void qc.invalidateQueries({ queryKey: ['notifications', 'unread'] });
  };

  const markAllRead = async () => {
    try {
      await notificationsApi.markAllRead();
      invalidateAll();
    } catch {
      Alert.alert(t('common.error') ?? 'Error');
    }
  };

  const onPressItem = async (item: AppNotification) => {
    if (item.readAt) return;
    qc.setQueryData<{ data: AppNotification[] }>(['admin/notifications'], (prev) => ({
      data: (prev?.data ?? []).map((n) =>
        n.id === item.id ? { ...n, readAt: new Date().toISOString() } : n
      ),
    }));
    try {
      await notificationsApi.markRead(item.id);
    } finally {
      invalidateAll();
    }
  };

  return (
    <Screen>
      <ScreenHeader
        title={t('mobile.notifications') ?? 'Notifications'}
        right={
          hasUnread ? (
            <Pressable onPress={() => void markAllRead()} hitSlop={8}>
              <Text className="text-sm font-semibold" style={{ color: colors.primary }}>
                {t('notifications.markAllRead') ?? 'Mark all as read'}
              </Text>
            </Pressable>
          ) : null
        }
      />
      <FlatList
        data={items}
        extraData={getClientLocale()}
        keyExtractor={(n) => String(n.id)}
        refreshControl={
          <RefreshControl refreshing={isFetching} onRefresh={() => void refetch()} tintColor="#6B5BFF" />
        }
        contentContainerClassName="gap-3 pb-8"
        ListEmptyComponent={
          <Card>
            <Text className="text-center" style={{ color: colors.textSecondary }}>
              {t('mobile.noNotifications') ?? 'No notifications yet'}
            </Text>
          </Card>
        }
        renderItem={({ item }) => (
          <Pressable onPress={() => void onPressItem(item)}>
            <Card className={item.readAt ? 'opacity-60' : ''}>
              <View className="flex-row items-start gap-2">
                {!item.readAt ? <View className="h-2 w-2 rounded-full mt-1.5" style={{ backgroundColor: colors.primary }} /> : null}
                <View className="flex-1 gap-0.5">
                  <Text className="font-semibold" style={{ color: colors.textPrimary }}>{item.title}</Text>
                  <Text className="text-sm" style={{ color: colors.textSecondary }}>{item.body}</Text>
                  <Text className="text-xs mt-1" style={{ color: colors.textInactive }}>
                    {relativeTime(item.createdAt, t)}
                  </Text>
                </View>
              </View>
            </Card>
          </Pressable>
        )}
      />
    </Screen>
  );
}