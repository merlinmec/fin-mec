import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Check, Copy, Crown, LogOut, Mail, Pencil, Send, UserMinus, Users, X } from "lucide-react";
import { toast } from "sonner";
import {
  MAX_HOUSEHOLD_MEMBERS,
  inviteToHousehold,
  leaveHousehold,
  removeMember,
  renameHousehold,
  revokeInvite,
  transferOwnership,
  type HouseholdMember,
  type HouseholdOverview,
  type InviteCreated,
} from "@/api/household";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Panel } from "@/components/ui/panel";
import { Skeleton } from "@/components/ui/skeleton";
import { householdQueryKey, useHousehold } from "@/hooks/useHousehold";
import { formatDate } from "@/lib/dates";
import { getErrorMessage } from "@/lib/errors";
import { cn } from "@/lib/utils";

/**
 * Fase 17 — household compartilhado. Um espaço financeiro com várias pessoas (casal, família):
 * todos veem e lançam nas mesmas contas. O dono convida, remove e transfere a posse.
 */
export function HouseholdSection() {
  const { data: household, isPending } = useHousehold();

  if (isPending || !household) {
    return <Skeleton className="h-72 rounded-2xl" />;
  }
  const isOwner = household.myRole === "OWNER";
  const seats = household.members.length + household.invites.length;

  return (
    <div className="space-y-5">
      <Panel
        title={<HouseholdName household={household} editable={isOwner} />}
        description={
          household.members.length > 1
            ? "Todos aqui veem e lançam nas mesmas contas, cartões, orçamentos e metas."
            : "Hoje só você usa este espaço. Convide quem divide as contas com você."
        }
      >
        <ul className="-mx-1 divide-y divide-border/60">
          {household.members.map((member) => (
            <MemberRow key={member.userId} member={member} canManage={isOwner && !member.you} />
          ))}
        </ul>
        {!isOwner && <LeaveButton />}
      </Panel>

      {isOwner && <InvitePanel household={household} seatsLeft={MAX_HOUSEHOLD_MEMBERS - seats} />}

      <div className="grid gap-3 sm:grid-cols-3">
        {[
          {
            title: "Um espaço, várias pessoas",
            text: "Cada lançamento mostra quem lançou. Relatórios e saldos são da casa toda.",
          },
          {
            title: "Convite seguro",
            text: "O link vale 7 dias, funciona uma vez e só com a conta do e-mail convidado.",
          },
          {
            title: "Sair é imediato",
            text: "Quem sai ou é removido perde o acesso na hora, em todos os dispositivos.",
          },
        ].map((item) => (
          <div key={item.title} className="rounded-2xl bg-surface-2 px-4 py-3">
            <p className="text-sm font-semibold">{item.title}</p>
            <p className="mt-0.5 text-xs text-muted-foreground">{item.text}</p>
          </div>
        ))}
      </div>
    </div>
  );
}

function HouseholdName({
  household,
  editable,
}: {
  household: HouseholdOverview;
  editable: boolean;
}) {
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState(household.name);
  const rename = useMutation({
    mutationFn: () => renameHousehold(name),
    onSuccess: (data) => {
      queryClient.setQueryData(householdQueryKey, data);
      setEditing(false);
    },
    onError: (err) => toast.error(getErrorMessage(err, "Não foi possível renomear.")),
  });

  if (!editing) {
    return (
      <span className="flex items-center gap-2">
        <Users className="size-4 text-primary" />
        {household.name}
        {editable && (
          <button
            type="button"
            className="rounded-md p-1 text-muted-foreground hover:bg-muted hover:text-foreground"
            aria-label="Renomear household"
            onClick={() => {
              setName(household.name);
              setEditing(true);
            }}
          >
            <Pencil className="size-3.5" />
          </button>
        )}
      </span>
    );
  }
  return (
    <form
      className="flex items-center gap-2"
      onSubmit={(e) => {
        e.preventDefault();
        if (name.trim()) rename.mutate();
      }}
    >
      <Input
        autoFocus
        value={name}
        maxLength={80}
        onChange={(e) => setName(e.target.value)}
        className="h-8 max-w-xs"
        aria-label="Nome do household"
      />
      <Button
        size="sm"
        type="submit"
        disabled={rename.isPending || !name.trim()}
        aria-label="Salvar"
      >
        <Check className="size-4" />
      </Button>
      <Button
        size="sm"
        variant="ghost"
        type="button"
        onClick={() => setEditing(false)}
        aria-label="Cancelar"
      >
        <X className="size-4" />
      </Button>
    </form>
  );
}

