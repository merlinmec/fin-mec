import { useState } from "react";
import {
  Archive,
  ArchiveRestore,
  CalendarDays,
  History,
  MoreHorizontal,
  Pencil,
  Plus,
  Target,
  Trash2,
  TrendingDown,
  TrendingUp,
} from "lucide-react";
import type { Goal, GoalPayload } from "@/api/goals";
import { GOAL_STATUS_LABELS } from "@/api/goals";
import { CategoryIcon } from "@/components/CategoryIcon";
import { ColorSwatchPicker, IconPicker } from "@/components/SwatchPickers";
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
import { PageHeader } from "@/components/ui/panel";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Skeleton } from "@/components/ui/skeleton";
import {
  useContribute,
  useDeleteGoal,
  useGoalContributions,
  useGoals,
  useRemoveContribution,
  useSaveGoal,
} from "@/hooks/useFeatureData";
import { CATEGORY_COLORS } from "@/lib/category-icons";
import { formatDate } from "@/lib/dates";
import { formatMoney } from "@/lib/money";
import { cn } from "@/lib/utils";

const GOAL_ICONS = [
  "piggy-bank",
  "plane",
  "home",
  "car",
  "graduation-cap",
  "heart-pulse",
  "gift",
  "briefcase",
  "baby",
  "landmark",
  "shopping-cart",
  "trending-up",
];

const STATUS_STYLE: Record<Goal["status"], string> = {
  ACTIVE: "bg-primary/10 text-primary",
  COMPLETED: "bg-success/12 text-success",
  OVERDUE: "bg-warning/15 text-warning",
  ARCHIVED: "bg-muted text-muted-foreground",
};

function monthYear(iso: string): string {
  const [y, m] = iso.split("-").map(Number);
  return new Date(y, m - 1, 1)
    .toLocaleDateString("pt-BR", { month: "short", year: "numeric" })
    .replace(".", "");
}

export function GoalsPage() {
  const [showArchived, setShowArchived] = useState(false);
  const { data: goals, isPending, isError } = useGoals(showArchived);
  const [editing, setEditing] = useState<Goal | "new" | null>(null);
  const [moving, setMoving] = useState<{ goal: Goal; direction: 1 | -1 } | null>(null);
  const [historyOf, setHistoryOf] = useState<Goal | null>(null);

  return (
    <div className="space-y-5">
      <PageHeader
        title="Metas"
        description="Separe dinheiro para o que importa e acompanhe quanto falta."
        actions={
          <>
            <Button variant="ghost" onClick={() => setShowArchived((v) => !v)}>
              {showArchived ? "Ocultar arquivadas" : "Ver arquivadas"}
            </Button>
            <Button onClick={() => setEditing("new")}>
              <Plus /> Nova meta
            </Button>
          </>
        }
      />

      {isError && (
        <p className="rounded-xl bg-destructive/10 px-4 py-3 text-sm text-destructive">
          Não foi possível carregar as metas.
        </p>
      )}

      {isPending && (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {Array.from({ length: 3 }).map((_, i) => (
            <Skeleton key={i} className="h-60 rounded-2xl" />
          ))}
        </div>
      )}

      {goals && goals.length === 0 && (
        <div className="flex flex-col items-center gap-3 rounded-2xl border border-dashed border-border bg-card px-6 py-12 text-center">
          <span className="flex size-14 items-center justify-center rounded-full bg-primary/10 text-primary">
            <Target className="size-7" />
          </span>
          <div>
            <p className="text-base font-semibold">Qual o seu próximo objetivo?</p>
            <p className="mx-auto mt-1 max-w-md text-sm text-muted-foreground">
              Reserva de emergência, viagem, entrada do apartamento. Defina o valor e o prazo: o
              fin-mec calcula quanto guardar por mês e acompanha cada aporte.
            </p>
          </div>
          <Button onClick={() => setEditing("new")}>
            <Plus /> Criar meta
          </Button>
        </div>
      )}

      {goals && goals.length > 0 && (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {goals.map((goal) => (
            <GoalCard
              key={goal.id}
              goal={goal}
              onEdit={() => setEditing(goal)}
              onMove={(direction) => setMoving({ goal, direction })}
              onHistory={() => setHistoryOf(goal)}
            />
          ))}
        </div>
      )}

      <GoalFormDialog
        goal={editing === "new" ? undefined : (editing ?? undefined)}
        open={editing !== null}
        onOpenChange={(o) => !o && setEditing(null)}
      />
      <MoveDialog state={moving} onClose={() => setMoving(null)} />
      <HistoryDialog goal={historyOf} onClose={() => setHistoryOf(null)} />
    </div>
  );
}

