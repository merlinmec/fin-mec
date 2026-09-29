import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import QRCode from "qrcode";
import {
  AlertTriangle,
  CheckCircle2,
  Copy,
  Download,
  KeyRound,
  LogOut,
  Monitor,
  ShieldCheck,
  ShieldOff,
  Smartphone,
} from "lucide-react";
import { toast } from "sonner";
import {
  ALERT_EVENTS,
  endSession,
  listSessions,
  SECURITY_EVENT_LABELS,
  changePassword,
  disableMfa,
  enableMfa,
  getSecurityEvents,
  getSecurityOverview,
  regenerateRecoveryCodes,
  revokeOtherSessions,
  startMfaSetup,
  type MfaSetup,
} from "@/api/account-security";
import { fetchCurrentUser } from "@/api/auth";
import { useAuth } from "@/auth/auth-context";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Panel } from "@/components/ui/panel";
import { Skeleton } from "@/components/ui/skeleton";
import { getErrorMessage } from "@/lib/errors";
import { cn } from "@/lib/utils";

function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short" });
}

/** Navegador/sistema legível a partir do User-Agent (só para a lista de eventos). */
function describeAgent(ua: string | null): string {
  if (!ua) return "Dispositivo desconhecido";
  const browser = /Edg\//.test(ua)
    ? "Edge"
    : /Chrome\//.test(ua)
      ? "Chrome"
      : /Firefox\//.test(ua)
        ? "Firefox"
        : /Safari\//.test(ua)
          ? "Safari"
          : "Navegador";
  const os = /Windows/.test(ua)
    ? "Windows"
    : /Android/.test(ua)
      ? "Android"
      : /iPhone|iPad/.test(ua)
        ? "iOS"
        : /Mac OS/.test(ua)
          ? "macOS"
          : /Linux/.test(ua)
            ? "Linux"
            : "";
  return os ? `${browser} · ${os}` : browser;
}

function useRefreshSecurity() {
  const queryClient = useQueryClient();
  const { setCurrentUser } = useAuth();
  return async () => {
    void queryClient.invalidateQueries({ queryKey: ["account-security"] });
    // O backend renova a sessão (novo cookie) nessas operações; /auth/me traz o usuário atualizado.
    setCurrentUser(await fetchCurrentUser());
  };
}

export function SecuritySection() {
  const overview = useQuery({
    queryKey: ["account-security", "overview"],
    queryFn: getSecurityOverview,
  });
  const events = useQuery({ queryKey: ["account-security", "events"], queryFn: getSecurityEvents });

  return (
    <div className="space-y-4">
      <div className="grid gap-4 lg:grid-cols-2">
        <Panel
          title="Senha"
          description={
            overview.data?.passwordChangedAt
              ? `Alterada em ${formatDateTime(overview.data.passwordChangedAt)}`
              : "Nunca alterada desde o cadastro"
          }
        >
          <ChangePasswordForm />
        </Panel>
        <Panel
          title="Verificação em duas etapas"
          description="Um código do app autenticador além da senha em todo login."
        >
          {overview.data ? (
            <MfaManager
              enabled={overview.data.mfaEnabled}
              remaining={overview.data.recoveryCodesRemaining}
            />
          ) : (
            <Skeleton className="h-40 rounded-xl" />
          )}
        </Panel>
      </div>

      <Panel
        title="Sessões"
        description="Trocar a senha ou mexer no 2FA já encerra as outras sessões automaticamente."
      >
        <ActiveSessions />
      </Panel>

      <Panel
        title="Atividade recente"
        description="Os últimos 50 eventos de segurança da sua conta."
      >
        {events.isPending ? (
          <Skeleton className="h-48 rounded-xl" />
        ) : (
          <ul className="-mx-1 divide-y divide-border/60">
            {(events.data ?? []).map((event) => {
              const alert = ALERT_EVENTS.has(event.type);
              return (
                <li key={event.id} className="flex items-center gap-3 px-1 py-2.5">
                  <span
                    className={cn(
                      "flex size-8 shrink-0 items-center justify-center rounded-full",
                      alert ? "bg-warning/15 text-warning" : "bg-muted text-muted-foreground",
                    )}
                  >
                    {alert ? (
                      <AlertTriangle className="size-4" />
                    ) : (
                      <ShieldCheck className="size-4" />
                    )}
                  </span>
                  <div className="min-w-0 flex-1">
                    <div className="text-sm font-medium">{SECURITY_EVENT_LABELS[event.type]}</div>
                    <div className="truncate text-xs text-muted-foreground">
                      {describeAgent(event.userAgent)}
                      {event.ipAddress ? ` · IP ${event.ipAddress}` : ""}
                    </div>
                  </div>
                  <time className="shrink-0 text-xs text-muted-foreground">
                    {formatDateTime(event.createdAt)}
                  </time>
                </li>
              );
            })}
          </ul>
        )}
      </Panel>
    </div>
  );
}

