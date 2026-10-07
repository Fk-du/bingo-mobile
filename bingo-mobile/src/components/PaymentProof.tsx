import { Image } from 'expo-image';
import { useState } from 'react';
import { Pressable, Text, View } from 'react-native';
import { Modal } from '@/components/ui';
import { getApiBaseUrl } from '@/lib/backend';
import { useTranslate } from '@/hooks/useTranslate';
import { useAuthStore } from '@/store/auth.store';
import { useTheme } from '@/lib/theme';

/** A proof screenshot. Resolves API-relative URLs to an absolute backend URL. */
export function PaymentProof({
  url,
  size = 64,
}: {
  url: string | null | undefined;
  size?: number;
}) {
  const t = useTranslate();
  const { colors } = useTheme();
  const [zoom, setZoom] = useState(false);
  const token = useAuthStore((s) => s.token);

  if (!url) return null;
  const resolved = url.startsWith('/') ? `${getApiBaseUrl().replace(/\/api\/v1$/, '')}${url}` : url;
  const source = token ? { uri: resolved, headers: { Authorization: `Bearer ${token}` } } : { uri: resolved };
  return (
    <>
      <Pressable onPress={() => setZoom(true)} className="active:opacity-80">
        <Image
          source={source}
          style={{ width: size, height: size, borderRadius: 14 }}
          contentFit="cover"
          transition={150}
        />
      </Pressable>
      {zoom && (
        <Modal onClose={() => setZoom(false)}>
          <Pressable onPress={() => setZoom(false)} className="active:opacity-80">
            <Image
              source={source}
              contentFit="contain"
              style={{ width: '100%', height: 420, borderRadius: 16 }}
            />
            <View className="items-center mt-3">
              <View className="rounded-full px-4 py-2" style={{ borderColor: colors.borderInactive, backgroundColor: colors.surfaceAlt }}>
                <Text className="text-sm" style={{ color: colors.textSecondary }}>{t('game.close') ?? 'Close'}</Text>
              </View>
            </View>
          </Pressable>
        </Modal>
      )}
    </>
  );
}