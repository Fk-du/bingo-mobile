import { Redirect } from 'expo-router';

/**
 * Entry route. The auth gate in _layout handles redirects — an empty index
 * just resolves the initial path so expo-router mounts a valid screen.
 */
export default function Index() {
  return <Redirect href="/(auth)/login" />;
}