function ChangePasswordForm() {
  const refresh = useRefreshSecurity();
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState<string | null>(null);
  const mutation = useMutation({
    mutationFn: () => changePassword(current, next),
    onSuccess: async () => {
      setCurrent("");
      setNext("");
      setConfirm("");
      toast.success("Senha alterada. As outras sessões foram encerradas.");
      await refresh();
    },
    onError: (err) => setError(getErrorMessage(err, "Não foi possível alterar a senha.")),
  });

  function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    if (next.length < 10) return setError("A nova senha precisa de pelo menos 10 caracteres.");
    if (next !== confirm) return setError("A confirmação não confere com a nova senha.");
    mutation.mutate();
  }

  return (
    <form className="space-y-3" onSubmit={submit} noValidate>
      <div className="space-y-1.5">
        <Label htmlFor="pw-current">Senha atual</Label>
        <Input
          id="pw-current"
          type="password"
          autoComplete="current-password"
          value={current}
          onChange={(e) => setCurrent(e.target.value)}
        />
      </div>
      <div className="grid gap-3 sm:grid-cols-2">
        <div className="space-y-1.5">
          <Label htmlFor="pw-new">Nova senha</Label>
          <Input
            id="pw-new"
            type="password"
            autoComplete="new-password"
            value={next}
            onChange={(e) => setNext(e.target.value)}
          />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="pw-confirm">Confirmar</Label>
          <Input
            id="pw-confirm"
            type="password"
            autoComplete="new-password"
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
          />
        </div>
      </div>
      <p className="text-xs text-muted-foreground">
        Mínimo de 10 caracteres. Uma frase com palavras aleatórias é mais forte e mais fácil de
        lembrar do que “Senha@2026”.
      </p>
      {error && (
        <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
          {error}
        </p>
      )}
      <Button type="submit" disabled={mutation.isPending || !current || !next}>
        <KeyRound /> {mutation.isPending ? "Alterando…" : "Alterar senha"}
      </Button>
    </form>
  );
}

