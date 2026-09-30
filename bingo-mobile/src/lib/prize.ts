/**
 * A winner's own share of the game's prize. The prize is a total the admin
 * committed to, split equally between everyone who won, so the remainder from
 * a cent-perfect split goes to the first winner.
 */
export function shareOfPrize(prizeAmount: number | null | undefined, winnerCount: number): number {
  if (prizeAmount == null || winnerCount < 1) return 0;
  return Math.round((prizeAmount / winnerCount) * 100) / 100;
}
