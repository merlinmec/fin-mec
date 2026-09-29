import { useState } from "react";
import { Link } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { PluggyConnect } from "react-pluggy-connect";
import {
  AlertTriangle,
  ArrowRight,
  Building2,
  CheckCircle2,
  EyeOff,
  Landmark,
  Link2,
  Plus,
  PlugZap,
  RefreshCw,
  ShieldCheck,
  Unplug,
} from "lucide-react";
import { toast } from "sonner";
import {
  createConnectToken,
  disconnectBank,
  getBankProviderStatus,
  listBankConnections,
  registerBankConnection,
  setupBankAccount,
  syncBankConnection,
  type BankAccountLink,
  type BankConnection,
  type BankConnectionStatus,
  type SetupMode,
} from "@/api/bankConnections";
import { AccountSelect } from "@/components/AccountSelect";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { PageHeader, Panel } from "@/components/ui/panel";
import { Skeleton } from "@/components/ui/skeleton";
import { invalidateFinancialViews } from "@/hooks/useFeatureData";
import { getErrorMessage } from "@/lib/errors";
import { formatMoney } from "@/lib/money";
import { useTheme } from "@/lib/theme";
import { cn } from "@/lib/utils";

/**
 * Fase 16 — Open Finance. O usuário conecta o banco pelo widget da Pluggy (as
 * credenciais nunca passam pelo fin-mec), escolhe o destino de cada conta e a
 * partir daí os lançamentos chegam sozinhos: webhook + sincronização diária,
 * pelo mesmo motor de importação da Fase 15 (sem duplicar, efetivando previstos).
 */

const connectionsKey = ["bank-connections"] as const;

const STATUS_BADGE: Record<BankConnectionStatus, { label: string; className: string }> = {
  ACTIVE: { label: "Conectado", className: "bg-primary/10 text-primary" },
  ERROR: { label: "Com erro", className: "bg-warning/15 text-warning" },
  EXPIRED: { label: "Reconectar", className: "bg-destructive/10 text-destructive" },
  DISCONNECTED: { label: "Desconectado", className: "bg-muted text-muted-foreground" },
};

const relative = new Intl.RelativeTimeFormat("pt-BR", { numeric: "auto" });

function timeAgo(iso: string): string {
  const minutes = Math.round((new Date(iso).getTime() - Date.now()) / 60_000);
  if (Math.abs(minutes) < 60) return relative.format(minutes, "minute");
  const hours = Math.round(minutes / 60);
  if (Math.abs(hours) < 24) return relative.format(hours, "hour");
  return relative.format(Math.round(hours / 24), "day");
}

interface WidgetState {
  token: string;
  /** Item da Pluggy sendo reconectado (widget em modo "atualizar credenciais"). */
  updateItem?: string;
}

