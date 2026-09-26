import { Image } from 'expo-image';
import { useState } from 'react';
import { Pressable, Text, View } from 'react-native';
import { Modal } from '@/components/ui';
import { getApiBaseUrl } from '@/lib/backend';
import { useTranslate } from '@/hooks/useTranslate';
import { useAuthStore } from '@/store/auth.store';

/** A proof screenshot. Resolves API-relative URLs to an absolute backend URL. */
export function PaymentProof({
  url,
  size = 64,
}: {
  url: string | null | undefined;
  size?: number;
}) {
  const t = useTranslate();
  const [zoom, setZoom] = useState(false);
  // The screenshots endpoint is authenticated but <Image> sends no auth
  // header, so every proof used to 403 and render blank. The token is read
  // from the store (persisted, so it rehydrates on its own); when it lands the
  // source changes and expo-image refetches with the header attached.
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
              <View className="bg-bp-surfaceAlt border border-bp-borderInactive rounded-full px-4 py-2">
                <Text className="text-bp-textSecondary text-sm">{t('game.close') ?? 'Close'}</Text>
              </View>
            </View>
          </Pressable>
        </Modal>
      )}
    </>
  );
}