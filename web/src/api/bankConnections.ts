import { api } from "./client";

/** Espelha com.mecfin.bankprovider.domain.BankConnectionStatus (DISCONNECTED nunca é listado). */
export type BankConnectionStatus = "ACTIVE" | "ERROR" | "EXPIRED" | "DISCONNECTED";

/** PENDING = o usuário ainda não decidiu o que fazer com a conta do banco. */
export type BankAccountLinkMode = "PENDING" | "LINKED" | "IGNORED";

export type SetupMode = "CREATE" | "LINK" | "IGNORE";

export interface BankProviderStatus {
  enabled: boolean;
  includeSandbox: boolean;
  provider: string;
}

export interface BankAccountLink {
  id: string;
  name: string;
  number: string | null;
  bankBalance: number | null;
  bankBalanceAt: string | null;
  mode: BankAccountLinkMode;
  accountId: string | null;
}

export interface BankConnection {
  id: string;
  institutionName: string;
  institutionImageUrl: string | null;
  institutionColor: string | null;
  status: BankConnectionStatus;
  lastError: string | null;
  lastSyncedAt: string | null;
  createdAt: string;
  accounts: BankAccountLink[];
}

export interface SyncResult {
  status: BankConnectionStatus;
  created: number;
  matched: number;
  skipped: number;
  message: string | null;
}

export function getBankProviderStatus(): Promise<BankProviderStatus> {
  return api.get<BankProviderStatus>("/bank-connections/status");
}

/** Token de uso único do widget; com connectionId, abre o widget em modo "reconectar". */
export function createConnectToken(
  connectionId?: string,
): Promise<{ accessToken: string; itemId: string | null }> {
  return api.post<{ accessToken: string; itemId: string | null }>(
    "/bank-connections/connect-token",
    {
      connectionId: connectionId ?? null,
    },
  );
}

export function listBankConnections(): Promise<BankConnection[]> {
  return api.get<BankConnection[]>("/bank-connections");
}

export function registerBankConnection(itemId: string): Promise<BankConnection> {
  return api.post<BankConnection>("/bank-connections", { itemId });
}

export function setupBankAccount(
  connectionId: string,
  linkId: string,
  payload: { mode: SetupMode; accountId?: string | null },
): Promise<BankConnection> {
  return api.put<BankConnection>(`/bank-connections/${connectionId}/accounts/${linkId}`, payload);
}

export function syncBankConnection(connectionId: string): Promise<SyncResult> {
  return api.post<SyncResult>(`/bank-connections/${connectionId}/sync`);
}

export function disconnectBank(connectionId: string): Promise<void> {
  return api.del<void>(`/bank-connections/${connectionId}`);
}