export function BankConnectionsPage() {
  const queryClient = useQueryClient();
  const { resolved: theme } = useTheme();
  const status = useQuery({
    queryKey: ["bank-connections", "status"],
    queryFn: getBankProviderStatus,
  });
  const connections = useQuery({
    queryKey: connectionsKey,
    queryFn: listBankConnections,
    enabled: status.data?.enabled === true,
  });
  const [widget, setWidget] = useState<WidgetState | null>(null);
  const [setupTarget, setSetupTarget] = useState<{
    connection: BankConnection;
    link: BankAccountLink;
  } | null>(null);

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: connectionsKey });
    invalidateFinancialViews(queryClient);
  };

  const openWidget = useMutation({
    mutationFn: (connectionId?: string) => createConnectToken(connectionId),
    onSuccess: ({ accessToken, itemId }) =>
      setWidget({ token: accessToken, updateItem: itemId ?? undefined }),
    onError: (err) =>
      toast.error(getErrorMessage(err, "Não foi possível abrir a conexão bancária.")),
  });

  const register = useMutation({
    mutationFn: (itemId: string) => registerBankConnection(itemId),
    onSuccess: (connection) => {
      refresh();
      const pending = connection.accounts.find((a) => a.mode === "PENDING");
      if (pending) {
        setSetupTarget({ connection, link: pending });
      } else {
        toast.success(`${connection.institutionName} reconectado.`);
      }
    },
    onError: (err) => toast.error(getErrorMessage(err, "Não foi possível registrar o banco.")),
  });

  const enabled = status.data?.enabled;
  const list = connections.data ?? [];

  return (
    <div className="space-y-5">
      <PageHeader
        title="Bancos conectados"
        description="Conecte sua conta pelo Open Finance e os lançamentos entram sozinhos — sem digitar, sem duplicar."
        actions={
          enabled && (
            <Button onClick={() => openWidget.mutate(undefined)} disabled={openWidget.isPending}>
              {openWidget.isPending ? (
                <RefreshCw className="size-4 animate-spin" />
              ) : (
                <Plus className="size-4" />
              )}
              Conectar banco
            </Button>
          )
        }
      />

      {status.isLoading ? (
        <Skeleton className="h-40 rounded-2xl" />
      ) : !enabled ? (
        <DisabledCard />
      ) : connections.isLoading ? (
        <div className="grid gap-4 lg:grid-cols-2">
          <Skeleton className="h-56 rounded-2xl" />
          <Skeleton className="h-56 rounded-2xl" />
        </div>
      ) : list.length === 0 ? (
        <FirstConnection
          busy={openWidget.isPending || register.isPending}
          onConnect={() => openWidget.mutate(undefined)}
        />
      ) : (
        <div className="grid gap-4 lg:grid-cols-2">
          {list.map((connection) => (
            <ConnectionCard
              key={connection.id}
              connection={connection}
              onChanged={refresh}
              onReconnect={() => openWidget.mutate(connection.id)}
              onSetup={(link) => setSetupTarget({ connection, link })}
            />
          ))}
        </div>
      )}

      {enabled && <HowItWorks />}

      {widget && (
        <PluggyConnect
          connectToken={widget.token}
          includeSandbox={status.data?.includeSandbox}
          updateItem={widget.updateItem}
          language="pt"
          theme={theme}
          onSuccess={({ item }) => {
            setWidget(null);
            register.mutate(item.id);
          }}
          onError={(error) => {
            toast.error(error.message || "A conexão com o banco não foi concluída.");
          }}
          onClose={() => setWidget(null)}
          onLoadError={() => {
            setWidget(null);
            toast.error("Não foi possível carregar o conector do banco. Tente de novo.");
          }}
        />
      )}

      <SetupDialog
        target={setupTarget}
        onClose={() => setSetupTarget(null)}
        onDone={(connection) => {
          refresh();
          const next = connection.accounts.find((a) => a.mode === "PENDING");
          setSetupTarget(next ? { connection, link: next } : null);
        }}
      />
    </div>
  );
}

function DisabledCard() {
  return (
    <Panel>
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start">
        <span className="flex size-12 shrink-0 items-center justify-center rounded-2xl bg-muted text-muted-foreground">
          <PlugZap className="size-6" />
        </span>
        <div className="space-y-2">
          <h2 className="font-semibold">Conexão bancária ainda não ativada neste servidor</h2>
          <p className="max-w-2xl text-sm text-muted-foreground">
            O fin-mec usa a Pluggy, agregadora autorizada do Open Finance Brasil. Para ativar, quem
            administra o servidor cria uma conta em dashboard.pluggy.ai e define{" "}
            <code className="rounded bg-muted px-1 py-0.5 text-xs">PLUGGY_CLIENT_ID</code> e{" "}
            <code className="rounded bg-muted px-1 py-0.5 text-xs">PLUGGY_CLIENT_SECRET</code>.
          </p>
          <p className="text-sm text-muted-foreground">
            Enquanto isso, dá para trazer o extrato em OFX ou CSV:
          </p>
          <Link
            to="/importar"
            className="inline-flex items-center gap-1 text-sm font-medium text-primary hover:underline"
          >
            Importar extrato <ArrowRight className="size-4" />
          </Link>
        </div>
      </div>
    </Panel>
  );
}

