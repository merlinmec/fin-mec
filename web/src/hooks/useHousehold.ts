import { useQuery } from "@tanstack/react-query";
import { getHousehold } from "@/api/household";

export const householdQueryKey = ["household"] as const;

export function useHousehold() {
  return useQuery({ queryKey: householdQueryKey, queryFn: getHousehold, staleTime: 60_000 });
}

/** Rótulo curto de quem lançou: "Você" ou o começo do e-mail. */
export function memberLabel(email: string, you: boolean): string {
  return you ? "Você" : email.split("@")[0];
}
