import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { listNotifications, markNotificationRead, syncNotifications } from "@/api/notifications";
import { getErrorMessage } from "@/lib/errors";

/**
 * Em hooks/ (nao routes/) porque o sino de notificacoes mora no AppShell, nao
 * numa tela de dominio propria — nao ha uma "NotificationsPage".
 */
const notificationsKey = ["notifications"] as const;

export function useNotifications(unreadOnly?: boolean) {
  return useQuery({
    queryKey: [...notificationsKey, unreadOnly ?? "all"],
    queryFn: () => listNotifications(unreadOnly ? false : undefined),
  });
}

export function useSyncNotifications() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: syncNotifications,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: notificationsKey });
    },
    onError: (err) => toast.error(getErrorMessage(err, "Não foi possível atualizar as notificações.")),
  });
}

export function useMarkNotificationRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => markNotificationRead(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: notificationsKey });
    },
    onError: (err) => toast.error(getErrorMessage(err, "Não foi possível marcar como lida.")),
  });
}
