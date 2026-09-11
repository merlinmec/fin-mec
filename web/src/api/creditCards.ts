import { api } from "./client";
import type { EntryType } from "./transactions";

/** Espelha com.mecfin.creditcard.api.CreditCardResponse. */
export interface CreditCard {
  id: string;
  name: string;
  creditLimit: number;
  closingDay: number;
  dueDay: number;
  paymentAccountId: string | null;
  archived: boolean;
  createdAt: string;
  updatedAt: string;
}

/** Espelha com.mecfin.creditcard.api.CreateCreditCardRequest. */
export interface CreateCreditCardPayload {
  name: string;
  creditLimit: number;
  closingDay: number;
  dueDay: number;
  paymentAccountId?: string;
}

/** Espelha com.mecfin.creditcard.api.UpdateCreditCardRequest. */
export interface UpdateCreditCardPayload extends CreateCreditCardPayload {
  archived: boolean;
}

/** Espelha com.mecfin.creditcard.domain.CreditCardInvoiceStatus — CLOSED e sempre o status efetivo calculado na leitura, nunca persistido. */
export type CreditCardInvoiceStatus = "OPEN" | "CLOSED" | "PAID";

/** Espelha com.mecfin.creditcard.api.CreditCardChargeResponse. type nunca e TRANSFER aqui (EXPENSE = compra, INCOME = estorno). */
export interface CreditCardCharge {
  id: string;
  creditCardInvoiceId: string;
  categoryId: string | null;
  type: EntryType;
  amount: number;
  description: string;
  purchaseDate: string;
  installmentNumber: number | null;
  installmentTotal: number | null;
}

/** Espelha com.mecfin.creditcard.api.CreditCardInvoiceResponse. totalAmount ja vem calculado (EXPENSE soma, INCOME/estorno subtrai) — nunca recalcular. */
export interface CreditCardInvoice {
  id: string;
  creditCardId: string;
  referenceMonth: string;
  closingDate: string;
  dueDate: string;
  status: CreditCardInvoiceStatus;
  paidTransactionId: string | null;
  totalAmount: number;
  charges: CreditCardCharge[];
}

/** Espelha com.mecfin.creditcard.api.CreateCreditCardChargeRequest — amount e o valor de CADA parcela quando installments e informado. */
export interface CreateChargePayload {
  description: string;
  amount: number;
  type: EntryType;
  categoryId?: string;
  purchaseDate: string;
  installments?: number;
}

/** Espelha com.mecfin.creditcard.api.PayCreditCardInvoiceRequest. */
export interface PayInvoicePayload {
  accountId?: string;
  paymentDate: string;
  paidAmount?: number;
}

export function listCreditCards(): Promise<CreditCard[]> {
  return api.get<CreditCard[]>("/credit-cards");
}

export function createCreditCard(payload: CreateCreditCardPayload): Promise<CreditCard> {
  return api.post<CreditCard>("/credit-cards", payload);
}

export function updateCreditCard(id: string, payload: UpdateCreditCardPayload): Promise<CreditCard> {
  return api.put<CreditCard>(`/credit-cards/${id}`, payload);
}

/** DELETE /credit-cards/{id} — soft delete; faturas ja emitidas nao perdem a referencia. */
export function deleteCreditCard(id: string): Promise<void> {
  return api.del<void>(`/credit-cards/${id}`);
}

/** Fatura resolvida automaticamente a partir de purchaseDate + closingDay do cartao — nunca se escolhe a fatura na hora de lançar. */
export function registerCharge(cardId: string, payload: CreateChargePayload): Promise<CreditCardCharge[]> {
  return api.post<CreditCardCharge[]>(`/credit-cards/${cardId}/charges`, payload);
}

export function listInvoices(cardId: string): Promise<CreditCardInvoice[]> {
  return api.get<CreditCardInvoice[]>(`/credit-cards/${cardId}/invoices`);
}

export function getInvoice(id: string): Promise<CreditCardInvoice> {
  return api.get<CreditCardInvoice>(`/credit-card-invoices/${id}`);
}

export function payInvoice(id: string, payload: PayInvoicePayload): Promise<CreditCardInvoice> {
  return api.post<CreditCardInvoice>(`/credit-card-invoices/${id}/pay`, payload);
}

/** So permitido enquanto a fatura esta OPEN (efetivo) — corrige lançamento errado antes do fechamento. */
export function deleteCharge(invoiceId: string, chargeId: string): Promise<void> {
  return api.del<void>(`/credit-card-invoices/${invoiceId}/charges/${chargeId}`);
}
