import { api } from "./client";

/** Espelha com.mecfin.household.domain.HouseholdRole. */
export type HouseholdRole = "OWNER" | "MEMBER";

export interface HouseholdMember {
  userId: string;
  email: string;
  role: HouseholdRole;
  joinedAt: string;
  you: boolean;
}

export interface HouseholdInvite {
  id: string;
  email: string;
  createdAt: string;
  expiresAt: string;
}

/** Espelha HouseholdSharingService.Overview — convites só vêm para o dono. */
export interface HouseholdOverview {
  id: string;
  name: string;
  myRole: HouseholdRole;
  members: HouseholdMember[];
  invites: HouseholdInvite[];
}

export interface InviteCreated {
  invite: HouseholdInvite;
  /** Link de aceite: só aparece nesta resposta (o servidor guarda só o hash). */
  acceptUrl: string;
}

export interface InvitePreview {
  householdName: string;
  invitedByEmail: string | null;
  memberCount: number;
  emailMatches: boolean;
  alreadyMember: boolean;
  mustLeaveCurrent: boolean;
  hasPersonalData: boolean;
}

export const MAX_HOUSEHOLD_MEMBERS = 6;

export function getHousehold(): Promise<HouseholdOverview> {
  return api.get<HouseholdOverview>("/household");
}

export function renameHousehold(name: string): Promise<HouseholdOverview> {
  return api.put<HouseholdOverview>("/household", { name });
}

export function inviteToHousehold(email: string): Promise<InviteCreated> {
  return api.post<InviteCreated>("/household/invites", { email });
}

export function revokeInvite(id: string): Promise<void> {
  return api.del<void>(`/household/invites/${id}`);
}

export function previewInvite(token: string): Promise<InvitePreview> {
  return api.post<InvitePreview>("/household/invites/preview", { token });
}

export function acceptInvite(
  token: string,
  discardPersonalData: boolean,
): Promise<HouseholdOverview> {
  return api.post<HouseholdOverview>("/household/invites/accept", { token, discardPersonalData });
}

export function leaveHousehold(): Promise<HouseholdOverview> {
  return api.post<HouseholdOverview>("/household/leave");
}

export function removeMember(userId: string): Promise<void> {
  return api.del<void>(`/household/members/${userId}`);
}

export function transferOwnership(userId: string): Promise<HouseholdOverview> {
  return api.post<HouseholdOverview>(`/household/members/${userId}/owner`);
}
