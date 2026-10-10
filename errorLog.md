npx eas login
npx eas build --platform android --profile preview   # install on device
npx eas update --channel preview --message "test update"


npx eas-cli login
npx eas-cli build --platform android --profile preview
npx eas-cli update --channel preview --message "test update"

CI=1 EXPO_PUBLIC_BACKEND_URL=https://api.nowbingoplus.lol npx eas-cli@latest update --channel preview --message "translate update banner" 2>&1 | tail -15
