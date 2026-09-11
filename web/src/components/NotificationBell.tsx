import { useEffect, useRef } from "react";
import { Bell, RefreshCw } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { useMarkNotificationRead, useNotifications, useSyncNotifications } from "@/hooks/useNotifications";
import { formatDate } from "@/lib/dates";
import { cn } from "@/lib/utils";

/**
 * Sino de notificacoes do topbar (AppShell) — nao ha uma NotificationsPage
 * dedicada. Sincroniza sob demanda: uma vez no boot (o backend nao tem job
 * agendado, ver api/notifications.ts) e via botao manual de refresh.
 */
export function NotificationBell() {
  const { data: notifications } = useNotifications(true);
  const syncNotifications = useSyncNotifications();
  const markRead = useMarkNotificationRead();
  const syncedOnMount = useRef(false);

  useEffect(() => {
    if (!syncedOnMount.current) {
      syncedOnMount.current = true;
      syncNotifications.mutate();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const unread = notifications ?? [];

  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button variant="ghost" size="icon" className="relative" aria-label="Notificações">
          <Bell className="size-4" />
          {unread.length > 0 && (
            <span className="absolute top-0.5 right-0.5 flex size-4 items-center justify-center rounded-full bg-destructive text-[10px] font-medium text-destructive-foreground">
              {unread.length > 9 ? "9+" : unread.length}
            </span>
          )}
        </Button>
      </PopoverTrigger>
      <PopoverContent>
        <div className="flex items-center justify-between border-b border-border px-3 py-2">
          <span className="text-sm font-medium">Notificações</span>
          <Button
            variant="ghost"
            size="icon"
            className="size-7"
            onClick={() => syncNotifications.mutate()}
            disabled={syncNotifications.isPending}
            aria-label="Atualizar notificações"
          >
            <RefreshCw className={cn("size-3.5", syncNotifications.isPending && "animate-spin")} />
          </Button>
        </div>
        <div className="max-h-80 overflow-y-auto">
          {unread.length === 0 ? (
            <p className="px-3 py-6 text-center text-sm text-muted-foreground">Nenhuma notificação pendente.</p>
          ) : (
            unread.map((notification) => (
              <button
                key={notification.id}
                type="button"
                onClick={() => markRead.mutate(notification.id)}
                className="flex w-full flex-col gap-0.5 border-b border-border px-3 py-2 text-left text-sm last:border-0 hover:bg-accent"
              >
                <span>{notification.message}</span>
                <span className="text-xs text-muted-foreground">{formatDate(notification.createdAt.slice(0, 10))}</span>
              </button>
            ))
          )}
        </div>
      </PopoverContent>
    </Popover>
  );
}
