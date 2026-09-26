import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, FlatList, Pressable, RefreshControl, Text, View } from 'react-native';
import { useFocusEffect } from 'expo-router';
import { useCallback } from 'react';
import { notificationsApi } from '@/api';
import { Card, Screen, ScreenHeader } from '@/components/ui';
import { useTranslate } from '@/hooks/useTranslate';
import { getClientLocale } from '@/lib/clientTranslations';
import { AppNotification } from '@/types';

function relativeTime(iso: string, t: (key: string, params?: Record<string, string | number>) => string): string {
  const diff = Date.now() - new Date(iso).getTime();
  const minutes = Math.floor(diff / 60_000);
  if (minutes < 1) return t('notifications.justNow') ?? 'just now';
  if (minutes < 60) return t('notifications.minutesAgo', { count: minutes }) ?? `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return t('notifications.hoursAgo', { count: hours }) ?? `${hours}h ago`;
  const days = Math.floor(hours / 24);
  return t('notifications.daysAgo', { count: days }) ?? `${days}d ago`;
}

export default function NotificationsScreen() {
  const t = useTranslate();
  const qc = useQueryClient();

  const { data, isFetching, refetch } = useQuery({
    queryKey: ['notifications'],
    queryFn: () => notificationsApi.list(50),
  });

  const items: AppNotification[] = data?.data ?? [];
  const hasUnread = items.some((n) => !n.readAt);

  const invalidateAll = useCallback(() => {
    void qc.invalidateQueries({ queryKey: ['notifications'] });
    void qc.invalidateQueries({ queryKey: ['notifications', 'unread'] });
  }, [qc]);

  useFocusEffect(
    useCallback(() => {
      invalidateAll();
    }, [invalidateAll])
  );

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
    // Optimistic local update so the row dims immediately.
    qc.setQueryData<{ data: AppNotification[] }>(['notifications'], (prev) => ({
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
            <Pressable onPress={markAllRead} hitSlop={8}>
              <Text className="text-bp-primary text-sm font-semibold">
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
          <RefreshControl refreshing={isFetching} onRefresh={() => refetch()} tintColor="#6B5BFF" />
        }
        contentContainerClassName="gap-3 pb-8"
        ListEmptyComponent={
          <Card>
            <Text className="text-bp-textSecondary text-center">
              {t('mobile.noNotifications') ?? 'No notifications yet'}
            </Text>
          </Card>
        }
        renderItem={({ item }) => (
          <Pressable onPress={() => onPressItem(item)}>
            <Card className={item.readAt ? 'opacity-60' : ''}>
              <View className="flex-row items-start gap-2">
                {!item.readAt ? <View className="h-2 w-2 rounded-full bg-bp-primary mt-1.5" /> : null}
                <View className="flex-1 gap-0.5">
                  <Text className="text-bp-textPrimary font-semibold">{item.title}</Text>
                  <Text className="text-bp-textSecondary text-sm">{item.body}</Text>
                  <Text className="text-bp-textInactive text-xs mt-1">
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