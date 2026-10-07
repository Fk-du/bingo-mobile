import { cssToReactNativeRuntime } from 'react-native-css-interop/dist/css-to-rn/index.js';
import fs from 'fs';
const css = fs.readFileSync('./src/global.css', 'utf8');
const result = cssToReactNativeRuntime(css, {});
console.log('FLAGS:', JSON.stringify(result.flags));
console.log('ROOT VARIABLES:', JSON.stringify(result.rootVariables, null, 2));
console.log('UNIVERSAL VARIABLES:', JSON.stringify(result.universalVariables, null, 2));
console.log('HAS RULES:', !!result.rules, Object.keys(result.rules || {}).slice(0, 10));