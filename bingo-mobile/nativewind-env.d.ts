/// <reference types="nativewind/types" />

declare module '*.css' {
  const classes: { [key: string]: string };
  export default classes;
}