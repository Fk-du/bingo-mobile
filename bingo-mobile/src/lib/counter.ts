export function capCount(n: number): string {
  return n > 99 ? '99+' : String(n);
}