function FirstConnection({ busy, onConnect }: { busy: boolean; onConnect: () => void }) {
  return (
    <Panel>
      <div className="flex flex-col items-center gap-4 px-4 py-8 text-center">
        <span className="flex size-14 items-center justify-center rounded-full bg-primary/10 text-primary">
          <Landmark className="size-7" />
        </span>
        <div className="space-y-1">
          <h2 className="text-lg font-semibold">Nenhum banco conectado</h2>
          <p className="max-w-md text-sm text-muted-foreground">
            Você entra no seu banco dentro da janela segura da Pluggy e autoriza só a leitura. O
            fin-mec nunca vê sua senha e não consegue movimentar dinheiro.
          </p>
        </div>
        <Button onClick={onConnect} disabled={busy}>
          {busy ? <RefreshCw className="size-4 animate-spin" /> : <Plus className="size-4" />}
          Conectar meu banco
        </Button>
      </div>
    </Panel>
  );
}

function InstitutionLogo({ connection }: { connection: BankConnection }) {
  const [broken, setBroken] = useState(false);
  return (
    <span
      className="flex size-11 shrink-0 items-center justify-center overflow-hidden rounded-xl border border-border/60 bg-white"
      style={
        connection.institutionColor
          ? { boxShadow: `inset 0 -3px 0 ${connection.institutionColor}` }
          : undefined
      }
    >
      {connection.institutionImageUrl && !broken ? (
        <img
          src={connection.institutionImageUrl}
          alt=""
          className="size-8 object-contain"
          onError={() => setBroken(true)}
        />
      ) : (
        <Building2 className="size-5 text-muted-foreground" />
      )}
    </span>
  );
}

