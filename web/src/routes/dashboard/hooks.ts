import { useQuery } from "@tanstack/react-query";
import { getDashboard } from "@/api/dashboard";

export function useDashboard(month: string) {
  return useQuery({ queryKey: ["dashboard", month], queryFn: () => getDashboard(month) });
}