function initials(email: string) {
  return email.slice(0, 2).toUpperCase();
}

function MemberRow({ member, canManage }: { member: HouseholdMember; canManage: boolean }) {
  const queryClient = useQueryClient();
  const [confirm, setConfirm] = useState<"remove" | "owner" | null>(null);
  const done = (data?: HouseholdOverview) => {
    setConfirm(null);
    if (data) queryClient.setQueryData(householdQueryKey, data);
    else void queryClient.invalidateQueries({ queryKey: householdQueryKey });
  };
  const remove = useMutation({
    mutationFn: () => removeMember(member.userId),
    onSuccess: () => {
      done();
      toast.success(`${member.email} não tem mais acesso.`);
    },
    onError: (err) => toast.error(getErrorMessage(err, "Não foi possível remover.")),
  });
  const promote = useMutation({
    mutationFn: () => transferOwnership(member.userId),
    onSuccess: (data) => {
      done(data);
      toast.success(`${member.email} agora é o dono.`);
    },
    onError: (err) => toast.error(getErrorMessage(err, "Não foi possível transferir.")),
  });

  return (
    <li className="flex items-center gap-3 px-1 py-2.5">
      <span
        className={cn(
          "flex size-9 shrink-0 items-center justify-center rounded-full text-xs font-bold",
          member.you ? "bg-primary text-primary-foreground" : "bg-muted text-muted-foreground",
        )}
        aria-hidden
      >
        {initials(member.email)}
      </span>
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium">
          {member.email}
          {member.you && <span className="text-muted-foreground"> · você</span>}
        </p>
        <p className="text-xs whitespace-nowrap text-muted-foreground">
          Desde {formatDate(member.joinedAt.slice(0, 10))}
        </p>
      </div>
      <span
        className={cn(
          "items-center gap-1 rounded-full px-2 py-0.5 text-[0.6875rem] font-semibold",
          member.role === "OWNER"
            ? "inline-flex bg-warning/15 text-warning"
            : "hidden bg-muted text-muted-foreground sm:inline-flex",
        )}
      >
        {member.role === "OWNER" && <Crown className="size-3" />}
        {member.role === "OWNER" ? "Dono" : "Membro"}
      </span>
      {canManage && (
        <div className="flex gap-1">
          <Button
            size="sm"
            variant="ghost"
            onClick={() => setConfirm("owner")}
            aria-label={`Tornar ${member.email} dono`}
            title="Tornar dono"
          >
            <Crown className="size-4" />
          </Button>
          <Button
            size="sm"
            variant="ghost"
            className="text-destructive hover:text-destructive"
            onClick={() => setConfirm("remove")}
            aria-label={`Remover ${member.email}`}
            title="Remover"
          >
            <UserMinus className="size-4" />
          </Button>
        </div>
      )}

      <Dialog open={confirm !== null} onOpenChange={(open) => !open && setConfirm(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {confirm === "remove"
                ? `Remover ${member.email}?`
                : `Passar a posse para ${member.email}?`}
            </DialogTitle>
            <DialogDescription>
              {confirm === "remove"
                ? "A pessoa perde o acesso na hora, em todos os dispositivos, e ganha um espaço próprio vazio. Os lançamentos que ela fez continuam aqui."
                : "Você vira membro: deixa de poder convidar, remover e renomear. Só o novo dono pode devolver a posse."}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setConfirm(null)}>
              Cancelar
            </Button>
            <Button
              variant={confirm === "remove" ? "destructive" : "default"}
              disabled={remove.isPending || promote.isPending}
              onClick={() => (confirm === "remove" ? remove.mutate() : promote.mutate())}
            >
              {confirm === "remove" ? "Remover" : "Transferir posse"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </li>
  );
}

function InvitePanel({
  household,
  seatsLeft,
}: {
  household: HouseholdOverview;
  seatsLeft: number;
}) {
  const queryClient = useQueryClient();
  const [email, setEmail] = useState("");
  const [created, setCreated] = useState<InviteCreated | null>(null);
  const [copied, setCopied] = useState(false);

  const invite = useMutation({
    mutationFn: () => inviteToHousehold(email),
    onSuccess: (data) => {
      setCreated(data);
      setCopied(false);
      setEmail("");
      void queryClient.invalidateQueries({ queryKey: householdQueryKey });
    },
    onError: (err) => toast.error(getErrorMessage(err, "Não foi possível convidar.")),
  });
  const revoke = useMutation({
    mutationFn: (id: string) => revokeInvite(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: householdQueryKey });
      toast.success("Convite cancelado — o link parou de funcionar.");
    },
    onError: (err) => toast.error(getErrorMessage(err, "Não foi possível cancelar.")),
  });

  const copy = async () => {
    if (!created) return;
    try {
      await navigator.clipboard.writeText(created.acceptUrl);
      setCopied(true);
    } catch {
      toast.error("Não deu para copiar — selecione o link e copie manualmente.");
    }
  };

  return (
    <Panel
      title="Convidar alguém"
      description={`O convite vai por e-mail e você também recebe o link para mandar por onde quiser. Cabem mais ${Math.max(seatsLeft, 0)} pessoa(s), contando convites pendentes.`}
    >
      <form
        className="flex flex-col gap-2 sm:flex-row sm:items-end"
        onSubmit={(e) => {
          e.preventDefault();
          invite.mutate();
        }}
      >
        <div className="flex-1 space-y-1.5">
          <Label htmlFor="invite-email">E-mail de quem vai entrar</Label>
          <Input
            id="invite-email"
            type="email"
            required
            value={email}
            placeholder="pessoa@exemplo.com"
            onChange={(e) => setEmail(e.target.value)}
          />
        </div>
        <Button type="submit" disabled={invite.isPending || seatsLeft <= 0 || !email}>
          <Send className="size-4" /> Convidar
        </Button>
      </form>

      {created && (
        <div className="mt-4 rounded-xl border border-primary/30 bg-primary/6 p-3">
          <p className="text-sm font-medium">Convite criado para {created.invite.email}</p>
          <p className="mt-0.5 text-xs text-muted-foreground">
            Se o e-mail não chegar, mande este link por outro canal. Ele aparece só agora, vale até{" "}
            {formatDate(created.invite.expiresAt.slice(0, 10))} e só funciona com a conta desse
            e-mail.
          </p>
          <div className="mt-2 flex gap-2">
            <Input
              readOnly
              value={created.acceptUrl}
              className="font-mono text-xs"
              onFocus={(e) => e.target.select()}
            />
            <Button type="button" variant="outline" onClick={() => void copy()}>
              {copied ? <Check className="size-4" /> : <Copy className="size-4" />}
              {copied ? "Copiado" : "Copiar"}
            </Button>
          </div>
        </div>
      )}

      {household.invites.length > 0 && (
        <div className="mt-5">
          <p className="text-xs font-semibold tracking-wide text-muted-foreground uppercase">
            Aguardando resposta
          </p>
          <ul className="mt-1 divide-y divide-border/60">
            {household.invites.map((pending) => (
              <li key={pending.id} className="flex items-center gap-3 py-2">
                <Mail className="size-4 text-muted-foreground" />
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm">{pending.email}</p>
                  <p className="text-xs text-muted-foreground">
                    Vale até {formatDate(pending.expiresAt.slice(0, 10))}
                  </p>
                </div>
                <Button
                  size="sm"
                  variant="ghost"
                  disabled={revoke.isPending}
                  onClick={() => revoke.mutate(pending.id)}
                >
                  Cancelar
                </Button>
              </li>
            ))}
          </ul>
        </div>
      )}
    </Panel>
  );
}

function LeaveButton() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const leave = useMutation({
    mutationFn: leaveHousehold,
    onSuccess: () => {
      // Tudo no cache era do household anterior.
      queryClient.clear();
      toast.success("Você saiu. Este agora é o seu espaço pessoal, vazio.");
      void navigate("/", { replace: true });
    },
    onError: (err) => toast.error(getErrorMessage(err, "Não foi possível sair.")),
  });
  return (
    <>
      <Button variant="outline" className="mt-4" onClick={() => setOpen(true)}>
        <LogOut className="size-4" /> Sair do household
      </Button>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Sair do household?</DialogTitle>
            <DialogDescription>
              Você deixa de ver as contas e lançamentos daqui e começa um espaço pessoal vazio. Os
              lançamentos que você fez continuam com as outras pessoas. Para voltar, só com um novo
              convite.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setOpen(false)}>
              Cancelar
            </Button>
            <Button variant="destructive" onClick={() => leave.mutate()} disabled={leave.isPending}>
              Sair
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