function ConnectionCard({
  connection,
  onChanged,
  onReconnect,
  onSetup,
}: {
  connection: BankConnection;
  onChanged: () => void;
  onReconnect: () => void;
  onSetup: (link: BankAccountLink) => void;
}) {
  const [confirmDisconnect, setConfirmDisconnect] = useState(false);
  const badge = STATUS_BADGE[connection.status];

  const sync = useMutation({
    mutationFn: () => syncBankConnection(connection.id),
    onSuccess: (result) => {
      onChanged();
      if (result.status === "ACTIVE") {
        const parts = [
          result.created && `${result.created} novo${result.created > 1 ? "s" : ""}`,
          result.matched &&
            `${result.matched} previsto${result.matched > 1 ? "s" : ""} efetivado${result.matched > 1 ? "s" : ""}`,
        ].filter(Boolean);
        toast.success(
          parts.length ? `Sincronizado: ${parts.join(", ")}.` : "Tudo em dia — nada novo no banco.",
        );
      } else {
        toast.error(result.message ?? "O banco não respondeu como esperado.");
      }
    },
    onError: (err) => toast.error(getErrorMessage(err, "Não foi possível sincronizar agora.")),
  });

  const disconnect = useMutation({
    mutationFn: () => disconnectBank(connection.id),
    onSuccess: () => {
      setConfirmDisconnect(false);
      onChanged();
      toast.success(
        `${connection.institutionName} desconectado. Os lançamentos já importados ficam.`,
      );
    },
    onError: (err) => toast.error(getErrorMessage(err, "Não foi possível desconectar.")),
  });

  const needsReconnect = connection.status === "EXPIRED";

  return (
    <Panel bodyClassName="pt-5">
      <div className="flex items-start gap-3">
        <InstitutionLogo connection={connection} />
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <h2 className="truncate font-semibold">{connection.institutionName}</h2>
            <span
              className={cn(
                "rounded-full px-2 py-0.5 text-[0.6875rem] font-semibold",
                badge.className,
              )}
            >
              {badge.label}
            </span>
          </div>
          <p className="mt-0.5 text-xs text-muted-foreground">
            {connection.lastSyncedAt
              ? `Última sincronização ${timeAgo(connection.lastSyncedAt)}`
              : "Ainda não sincronizado"}
          </p>
        </div>
      </div>

      {connection.lastError && (
        <p
          role="alert"
          className={cn(
            "mt-3 flex items-start gap-2 rounded-lg px-3 py-2 text-sm",
            needsReconnect ? "bg-destructive/10 text-destructive" : "bg-warning/12 text-warning",
          )}
        >
          <AlertTriangle className="mt-0.5 size-4 shrink-0" />
          {connection.lastError}
        </p>
      )}

      <ul className="mt-4 divide-y divide-border/60 rounded-xl border border-border/60">
        {connection.accounts.map((link) => (
          <li key={link.id} className="flex items-center gap-3 px-3 py-2.5">
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium">{link.name}</p>
              <p className="text-xs text-muted-foreground">
                {link.number ?? "—"}
                {link.mode === "LINKED" && " · recebendo lançamentos"}
                {link.mode === "IGNORED" && " · ignorada"}
              </p>
            </div>
            {link.bankBalance != null && (
              <span className="text-sm font-semibold tabular-nums">
                {formatMoney(link.bankBalance)}
              </span>
            )}
            {link.mode === "PENDING" ? (
              <Button size="sm" onClick={() => onSetup(link)}>
                Configurar
              </Button>
            ) : (
              <Button
                size="sm"
                variant="ghost"
                onClick={() => onSetup(link)}
                aria-label={`Alterar destino de ${link.name}`}
              >
                {link.mode === "LINKED" ? (
                  <CheckCircle2 className="size-4 text-primary" />
                ) : (
                  <EyeOff className="size-4 text-muted-foreground" />
                )}
              </Button>
            )}
          </li>
        ))}
        {connection.accounts.length === 0 && (
          <li className="px-3 py-3 text-sm text-muted-foreground">
            O banco ainda não informou as contas. Sincronize em instantes.
          </li>
        )}
      </ul>

      <div className="mt-4 flex flex-wrap gap-2">
        {needsReconnect ? (
          <Button size="sm" onClick={onReconnect}>
            <Link2 className="size-4" /> Reconectar
          </Button>
        ) : (
          <Button
            size="sm"
            variant="outline"
            onClick={() => sync.mutate()}
            disabled={sync.isPending}
          >
            <RefreshCw className={cn("size-4", sync.isPending && "animate-spin")} />
            {sync.isPending ? "Sincronizando…" : "Sincronizar agora"}
          </Button>
        )}
        <Button
          size="sm"
          variant="ghost"
          className="text-muted-foreground"
          onClick={() => setConfirmDisconnect(true)}
        >
          <Unplug className="size-4" /> Desconectar
        </Button>
      </div>

      <Dialog open={confirmDisconnect} onOpenChange={setConfirmDisconnect}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Desconectar {connection.institutionName}?</DialogTitle>
            <DialogDescription>
              A autorização é revogada na Pluggy e novos lançamentos param de chegar. O que já foi
              importado continua no fin-mec — dá para desfazer lotes em Importar extrato.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setConfirmDisconnect(false)}>
              Cancelar
            </Button>
            <Button
              variant="destructive"
              onClick={() => disconnect.mutate()}
              disabled={disconnect.isPending}
            >
              Desconectar
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Panel>
  );
}

const SETUP_OPTIONS: { mode: SetupMode; title: string; description: string }[] = [
  {
    mode: "CREATE",
    title: "Criar uma conta nova no fin-mec",
    description: "Começa com o saldo do banco e recebe os lançamentos dos últimos 90 dias.",
  },
  {
    mode: "LINK",
    title: "Usar uma conta que já existe",
    description: "Lançamentos iguais aos que você já digitou não são duplicados.",
  },
  {
    mode: "IGNORE",
    title: "Ignorar esta conta",
    description: "Nada desta conta entra no fin-mec. Dá para mudar depois.",
  },
];