function GoalCard({
  goal,
  onEdit,
  onMove,
  onHistory,
}: {
  goal: Goal;
  onEdit: () => void;
  onMove: (direction: 1 | -1) => void;
  onHistory: () => void;
}) {
  const saveGoal = useSaveGoal();
  const deleteGoal = useDeleteGoal();
  const [menuOpen, setMenuOpen] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const color = goal.color ?? "var(--color-primary)";

  function toggleArchive() {
    setMenuOpen(false);
    saveGoal.mutate({
      id: goal.id,
      payload: {
        name: goal.name,
        targetAmount: goal.targetAmount,
        targetDate: goal.targetDate,
        color: goal.color,
        icon: goal.icon,
        archived: !goal.archived,
      },
    });
  }

  return (
    <article
      className={cn(
        "flex flex-col rounded-2xl border border-border/60 bg-card p-5 shadow-card",
        goal.archived && "opacity-70",
      )}
    >
      <header className="flex items-start gap-3">
        <CategoryIcon
          icon={goal.icon ?? "piggy-bank"}
          color={color}
          className="size-11 [&_svg]:size-5"
        />
        <div className="min-w-0 flex-1">
          <h2 className="truncate text-base font-semibold">{goal.name}</h2>
          <span
            className={cn(
              "mt-1 inline-block rounded-full px-2 py-0.5 text-[11px] font-semibold",
              STATUS_STYLE[goal.status],
            )}
          >
            {GOAL_STATUS_LABELS[goal.status]}
          </span>
        </div>
        <Popover open={menuOpen} onOpenChange={setMenuOpen}>
          <PopoverTrigger asChild>
            <Button
              variant="ghost"
              size="icon"
              className="-mt-1 -mr-2 size-8 text-muted-foreground"
              aria-label="Ações da meta"
            >
              <MoreHorizontal className="size-4" />
            </Button>
          </PopoverTrigger>
          <PopoverContent className="w-48 p-1.5">
            <MenuItem
              icon={<Pencil className="size-4" />}
              onClick={() => {
                setMenuOpen(false);
                onEdit();
              }}
            >
              Editar
            </MenuItem>
            <MenuItem
              icon={<History className="size-4" />}
              onClick={() => {
                setMenuOpen(false);
                onHistory();
              }}
            >
              Histórico
            </MenuItem>
            <MenuItem
              icon={
                goal.archived ? (
                  <ArchiveRestore className="size-4" />
                ) : (
                  <Archive className="size-4" />
                )
              }
              onClick={toggleArchive}
            >
              {goal.archived ? "Desarquivar" : "Arquivar"}
            </MenuItem>
            <MenuItem
              destructive
              icon={<Trash2 className="size-4" />}
              onClick={() => {
                setMenuOpen(false);
                setConfirmDelete(true);
              }}
            >
              Excluir
            </MenuItem>
          </PopoverContent>
        </Popover>
      </header>

      <div className="mt-5">
        <div className="flex items-baseline justify-between gap-2">
          <span className="num text-2xl font-bold tracking-tight">
            {formatMoney(goal.savedAmount)}
          </span>
          <span className="num text-sm font-semibold text-muted-foreground">
            {Math.floor(goal.progressPercent)}%
          </span>
        </div>
        <div className="mt-0.5 text-xs text-muted-foreground">
          de {formatMoney(goal.targetAmount)}
        </div>
        <div
          className="mt-3 h-2.5 overflow-hidden rounded-full bg-muted"
          role="progressbar"
          aria-valuenow={goal.progressPercent}
          aria-valuemin={0}
          aria-valuemax={100}
          aria-label={`Progresso de ${goal.name}`}
        >
          <div
            className="h-full rounded-full transition-[width] duration-500"
            style={{ width: `${goal.progressPercent}%`, backgroundColor: color }}
          />
        </div>
      </div>

      <dl className="mt-4 grid grid-cols-2 gap-3 text-xs">
        <div>
          <dt className="text-muted-foreground">Falta</dt>
          <dd className="num mt-0.5 text-sm font-semibold">{formatMoney(goal.remainingAmount)}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Guardado este mês</dt>
          <dd className="num mt-0.5 text-sm font-semibold">{formatMoney(goal.savedThisMonth)}</dd>
        </div>
      </dl>

      <p className="mt-3 flex min-h-5 items-center gap-1.5 text-xs text-muted-foreground">
        {goal.targetDate && <CalendarDays className="size-3.5 shrink-0" />}
        {goal.status === "COMPLETED"
          ? "Meta alcançada — parabéns!"
          : goal.monthlyNeeded !== null && goal.targetDate
            ? `Guarde ${formatMoney(goal.monthlyNeeded)}/mês até ${monthYear(goal.targetDate)}`
            : goal.status === "OVERDUE" && goal.targetDate
              ? `O prazo (${formatDate(goal.targetDate)}) passou — ajuste a data ou o valor`
              : "Sem prazo definido"}
      </p>

      {!goal.archived && (
        <div className="mt-4 grid grid-cols-2 gap-2 border-t border-border/60 pt-4">
          <Button
            variant="outline"
            size="sm"
            onClick={() => onMove(-1)}
            disabled={goal.savedAmount <= 0}
          >
            <TrendingDown /> Resgatar
          </Button>
          <Button size="sm" onClick={() => onMove(1)}>
            <TrendingUp /> Guardar
          </Button>
        </div>
      )}

      <Dialog open={confirmDelete} onOpenChange={setConfirmDelete}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Excluir “{goal.name}”?</DialogTitle>
            <DialogDescription>
              A meta e todo o histórico de aportes serão apagados. Nenhum lançamento ou saldo de
              conta é afetado — a meta é só um cofrinho virtual. Se quiser guardar o histórico,
              arquive em vez de excluir.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="ghost" onClick={() => setConfirmDelete(false)}>
              Voltar
            </Button>
            <Button
              variant="destructive"
              disabled={deleteGoal.isPending}
              onClick={() =>
                deleteGoal.mutate(goal.id, { onSuccess: () => setConfirmDelete(false) })
              }
            >
              Excluir meta
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </article>
  );
}