function MfaManager({ enabled, remaining }: { enabled: boolean; remaining: number }) {
  const refresh = useRefreshSecurity();
  const [setup, setSetup] = useState<MfaSetup | null>(null);
  const [qr, setQr] = useState<string | null>(null);
  const [code, setCode] = useState("");
  const [codes, setCodes] = useState<string[] | null>(null);
  const [mode, setMode] = useState<"idle" | "disable" | "regenerate">("idle");
  const [password, setPassword] = useState("");
  const [secondFactor, setSecondFactor] = useState("");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!setup) return setQr(null);
    void QRCode.toDataURL(setup.otpauthUri, {
      margin: 1,
      width: 200,
      errorCorrectionLevel: "M",
    }).then(setQr);
  }, [setup]);

  const start = useMutation({
    mutationFn: startMfaSetup,
    onSuccess: setSetup,
    onError: (e) => setError(getErrorMessage(e)),
  });
  const enable = useMutation({
    mutationFn: () => enableMfa(code),
    onSuccess: async (res) => {
      setCodes(res.recoveryCodes);
      setSetup(null);
      setCode("");
      toast.success("Verificação em duas etapas ativada.");
      await refresh();
    },
    onError: (e) => setError(getErrorMessage(e, "Código inválido.")),
  });
  const disable = useMutation({
    mutationFn: () => disableMfa(password, secondFactor),
    onSuccess: async () => {
      resetReauth();
      toast.success("Verificação em duas etapas desativada.");
      await refresh();
    },
    onError: (e) => setError(getErrorMessage(e)),
  });
  const regenerate = useMutation({
    mutationFn: () => regenerateRecoveryCodes(password, secondFactor),
    onSuccess: async (res) => {
      setCodes(res.recoveryCodes);
      resetReauth();
      await refresh();
    },
    onError: (e) => setError(getErrorMessage(e)),
  });

  function resetReauth() {
    setMode("idle");
    setPassword("");
    setSecondFactor("");
    setError(null);
  }

  if (codes) {
    return <RecoveryCodes codes={codes} onDone={() => setCodes(null)} />;
  }

  if (!enabled && setup) {
    return (
      <div className="space-y-4">
        <ol className="space-y-1 text-sm text-muted-foreground">
          <li>
            1. Abra o app autenticador (Google Authenticator, Microsoft Authenticator, 1Password…).
          </li>
          <li>2. Escaneie o QR code ou digite a chave manualmente.</li>
          <li>3. Informe o código de 6 dígitos que aparecer.</li>
        </ol>
        <div className="flex flex-col items-center gap-4 sm:flex-row sm:items-start">
          <div className="rounded-xl bg-white p-2 shadow-card">
            {qr ? (
              <img src={qr} alt="QR code para configurar o app autenticador" className="size-40" />
            ) : (
              <Skeleton className="size-40" />
            )}
          </div>
          <div className="w-full min-w-0 space-y-3">
            <div>
              <div className="text-xs text-muted-foreground">Chave manual</div>
              <code className="mt-1 block rounded-md bg-muted px-2 py-1.5 font-mono text-xs break-all select-all">
                {setup.secret}
              </code>
            </div>
            <form
              className="space-y-2"
              onSubmit={(e) => {
                e.preventDefault();
                setError(null);
                enable.mutate();
              }}
            >
              <Label htmlFor="mfa-code">Código do app</Label>
              <div className="flex gap-2">
                <Input
                  id="mfa-code"
                  inputMode="numeric"
                  autoComplete="one-time-code"
                  maxLength={6}
                  value={code}
                  onChange={(e) => setCode(e.target.value.replace(/\D/g, ""))}
                  className="num w-32 text-center text-lg tracking-[0.3em]"
                  placeholder="000000"
                />
                <Button type="submit" disabled={code.length !== 6 || enable.isPending}>
                  Ativar
                </Button>
              </div>
            </form>
          </div>
        </div>
        {error && (
          <p role="alert" className="text-sm text-destructive">
            {error}
          </p>
        )}
        <Button variant="ghost" size="sm" onClick={() => setSetup(null)}>
          Cancelar
        </Button>
      </div>
    );
  }

  if (!enabled) {
    return (
      <div className="space-y-3">
        <div className="flex items-start gap-3 rounded-xl border border-warning/30 bg-warning/8 p-3">
          <ShieldOff className="mt-0.5 size-5 shrink-0 text-warning" />
          <p className="text-sm">
            <strong className="font-semibold">Desativada.</strong> Com ela, alguém que descubra sua
            senha ainda não consegue entrar sem o seu celular.
          </p>
        </div>
        {error && (
          <p role="alert" className="text-sm text-destructive">
            {error}
          </p>
        )}
        <Button onClick={() => start.mutate()} disabled={start.isPending}>
          <Smartphone /> Configurar app autenticador
        </Button>
      </div>
    );
  }

  return (
    <div className="space-y-3">
      <div className="flex items-start gap-3 rounded-xl border border-success/30 bg-success/8 p-3">
        <CheckCircle2 className="mt-0.5 size-5 shrink-0 text-success" />
        <p className="text-sm">
          <strong className="font-semibold">Ativa.</strong> {remaining} código
          {remaining === 1 ? "" : "s"} de recuperação disponíve
          {remaining === 1 ? "l" : "is"}.
        </p>
      </div>
      {mode === "idle" ? (
        <div className="flex flex-wrap gap-2">
          <Button variant="outline" onClick={() => setMode("regenerate")}>
            Gerar novos códigos de recuperação
          </Button>
          <Button
            variant="ghost"
            className="text-destructive hover:bg-destructive/10 hover:text-destructive"
            onClick={() => setMode("disable")}
          >
            Desativar
          </Button>
        </div>
      ) : (
        <form
          className="space-y-3 rounded-xl border border-border p-3"
          onSubmit={(e) => {
            e.preventDefault();
            setError(null);
            (mode === "disable" ? disable : regenerate).mutate();
          }}
        >
          <p className="text-sm font-medium">
            {mode === "disable" ? "Confirme para desativar" : "Confirme para gerar novos códigos"}
          </p>
          <div className="grid gap-3 sm:grid-cols-2">
            <Input
              type="password"
              autoComplete="current-password"
              placeholder="Sua senha"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              aria-label="Senha"
            />
            <Input
              placeholder="Código do app ou de recuperação"
              value={secondFactor}
              onChange={(e) => setSecondFactor(e.target.value)}
              aria-label="Código de verificação"
              autoComplete="one-time-code"
            />
          </div>
          {error && (
            <p role="alert" className="text-sm text-destructive">
              {error}
            </p>
          )}
          <div className="flex gap-2">
            <Button
              type="submit"
              variant={mode === "disable" ? "destructive" : "default"}
              disabled={!password || !secondFactor || disable.isPending || regenerate.isPending}
            >
              {mode === "disable" ? "Desativar 2FA" : "Gerar códigos"}
            </Button>
            <Button type="button" variant="ghost" onClick={resetReauth}>
              Cancelar
            </Button>
          </div>
        </form>
      )}
    </div>
  );
}