function SetupDialog({
  target,
  onClose,
  onDone,
}: {
  target: { connection: BankConnection; link: BankAccountLink } | null;
  onClose: () => void;
  onDone: (connection: BankConnection) => void;
}) {
  const [mode, setMode] = useState<SetupMode>("CREATE");
  const [accountId, setAccountId] = useState<string | undefined>(undefined);
  const [lastLinkId, setLastLinkId] = useState<string | null>(null);

  // Reinicia a escolha quando o diálogo passa para outra conta do banco.
  if (target && target.link.id !== lastLinkId) {
    setLastLinkId(target.link.id);
    setMode(
      target.link.mode === "LINKED" ? "LINK" : target.link.mode === "IGNORED" ? "IGNORE" : "CREATE",
    );
    setAccountId(target.link.accountId ?? undefined);
  }

  const save = useMutation({
    mutationFn: () =>
      setupBankAccount(target!.connection.id, target!.link.id, {
        mode,
        accountId: mode === "LINK" ? accountId : null,
      }),
    onSuccess: (connection) => {
      toast.success(
        mode === "IGNORE"
          ? `${target!.link.name} ignorada.`
          : `${target!.link.name} pronta — sincronize para trazer os lançamentos.`,
      );
      onDone(connection);
    },
    onError: (err) => toast.error(getErrorMessage(err, "Não foi possível salvar.")),
  });

  const invalid = mode === "LINK" && !accountId;

  return (
    <Dialog open={target != null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Para onde vão os lançamentos de “{target?.link.name}”?</DialogTitle>
          <DialogDescription>
            {target?.connection.institutionName}
            {target?.link.bankBalance != null &&
              ` · saldo no banco ${formatMoney(target.link.bankBalance)}`}
          </DialogDescription>
        </DialogHeader>
        <fieldset className="space-y-2">
          <legend className="sr-only">Destino da conta</legend>
          {SETUP_OPTIONS.map((option) => (
            <label
              key={option.mode}
              className={cn(
                "flex cursor-pointer gap-3 rounded-xl border px-3 py-2.5 transition-colors",
                mode === option.mode
                  ? "border-primary bg-primary/6"
                  : "border-border hover:bg-muted/50",
              )}
            >
              <input
                type="radio"
                name="setup-mode"
                className="mt-1 accent-primary"
                checked={mode === option.mode}
                onChange={() => setMode(option.mode)}
              />
              <span>
                <span className="block text-sm font-medium">{option.title}</span>
                <span className="block text-xs text-muted-foreground">{option.description}</span>
              </span>
            </label>
          ))}
        </fieldset>
        {mode === "LINK" && <AccountSelect value={accountId} onValueChange={setAccountId} />}
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            Depois
          </Button>
          <Button onClick={() => save.mutate()} disabled={invalid || save.isPending}>
            Salvar
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function HowItWorks() {
  const items = [
    {
      icon: ShieldCheck,
      title: "Só leitura",
      text: "A autorização do Open Finance não permite pagar nem transferir. Você revoga quando quiser.",
    },
    {
      icon: RefreshCw,
      title: "Chega sozinho",
      text: "O banco avisa quando há lançamento novo, e todo dia de manhã conferimos de novo.",
    },
    {
      icon: CheckCircle2,
      title: "Sem duplicar",
      text: "Cada lançamento tem o id do banco. Previstos (fixos, contas) são efetivados, não duplicados.",
    },
  ];
  return (
    <div className="grid gap-3 sm:grid-cols-3">
      {items.map(({ icon: Icon, title, text }) => (
        <div key={title} className="rounded-2xl bg-surface-2 px-4 py-3">
          <Icon className="size-4 text-primary" />
          <p className="mt-2 text-sm font-semibold">{title}</p>
          <p className="mt-0.5 text-xs text-muted-foreground">{text}</p>
        </div>
      ))}
    </div>
  );
}