function MenuItem({
  icon,
  children,
  onClick,
  destructive,
}: {
  icon: React.ReactNode;
  children: React.ReactNode;
  onClick: () => void;
  destructive?: boolean;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "flex w-full items-center gap-2.5 rounded-md px-2.5 py-2 text-left text-sm",
        destructive ? "text-destructive hover:bg-destructive/10" : "hover:bg-accent",
      )}
    >
      {icon}
      {children}
    </button>
  );
}

function GoalFormDialog({
  goal,
  open,
  onOpenChange,
}: {
  goal?: Goal;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[92dvh] overflow-y-auto sm:max-w-lg">
        {open && (
          <GoalForm key={goal?.id ?? "new"} goal={goal} onDone={() => onOpenChange(false)} />
        )}
      </DialogContent>
    </Dialog>
  );
}

function GoalForm({ goal, onDone }: { goal?: Goal; onDone: () => void }) {
  const saveGoal = useSaveGoal();
  const [name, setName] = useState(goal?.name ?? "");
  const [target, setTarget] = useState(goal ? String(goal.targetAmount) : "");
  const [targetDate, setTargetDate] = useState(goal?.targetDate ?? "");
  const [color, setColor] = useState<string>(goal?.color ?? CATEGORY_COLORS[4]);
  const [icon, setIcon] = useState<string>(goal?.icon ?? "piggy-bank");
  const [error, setError] = useState<string | null>(null);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    const amount = Number(target);
    if (!name.trim()) return setError("Dê um nome para a meta.");
    if (!(amount > 0)) return setError("Informe um valor-alvo maior que zero.");
    setError(null);
    const payload: GoalPayload = {
      name: name.trim(),
      targetAmount: amount,
      targetDate: targetDate || null,
      color,
      icon,
      archived: goal?.archived ?? false,
    };
    await saveGoal.mutateAsync({ id: goal?.id, payload }).then(onDone, () => undefined);
  }

  return (
    <>
      <DialogHeader>
        <DialogTitle>{goal ? "Editar meta" : "Nova meta"}</DialogTitle>
      </DialogHeader>
      <form className="space-y-4" onSubmit={(e) => void submit(e)} noValidate>
        <div className="space-y-1.5">
          <Label htmlFor="goal-name">Nome</Label>
          <Input
            id="goal-name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            maxLength={120}
            placeholder="Ex.: Reserva de emergência"
            autoFocus
          />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1.5">
            <Label htmlFor="goal-target">Valor-alvo</Label>
            <Input
              id="goal-target"
              type="number"
              inputMode="decimal"
              min={0}
              step="0.01"
              value={target}
              onChange={(e) => setTarget(e.target.value)}
              className="num"
              placeholder="R$ 0,00"
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="goal-date">Prazo (opcional)</Label>
            <Input
              id="goal-date"
              type="date"
              value={targetDate}
              onChange={(e) => setTargetDate(e.target.value)}
            />
          </div>
        </div>
        <div className="space-y-1.5">
          <Label>Cor</Label>
          <ColorSwatchPicker value={color} onChange={setColor} />
        </div>
        <div className="space-y-1.5">
          <Label>Ícone</Label>
          <IconPicker value={icon} onChange={setIcon} names={GOAL_ICONS} />
        </div>
        {error && (
          <p
            role="alert"
            className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive"
          >
            {error}
          </p>
        )}
        <DialogFooter>
          <Button type="submit" disabled={saveGoal.isPending} className="min-w-28">
            {saveGoal.isPending ? "Salvando…" : "Salvar"}
          </Button>
        </DialogFooter>
      </form>
    </>
  );
}

