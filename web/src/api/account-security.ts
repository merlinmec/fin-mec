import { api } from "./client";

export type SecurityEventType =
  | "LOGIN_SUCCESS"
  | "LOGIN_FAILURE"
  | "LOGIN_MFA_FAILURE"
  | "ACCOUNT_LOCKED"
  | "PASSWORD_CHANGED"
  | "SESSIONS_REVOKED"
  | "MFA_ENABLED"
  | "MFA_DISABLED"
  | "RECOVERY_CODE_USED"
  | "RECOVERY_CODES_REGENERATED"
  | "PASSWORD_RESET_REQUESTED"
  | "PASSWORD_RESET"
  | "SESSION_ENDED"
  | "HOUSEHOLD_JOINED"
  | "HOUSEHOLD_LEFT"
  | "HOUSEHOLD_REMOVED";

export const SECURITY_EVENT_LABELS: Record<SecurityEventType, string> = {
  LOGIN_SUCCESS: "Login realizado",
  LOGIN_FAILURE: "Tentativa de login com senha errada",
  LOGIN_MFA_FAILURE: "Código de verificação errado no login",
  ACCOUNT_LOCKED: "Conta bloqueada temporariamente",
  PASSWORD_CHANGED: "Senha alterada",
  SESSIONS_REVOKED: "Outras sessões encerradas",
  MFA_ENABLED: "Verificação em duas etapas ativada",
  MFA_DISABLED: "Verificação em duas etapas desativada",
  RECOVERY_CODE_USED: "Código de recuperação usado",
  RECOVERY_CODES_REGENERATED: "Novos códigos de recuperação gerados",
  PASSWORD_RESET_REQUESTED: "Pedido de redefinição de senha por e-mail",
  PASSWORD_RESET: "Senha redefinida pelo link do e-mail",
  SESSION_ENDED: "Sessão encerrada em outro dispositivo",
  HOUSEHOLD_JOINED: "Entrou num household compartilhado",
  HOUSEHOLD_LEFT: "Saiu do household compartilhado",
  HOUSEHOLD_REMOVED: "Removido do household compartilhado pelo dono",
};

/** Eventos que merecem destaque de alerta na lista. */
export const ALERT_EVENTS = new Set<SecurityEventType>([
  "LOGIN_FAILURE",
  "LOGIN_MFA_FAILURE",
  "ACCOUNT_LOCKED",
]);

export interface SecurityOverview {
  mfaEnabled: boolean;
  recoveryCodesRemaining: number;
  passwordChangedAt: string | null;
  accountCreatedAt: string;
}

export interface SecurityEvent {
  id: string;
  type: SecurityEventType;
  ipAddress: string | null;
  userAgent: string | null;
  createdAt: string;
}

export interface MfaSetup {
  secret: string;
  otpauthUri: string;
}

/** Espelha SessionService.SessionInfo — id é uma impressão digital, nunca o id real da sessão. */
export interface ActiveSession {
  id: string;
  device: string;
  ipAddress: string | null;
  createdAt: string;
  lastAccessedAt: string;
  current: boolean;
}

export function listSessions(): Promise<ActiveSession[]> {
  return api.get<ActiveSession[]>("/account/sessions");
}

export function endSession(id: string): Promise<void> {
  return api.del<void>(`/account/sessions/${id}`);
}

export function getSecurityOverview(): Promise<SecurityOverview> {
  return api.get<SecurityOverview>("/account/security");
}

export function getSecurityEvents(): Promise<SecurityEvent[]> {
  return api.get<SecurityEvent[]>("/account/security-events");
}

export function changePassword(currentPassword: string, newPassword: string): Promise<void> {
  return api.post<void>("/account/password", { currentPassword, newPassword });
}

export function revokeOtherSessions(): Promise<void> {
  return api.post<void>("/account/sessions/revoke-others");
}

export function startMfaSetup(): Promise<MfaSetup> {
  return api.post<MfaSetup>("/account/2fa/setup");
}

export function enableMfa(code: string): Promise<{ recoveryCodes: string[] }> {
  return api.post<{ recoveryCodes: string[] }>("/account/2fa/enable", { code });
}

export function disableMfa(password: string, code?: string): Promise<void> {
  return api.post<void>("/account/2fa/disable", { password, code: code || undefined });
}

export function regenerateRecoveryCodes(
  password: string,
  code?: string,
): Promise<{ recoveryCodes: string[] }> {
  return api.post<{ recoveryCodes: string[] }>("/account/2fa/recovery-codes", {
    password,
    code: code || undefined,
  });
}

export function deleteAccount(password: string, code?: string): Promise<void> {
  return api.post<void>("/account/delete", { password, code: code || undefined });
}
