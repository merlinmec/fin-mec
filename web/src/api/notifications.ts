import { api } from "./client";

/** Espelha com.mecfin.notification.domain.NotificationType. */
export type NotificationType =
  | "BILL_DUE_SOON"
  | "BILL_OVERDUE"
  | "CREDIT_CARD_INVOICE_DUE_SOON"
  | "CREDIT_CARD_INVOICE_OVERDUE";

/** Espelha com.mecfin.notification.domain.NotificationSourceType. */
export type NotificationSourceType = "BILL" | "CREDIT_CARD_INVOICE";

/** Espelha com.mecfin.notification.api.NotificationResponse. */
export interface Notification {
  id: string;
  type: NotificationType;
  sourceType: NotificationSourceType;
  sourceId: string;
  message: string;
  read: boolean;
  readAt: string | null;
  createdAt: string;
}

/**
 * Gera as notificacoes pendentes (contas a pagar/faturas vencendo ou vencidas) e devolve a
 * lista atualizada. Sob demanda (sem job agendado no backend) — chamar no boot do app e em
 * refresh manual, nunca esperar tempo real.
 */
export function syncNotifications(): Promise<Notification[]> {
  return api.post<Notification[]>("/notifications/sync");
}

export function listNotifications(read?: boolean): Promise<Notification[]> {
  const query = read !== undefined ? `?read=${read}` : "";
  return api.get<Notification[]>(`/notifications${query}`);
}

/** Idempotente — marcar uma notificacao ja lida de novo continua 200. */
export function markNotificationRead(id: string): Promise<Notification> {
  return api.post<Notification>(`/notifications/${id}/read`);
}