function MoveDialog({
  state,
  onClose,
}: {
  state: { goal: Goal; direction: 1 | -1 } | null;
  onClose: () => void;
}) {
  const contribute = useContribute();
  const [amount, setAmount] = useState("");
  const [note, setNote] = useState("");
  const deposit = state?.direction === 1;

  function close() {
    setAmount("");
    setNote("");
    onClose();
  }

  function submit(e: React.FormEvent) {
    e.preventDefault();
    const value = Number(amount);
    if (!state || !(value > 0)) return;
    contribute.mutate(
      { goalId: state.goal.id, amount: value * state.direction, note },
      { onSuccess: close },
    );
  }

  return (
    <Dialog open={state !== null} onOpenChange={(o) => !o && close()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{deposit ? "Guardar na meta" : "Resgatar da meta"}</DialogTitle>
          <DialogDescription>
            {state &&
              (deposit
                ? `Quanto você separou para “${state.goal.name}”?`
                : `Guardado hoje: ${formatMoney(state.goal.savedAmount)}.`)}
          </DialogDescription>
        </DialogHeader>
        <form className="space-y-4" onSubmit={submit}>
          <div className="space-y-1.5">
            <Label htmlFor="move-amount">Valor</Label>
            <Input
              id="move-amount"
              type="number"
              inputMode="decimal"
              min={0}
              step="0.01"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              className="num h-11 text-lg font-semibold"
              autoFocus
              placeholder="R$ 0,00"
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="move-note">Observação (opcional)</Label>
            <Input
              id="move-note"
              value={note}
              onChange={(e) => setNote(e.target.value)}
              maxLength={255}
              placeholder={deposit ? "Ex.: sobra do salário" : "Ex.: conserto do carro"}
            />
          </div>
          <DialogFooter>
            <Button
              type="submit"
              variant={deposit ? "default" : "destructive"}
              disabled={contribute.isPending || !(Number(amount) > 0)}
            >
              {deposit ? "Guardar" : "Resgatar"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function HistoryDialog({ goal, onClose }: { goal: Goal | null; onClose: () => void }) {
  const { data: contributions, isPending } = useGoalContributions(goal?.id);
  const remove = useRemoveContribution();

  return (
    <Dialog open={goal !== null} onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Histórico de “{goal?.name}”</DialogTitle>
          <DialogDescription>Aportes e resgates, do mais recente ao mais antigo.</DialogDescription>
        </DialogHeader>
        {isPending ? (
          <Skeleton className="h-40 rounded-xl" />
        ) : !contributions || contributions.length === 0 ? (
          <p className="py-6 text-center text-sm text-muted-foreground">
            Nenhuma movimentação ainda.
          </p>
        ) : (
          <ul className="max-h-[50dvh] divide-y divide-border/60 overflow-y-auto">
            {contributions.map((c) => (
              <li key={c.id} className="flex items-center gap-3 py-2.5">
                <div className="min-w-0 flex-1">
                  <div className="text-sm font-medium">{c.amount > 0 ? "Aporte" : "Resgate"}</div>
                  <div className="truncate text-xs text-muted-foreground">
                    {formatDate(c.date)}
                    {c.note ? ` · ${c.note}` : ""}
                  </div>
                </div>
                <span
                  className={cn(
                    "num text-sm font-bold",
                    c.amount > 0 ? "text-success" : "text-destructive",
                  )}
                >
                  {c.amount > 0 ? "+" : "−"}
                  {formatMoney(Math.abs(c.amount))}
                </span>
                <Button
                  variant="ghost"
                  size="icon"
                  className="size-8 text-muted-foreground hover:text-destructive"
                  aria-label="Remover movimentação"
                  disabled={remove.isPending}
                  onClick={() => goal && remove.mutate({ goalId: goal.id, contributionId: c.id })}
                >
                  <Trash2 className="size-4" />
                </Button>
              </li>
            ))}
          </ul>
        )}
      </DialogContent>
    </Dialog>
  );
}
