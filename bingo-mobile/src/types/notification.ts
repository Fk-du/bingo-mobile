export const NotificationType = {
  DEPOSIT_REQUEST: 'DEPOSIT_REQUEST',
  CLAIM_PENDING: 'CLAIM_PENDING',
  DEPOSIT_APPROVED: 'DEPOSIT_APPROVED',
  DEPOSIT_REJECTED: 'DEPOSIT_REJECTED',
  WITHDRAWAL_REQUEST: 'WITHDRAWAL_REQUEST',
  WITHDRAWAL_APPROVED: 'WITHDRAWAL_APPROVED',
  WITHDRAWAL_REJECTED: 'WITHDRAWAL_REJECTED',
  PLAYER_FUNDED: 'PLAYER_FUNDED',
  WIN: 'WIN',
  COMMISSION_CREDITED: 'COMMISSION_CREDITED',
  ADMIN_APPROVED: 'ADMIN_APPROVED',
  ADMIN_REJECTED: 'ADMIN_REJECTED',
  ADMIN_WARNING: 'ADMIN_WARNING',
  ADMIN_SUSPENDED: 'ADMIN_SUSPENDED',
  ADMIN_RESUMED: 'ADMIN_RESUMED',
  NEW_PLAYER: 'NEW_PLAYER',
  MIN_WITHDRAWAL: 'MIN_WITHDRAWAL',
  MISSING_PAYMENT_SCREENSHOT: 'MISSING_PAYMENT_SCREENSHOT',
  CARD_BANNED: 'CARD_BANNED',
} as const;

export type NotificationTypeValue =
  (typeof NotificationType)[keyof typeof NotificationType];

export interface AppNotification {
  id: number;
  userId: number;
  type: NotificationTypeValue;
  title: string;
  body: string;
  referenceType: string | null;
  referenceId: number | null;
  messageKey: string | null;
  messageParams: string | null;
  readAt: string | null;
  createdAt: string;
}