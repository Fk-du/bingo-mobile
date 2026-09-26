/**
 * Prize actually shared by winner(s): collected pot minus the admin commission.
 */
export function netPrize(prizePool: number, commissionPercent?: number | null): number {
  const pct = commissionPercent == null ? 0 : Math.min(Math.max(commissionPercent, 0), 90);
  return Math.round(prizePool * (1 - pct / 100) * 100) / 100;
}