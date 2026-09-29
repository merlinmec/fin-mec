import { useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AlertTriangle, ArrowRight, MailWarning, Users } from "lucide-react";
import { toast } from "sonner";
import { acceptInvite, previewInvite } from "@/api/household";
import { useAuth } from "@/auth/auth-context";
import { Button } from "@/components/ui/button";
import { buttonVariants } from "@/components/ui/button-variants";
import { Panel } from "@/components/ui/panel";
import { Skeleton } from "@/components/ui/skeleton";
import { getErrorMessage } from "@/lib/errors";

/**
 * /convite?token=... (Fase 17). Rota protegida: quem não está logado passa pelo login (ou cria
 * a conta) e volta para cá pelo ?next=. Mostra o que acontece antes de aceitar — principalmente
 * que o espaço pessoal atual é substituído.
 */
export function InvitePage() {
  const [params] = useSearchParams();
  const token = params.get("token") ?? "";
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [discard, setDiscard] = useState(false);

  const preview = useQuery({
    queryKey: ["household-invite", token],
    queryFn: () => previewInvite(token),
    enabled: token.length > 0,
    retry: false,
  });

  const accept = useMutation({
    mutationFn: () => acceptInvite(token, discard),
    onSuccess: (household) => {
      // Tudo no cache era do espaço anterior.
      queryClient.clear();
      toast.success(`Bem-vindo a “${household.name}”.`);
      void navigate("/", { replace: true });
    },
    onError: (err) => toast.error(getErrorMessage(err, "Não foi possível aceitar o convite.")),
  });

  const switchAccount = async () => {
    await logout();
    void navigate(`/login?next=${encodeURIComponent(`/convite?token=${token}`)}`, {
      replace: true,
    });
  };

  if (!token || preview.isError) {
    return (
      <div className="mx-auto max-w-xl py-4">
        <Panel>
          <div className="flex flex-col items-center gap-3 py-6 text-center">
            <MailWarning className="size-10 text-muted-foreground" />
            <h1 className="text-lg font-semibold">Convite inválido</h1>
            <p className="max-w-sm text-sm text-muted-foreground">
              {preview.error
                ? getErrorMessage(
                    preview.error,
                    "Este convite é inválido, expirou ou já foi usado.",
                  )
                : "O link está incompleto."}{" "}
              Peça um novo convite para quem te chamou.
            </p>
            <Link to="/" className={buttonVariants({ variant: "outline" })}>
              Ir para o início
            </Link>
          </div>
        </Panel>
      </div>
    );
  }

  if (preview.isPending) {
    return (
      <div className="mx-auto max-w-xl py-4">
        <Skeleton className="h-64 rounded-2xl" />
      </div>
    );
  }

  const info = preview.data;
  return (
    <div className="mx-auto max-w-xl py-4">
      <Panel>
        <div className="space-y-4">
          <div className="flex items-start gap-3">
            <span className="flex size-12 shrink-0 items-center justify-center rounded-2xl bg-primary/10 text-primary">
              <Users className="size-6" />
            </span>
            <div>
              <p className="text-xs font-semibold tracking-wide text-muted-foreground uppercase">
                Convite para compartilhar
              </p>
              <h1 className="text-xl font-bold tracking-tight">{info.householdName}</h1>
              <p className="text-sm text-muted-foreground">
                {info.invitedByEmail ?? "Alguém"} convidou você · {info.memberCount}{" "}
                {info.memberCount === 1 ? "pessoa" : "pessoas"} hoje
              </p>
            </div>
          </div>

          {!info.emailMatches ? (
            <div className="space-y-3">
              <p className="rounded-lg bg-warning/12 px-3 py-2 text-sm text-warning">
                Este convite foi enviado para outro e-mail. Você está como{" "}
                <strong>{user?.email}</strong>.
              </p>
              <Button onClick={() => void switchAccount()}>Entrar com outra conta</Button>
            </div>
          ) : info.alreadyMember ? (
            <div className="space-y-3">
              <p className="text-sm text-muted-foreground">Você já participa deste household.</p>
              <Link to="/" className={buttonVariants()}>
                Ir para o início <ArrowRight className="size-4" />
              </Link>
            </div>
          ) : info.mustLeaveCurrent ? (
            <div className="space-y-3">
              <p className="rounded-lg bg-warning/12 px-3 py-2 text-sm text-warning">
                Você já divide outro espaço com outras pessoas. Saia dele antes em Configurações →
                Compartilhamento (se você é o dono, transfira a posse primeiro).
              </p>
              <Link
                to="/configuracoes/compartilhar"
                className={buttonVariants({ variant: "outline" })}
              >
                Abrir Compartilhamento
              </Link>
            </div>
          ) : (
            <div className="space-y-4">
              <ul className="space-y-1.5 text-sm text-muted-foreground">
                <li>
                  • Vocês passam a ver e lançar nas mesmas contas, cartões, orçamentos e metas.
                </li>
                <li>• Cada lançamento mostra quem lançou.</li>
                <li>• Você pode sair quando quiser e volta a ter um espaço só seu.</li>
              </ul>
              {info.hasPersonalData && (
                <div className="space-y-2 rounded-xl border border-destructive/30 bg-destructive/6 p-3">
                  <p className="flex items-start gap-2 text-sm font-medium text-destructive">
                    <AlertTriangle className="mt-0.5 size-4 shrink-0" />
                    Seus dados atuais (contas, lançamentos, cartões, metas) serão apagados. Os dois
                    espaços não se juntam.
                  </p>
                  <p className="text-xs text-muted-foreground">
                    Quer guardar uma cópia?{" "}
                    <Link to="/lancamentos" className="font-medium text-primary hover:underline">
                      Exporte os lançamentos em CSV
                    </Link>{" "}
                    antes de aceitar.
                  </p>
                  <label className="flex items-center gap-2 text-sm">
                    <input
                      type="checkbox"
                      className="accent-destructive"
                      checked={discard}
                      onChange={(e) => setDiscard(e.target.checked)}
                    />
                    Entendo que meus dados atuais serão apagados
                  </label>
                </div>
              )}
              <div className="flex gap-2">
                <Button
                  onClick={() => accept.mutate()}
                  disabled={accept.isPending || (info.hasPersonalData && !discard)}
                >
                  Aceitar convite
                </Button>
                <Link to="/" className={buttonVariants({ variant: "ghost" })}>
                  Agora não
                </Link>
              </div>
            </div>
          )}
        </div>
      </Panel>
    </div>
  );
}
