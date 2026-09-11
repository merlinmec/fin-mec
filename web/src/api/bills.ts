import { api } from "./client";
import type { RecurrenceRule } from "./transactions";

/** Espelha com.mecfin.bill.domain.BillStatus. OVERDUE nunca e persistido — o backend ja devolve o status EFETIVO (calculado na leitura), nunca o bruto. */
export type BillStatus = "OPEN" | "PAID" | "OVERDUE" | "CANCELED";

export const BILL_STATUS_LABELS: Record<BillStatus, string> = {
  OPEN: "Em aberto",
  OVERDUE: "Vencida",
  PAID: "Paga",
  CANCELED: "Cancelada",
};

/** Espelha com.mecfin.bill.api.BillResponse. */
export interface Bill {
  id: string;
  description: string;
  amount: number;
  /** LocalDate, yyyy-MM-dd. */
  dueDate: string;
  sourceAccountId: string | null;
  categoryId: string | null;
  status: BillStatus;
  paidTransactionId: string | null;
  recurrenceRule: RecurrenceRule | null;
  createdAt: string;
  updatedAt: string;
}

/** Espelha com.mecfin.bill.api.CreateBillRequest/UpdateBillRequest (mesmo shape). */
export interface BillPayload {
  description: string;
  amount: number;
  dueDate: string;
  sourceAccountId?: string;
  categoryId?: string;
  recurrenceRule?: RecurrenceRule;
}

/** Espelha com.mecfin.bill.api.PayBillRequest. */
export interface PayBillPayload {
  accountId?: string;
  paymentDate: string;
  paidAmount?: number;
}

export function listBills(status?: BillStatus): Promise<Bill[]> {
  const query = status ? `?status=${status}` : "";
  return api.get<Bill[]>(`/bills${query}`);
}

export function createBill(payload: BillPayload): Promise<Bill> {
  return api.post<Bill>("/bills", payload);
}

export function updateBill(id: string, payload: BillPayload): Promise<Bill> {
  return api.put<Bill>(`/bills/${id}`, payload);
}

/** Baixa: cria a Transaction real (EXPENSE, POSTED) e liga bill.paidTransactionId. */
export function payBill(id: string, payload: PayBillPayload): Promise<Bill> {
  return api.post<Bill>(`/bills/${id}/pay`, payload);
}

/** DELETE /bills/{id} — nunca hard-delete, cancela; so permitido enquanto OPEN/OVERDUE. */
export function cancelBill(id: string): Promise<void> {
  return api.del<void>(`/bills/${id}`);
}
