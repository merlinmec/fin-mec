import { api } from "./client";

/** Espelha com.mecfin.transaction.domain.TransactionType. TRANSFER nunca e criado por aqui (so via createTransfer). */
export type TransactionType = "INCOME" | "EXPENSE" | "TRANSFER";
/** Tipo aceito por POST /transactions e PUT /transactions/{id} — nunca TRANSFER. */
export type EntryType = "INCOME" | "EXPENSE";

export type TransactionStatus = "PENDING" | "POSTED" | "CANCELED";
export type TransactionDirection = "OUT" | "IN";
/** Espelha com.mecfin.shared.domain.RecurrenceRule — so metadado, o backend nao gera ocorrencias futuras. */
export type RecurrenceRule = "WEEKLY" | "BIWEEKLY" | "MONTHLY" | "BIMONTHLY" | "TRIMONTHLY" | "YEARLY";

export const ENTRY_TYPES = ["EXPENSE", "INCOME"] as const satisfies readonly EntryType[];

export const TRANSACTION_TYPE_LABELS: Record<TransactionType, string> = {
  EXPENSE: "Despesa",
  INCOME: "Receita",
  TRANSFER: "Transferência",
};

export const TRANSACTION_STATUS_LABELS: Record<TransactionStatus, string> = {
  PENDING: "Pendente",
  POSTED: "Efetivado",
  CANCELED: "Cancelado",
};

export const RECURRENCE_RULE_LABELS: Record<RecurrenceRule, string> = {
  WEEKLY: "Semanal",
  BIWEEKLY: "Quinzenal",
  MONTHLY: "Mensal",
  BIMONTHLY: "Bimestral",
  TRIMONTHLY: "Trimestral",
  YEARLY: "Anual",
};

export const RECURRENCE_RULES = Object.keys(RECURRENCE_RULE_LABELS) as RecurrenceRule[];

/** Espelha com.mecfin.transaction.api.TransactionResponse. */
export interface Transaction {
  id: string;
  accountId: string;
  categoryId: string | null;
  type: TransactionType;
  amount: number;
  description: string;
  /** LocalDate, formato yyyy-MM-dd — bate com o value de <input type="date">. */
  transactionDate: string;
  /** YearMonth, formato yyyy-MM — bate com o value de <input type="month">. */
  competenceMonth: string;
  status: TransactionStatus;
  transferPairId: string | null;
  transferDirection: TransactionDirection | null;
  installmentNumber: number | null;
  installmentTotal: number | null;
  installmentGroupId: string | null;
  recurrenceRule: RecurrenceRule | null;
  createdAt: string;
  updatedAt: string;
}

/** Espelha com.mecfin.shared.web.PagedResponse. */
export interface PagedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Espelha com.mecfin.transaction.api.CreateTransactionRequest. */
export interface CreateTransactionPayload {
  accountId: string;
  categoryId?: string;
  type: EntryType;
  amount: number;
  description: string;
  transactionDate: string;
  competenceMonth: string;
  status?: TransactionStatus;
  recurrenceRule?: RecurrenceRule;
}

/** Espelha com.mecfin.transaction.api.UpdateTransactionRequest. */
export interface UpdateTransactionPayload {
  categoryId?: string;
  type: EntryType;
  amount: number;
  description: string;
  transactionDate: string;
  competenceMonth: string;
  status: TransactionStatus;
  recurrenceRule?: RecurrenceRule;
}

/** Espelha com.mecfin.transaction.api.CreateTransferRequest. */
export interface CreateTransferPayload {
  sourceAccountId: string;
  destinationAccountId: string;
  amount: number;
  description: string;
  transactionDate: string;
  competenceMonth: string;
  status?: TransactionStatus;
}

/** Espelha com.mecfin.transaction.api.CreateInstallmentRequest — amountPerInstallment e o valor de CADA parcela. */
export interface CreateInstallmentPayload {
  accountId: string;
  categoryId?: string;
  type: EntryType;
  amountPerInstallment: number;
  description: string;
  firstTransactionDate: string;
  firstCompetenceMonth: string;
  installments: number;
}

export interface TransactionSearchParams {
  accountId?: string;
  categoryId?: string;
  type?: TransactionType;
  status?: TransactionStatus;
  competenceMonth?: string;
  page?: number;
  size?: number;
}

function toQueryString(params: object): string {
  const usp = new URLSearchParams();
  for (const [key, value] of Object.entries(params) as [string, string | number | undefined][]) {
    if (value !== undefined && value !== "") {
      usp.set(key, String(value));
    }
  }
  const query = usp.toString();
  return query ? `?${query}` : "";
}

export function searchTransactions(params: TransactionSearchParams): Promise<PagedResponse<Transaction>> {
  return api.get<PagedResponse<Transaction>>(`/transactions${toQueryString(params)}`);
}

export function createTransaction(payload: CreateTransactionPayload): Promise<Transaction> {
  return api.post<Transaction>("/transactions", payload);
}

export function createTransfer(payload: CreateTransferPayload): Promise<Transaction[]> {
  return api.post<Transaction[]>("/transactions/transfers", payload);
}

export function createInstallments(payload: CreateInstallmentPayload): Promise<Transaction[]> {
  return api.post<Transaction[]>("/transactions/installments", payload);
}

export function updateTransaction(id: string, payload: UpdateTransactionPayload): Promise<Transaction> {
  return api.put<Transaction>(`/transactions/${id}`, payload);
}

/** DELETE /transactions/{id} — nunca hard-delete, cancela (estorno); cascata pra perna pareada quando e transferencia. */
export function cancelTransaction(id: string): Promise<void> {
  return api.del<void>(`/transactions/${id}`);
}
