import * as Updates from 'expo-updates';
import { Platform, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useTranslate } from '@/hooks/useTranslate';
import { useTheme } from '@/lib/theme';
import { Button } from './ui';

/**
 * Floating prompt shown when an OTA update (EAS Update) has been downloaded.
 * expo-updates fetches the new bundle in the background on launch by default;
 * without this the user would never know it is pending and it would only apply
 * on some future cold start. Web, Expo Go and dev builds have no update pipeline,
 * so the hook reports no available/pending update and nothing renders.
 */
export function UpdateBanner() {
  const t = useTranslate();
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();
  const { isUpdateAvailable, isUpdatePending } = Updates.useUpdates();

  if (Platform.OS === 'web') return null;
  if (!isUpdateAvailable && !isUpdatePending) return null;

  const installAndRestart = async () => {
    if (!isUpdatePending) {
      await Updates.fetchUpdateAsync();
    }
    await Updates.reloadAsync();
  };

  const message = isUpdatePending
    ? (t('common.updateReady') ?? 'A new version is ready to install')
    : (t('common.updateAvailable') ?? 'A new version is available');

  return (
    <View pointerEvents="box-none" className="absolute left-0 right-0 z-50 px-4" style={{ bottom: insets.bottom + 12 }}>
      <View
        className="flex-row items-center justify-between gap-3 rounded-2xl border px-4 py-3"
        style={{ backgroundColor: colors.elevated, borderColor: colors.borderActive }}
      >
        <Text className="min-w-0 flex-1 text-sm font-semibold" style={{ color: colors.textPrimary }}>
          {message}
        </Text>
        <Button compact onPress={() => void installAndRestart()}>
          {t('common.updateRestart') ?? 'Restart to update'}
        </Button>
      </View>
    </View>
  );
}