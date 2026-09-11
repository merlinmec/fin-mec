import { api } from "./client";

/** Espelha com.mecfin.account.domain.AccountType. */
export type AccountType = "CHECKING" | "SAVINGS" | "WALLET" | "INVESTMENT";

export const ACCOUNT_TYPES = ["CHECKING", "SAVINGS", "WALLET", "INVESTMENT"] as const satisfies readonly AccountType[];

export const ACCOUNT_TYPE_LABELS: Record<AccountType, string> = {
  CHECKING: "Conta corrente",
  SAVINGS: "Poupança",
  WALLET: "Carteira",
  INVESTMENT: "Investimento",
};

/** Espelha com.mecfin.account.api.AccountResponse. */
export interface Account {
  id: string;
  name: string;
  type: AccountType;
  initialBalance: number;
  currency: string;
  archived: boolean;
  createdAt: string;
  updatedAt: string;
}

/** Espelha com.mecfin.account.api.CreateAccountRequest. */
export interface CreateAccountPayload {
  name: string;
  type: AccountType;
  initialBalance: number;
}

/**
 * Espelha com.mecfin.account.api.UpdateAccountRequest — sem initialBalance
 * (o backend nao permite editar o saldo inicial depois de criada a conta).
 */
export interface UpdateAccountPayload {
  name: string;
  type: AccountType;
  archived: boolean;
}

export function listAccounts(): Promise<Account[]> {
  return api.get<Account[]>("/accounts");
}

export function createAccount(payload: CreateAccountPayload): Promise<Account> {
  return api.post<Account>("/accounts", payload);
}

export function updateAccount(id: string, payload: UpdateAccountPayload): Promise<Account> {
  return api.put<Account>(`/accounts/${id}`, payload);
}

/** DELETE /accounts/{id} — soft delete (some da listagem; historico de lancamentos permanece). */
export function deleteAccount(id: string): Promise<void> {
  return api.del<void>(`/accounts/${id}`);
}
