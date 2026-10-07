import { useQuery, useQueryClient } from '@tanstack/react-query';
import { FlatList, Pressable, RefreshControl, Text, View } from 'react-native';
import { notificationsApi } from '@/api';
import { Card, EmptyState, Screen, SectionHeader } from '@/components/ui';
import { useTranslate } from '@/hooks/useTranslate';
import { getClientLocale, translateClientMessage } from '@/lib/clientTranslations';
import { useTheme } from '@/lib/theme';
import { AppNotification, NotificationType } from '@/types';

const TYPE_COLORS: Record<string, string> = {
  [NotificationType.DEPOSIT_REQUEST]: '#8B5E3C',
  [NotificationType.WITHDRAWAL_REQUEST]: '#8B5E3C',
  [NotificationType.MIN_WITHDRAWAL]: '#8B5E3C',
  [NotificationType.MISSING_PAYMENT_SCREENSHOT]: '#8B5E3C',
  [NotificationType.CLAIM_PENDING]: '#d97706',
  [NotificationType.ADMIN_WARNING]: '#d97706',
  [NotificationType.DEPOSIT_APPROVED]: '#6B5BFF',
  [NotificationType.WITHDRAWAL_APPROVED]: '#6B5BFF',
  [NotificationType.PLAYER_FUNDED]: '#6B5BFF',
  [NotificationType.WIN]: '#6B5BFF',
  [NotificationType.COMMISSION_CREDITED]: '#6B5BFF',
  [NotificationType.ADMIN_APPROVED]: '#6B5BFF',
  [NotificationType.ADMIN_RESUMED]: '#6B5BFF',
  [NotificationType.ADMIN_SUSPENDED]: '#FF5C6C',
  [NotificationType.DEPOSIT_REJECTED]: '#FF5C6C',
  [NotificationType.WITHDRAWAL_REJECTED]: '#FF5C6C',
  [NotificationType.ADMIN_REJECTED]: '#FF5C6C',
  [NotificationType.CARD_BANNED]: '#FF5C6C',
  [NotificationType.NEW_PLAYER]: '#6B5BFF',
};

function relativeTime(iso: string, t: (key: string, params?: Record<string, string | number>) => string): string {
  const diff = Date.now() - new Date(iso).getTime();
  const minutes = Math.floor(diff / 60_000);
  if (minutes < 1) return t('notifications.justNow') ?? 'just now';
  if (minutes < 60) return t('notifications.minutesAgo', { count: minutes }) ?? `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return t('notifications.hoursAgo', { count: hours }) ?? `${hours}h ago`;
  return t('notifications.daysAgo', { count: Math.floor(hours / 24) }) ?? `${Math.floor(hours / 24)}d ago`;
}

function localizedTitle(n: AppNotification, t: ReturnType<typeof useTranslate>): string {
  const mapped = t(`notifyTitles.${n.type}`);
  return mapped.startsWith('notifyTitles.') ? (n.title || n.type) : mapped;
}

function localizedBody(n: AppNotification): string {
  if (n.messageKey && n.messageKey.startsWith('notify.')) {
    let params: Record<string, string | number> = {};
    try {
      params = n.messageParams ? (JSON.parse(n.messageParams) as Record<string, string | number>) : {};
    } catch {
      params = {};
    }
    return translateClientMessage(n.messageKey, params) ?? n.body ?? '';
  }
  return n.body ?? '';
}

export default function SuperAdminNotificationsScreen() {
  const { colors } = useTheme();
  const t = useTranslate();
  const qc = useQueryClient();

  const { data, isFetching, refetch } = useQuery({
    queryKey: ['super/notifications'],
    queryFn: () => notificationsApi.list(50),
  });

  const items: AppNotification[] = data?.data ?? [];
  const hasUnread = items.some((n) => !n.readAt);

  const invalidateAll = () => {
    void qc.invalidateQueries({ queryKey: ['super/notifications'] });
    void qc.invalidateQueries({ queryKey: ['notifications'] });
    void qc.invalidateQueries({ queryKey: ['notifications', 'unread'] });
  };

  const markAllRead = async () => {
    try {
      await notificationsApi.markAllRead();
      invalidateAll();
    } catch {
      // ignore
    }
  };

  const onPressItem = async (item: AppNotification) => {
    if (item.readAt) return;
    qc.setQueryData<{ data: AppNotification[] }>(['super/notifications'], (prev) => ({
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
      <SectionHeader
        eyebrow={t('super.nbEyebrow') ?? ''}
        title={t('mobile.notifications') ?? 'Notifications'}
        description={t('super.nbDesc') ?? ''}
        action={
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
          <EmptyState title={t('notifications.empty') ?? 'No notifications yet'} />
        }
        renderItem={({ item }) => {
          const unread = !item.readAt;
          const dot = TYPE_COLORS[item.type] ?? colors.textInactive;
          return (
            <Pressable onPress={() => void onPressItem(item)}>
              <Card className={unread ? '' : 'opacity-60'} style={unread ? { borderColor: colors.borderActive } : undefined}>
                <View className="flex-row items-start gap-2">
                  <View className="flex-1 gap-0.5">
                    <View className="flex-row items-center gap-2">
                      <Text className="font-semibold flex-1" style={{ color: colors.textPrimary }}>
                        {localizedTitle(item, t)}
                      </Text>
                      {unread ? (
                        <View className="flex-row items-center gap-1 rounded-full px-2 py-0.5" style={{ backgroundColor: colors.surfaceAlt, borderColor: colors.borderInactive, borderWidth: 1 }}>
                          <View className="h-1.5 w-1.5 rounded-full" style={{ backgroundColor: dot }} />
                          <Text className="text-[10px] font-bold" style={{ color: colors.textSecondary }}>New</Text>
                        </View>
                      ) : null}
                    </View>
                    <Text className="text-sm" style={{ color: colors.textSecondary }}>{localizedBody(item)}</Text>
                    <Text className="text-xs mt-1" style={{ color: colors.textInactive }}>{relativeTime(item.createdAt, t)}</Text>
                  </View>
                </View>
              </Card>
            </Pressable>
          );
        }}
      />
    </Screen>
  );
}