function RecoveryCodes({ codes, onDone }: { codes: string[]; onDone: () => void }) {
  const text = `fin-mec — códigos de recuperação\nCada código funciona uma única vez.\n\n${codes.join("\n")}\n`;

  function download() {
    const url = URL.createObjectURL(new Blob([text], { type: "text/plain;charset=utf-8" }));
    const link = document.createElement("a");
    link.href = url;
    link.download = "fin-mec-codigos-de-recuperacao.txt";
    link.click();
    URL.revokeObjectURL(url);
  }

  return (
    <div className="space-y-3">
      <div className="flex items-start gap-3 rounded-xl border border-warning/30 bg-warning/8 p-3 text-sm">
        <AlertTriangle className="mt-0.5 size-5 shrink-0 text-warning" />
        <p>
          <strong className="font-semibold">Guarde estes códigos agora.</strong> Eles não serão
          mostrados de novo e são a única forma de entrar se você perder o celular.
        </p>
      </div>
      <ul className="grid grid-cols-2 gap-1.5 rounded-xl bg-muted p-3 font-mono text-sm">
        {codes.map((c) => (
          <li key={c} className="text-center select-all">
            {c}
          </li>
        ))}
      </ul>
      <div className="flex flex-wrap gap-2">
        <Button
          variant="outline"
          onClick={() =>
            void navigator.clipboard
              .writeText(codes.join("\n"))
              .then(() => toast.success("Códigos copiados."))
          }
        >
          <Copy /> Copiar
        </Button>
        <Button variant="outline" onClick={download}>
          <Download /> Baixar .txt
        </Button>
        <Button onClick={onDone}>Já guardei</Button>
      </div>
    </div>
  );
}

function ActiveSessions() {
  const refresh = useRefreshSecurity();
  const queryClient = useQueryClient();
  const sessions = useQuery({ queryKey: ["account-security", "sessions"], queryFn: listSessions });
  const end = useMutation({
    mutationFn: (id: string) => endSession(id),
    onSuccess: () => {
      toast.success("Sessão encerrada.");
      void queryClient.invalidateQueries({ queryKey: ["account-security"] });
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
  const revokeOthers = useMutation({
    mutationFn: revokeOtherSessions,
    onSuccess: async () => {
      toast.success("Todas as outras sessões foram encerradas.");
      await refresh();
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  });
  const others = (sessions.data ?? []).filter((s) => !s.current).length;

  if (sessions.isPending) return <Skeleton className="h-28 rounded-xl" />;
  return (
    <div className="space-y-3">
      <ul className="-mx-1 divide-y divide-border/60">
        {(sessions.data ?? []).map((s) => (
          <li key={s.id} className="flex items-center gap-3 px-1 py-2.5">
            <span
              className={cn(
                "flex size-8 shrink-0 items-center justify-center rounded-full",
                s.current ? "bg-primary/10 text-primary" : "bg-muted text-muted-foreground",
              )}
            >
              <Monitor className="size-4" />
            </span>
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-2 text-sm font-medium">
                {s.device}
                {s.current && (
                  <span className="rounded-full bg-primary/10 px-2 py-0.5 text-[10px] font-semibold text-primary">
                    Esta sessão
                  </span>
                )}
              </div>
              <div className="truncate text-xs text-muted-foreground">
                {s.ipAddress ? `IP ${s.ipAddress} · ` : ""}último acesso{" "}
                {formatDateTime(s.lastAccessedAt)} · desde {formatDateTime(s.createdAt)}
              </div>
            </div>
            {!s.current && (
              <Button
                variant="outline"
                size="sm"
                disabled={end.isPending}
                onClick={() => end.mutate(s.id)}
              >
                Encerrar
              </Button>
            )}
          </li>
        ))}
      </ul>
      {others > 0 && (
        <Button
          variant="outline"
          onClick={() => revokeOthers.mutate()}
          disabled={revokeOthers.isPending}
        >
          <LogOut /> Encerrar as outras {others} sessões
        </Button>
      )}
    </div>
  );
}
