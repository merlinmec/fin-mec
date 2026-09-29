import { useState } from "react";
import { NavLink, Navigate, useNavigate, useParams } from "react-router-dom";
import { useMutation } from "@tanstack/react-query";
import {
  Monitor,
  Moon,
  Palette,
  Pencil,
  Repeat,
  ShieldCheck,
  Sun,
  Tags,
  Trash2,
  UserX,
  Wand2,
} from "lucide-react";
import { toast } from "sonner";
import { deleteAccount } from "@/api/account-security";
import { RECURRENCE_RULE_LABELS } from "@/api/transactions";
import type { Tag } from "@/api/tags";
import { useAuth } from "@/auth/auth-context";
import { CategoryIcon } from "@/components/CategoryIcon";
import { EmptyState } from "@/components/EmptyState";
import { ColorSwatchPicker } from "@/components/SwatchPickers";
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
import { PageHeader, Panel } from "@/components/ui/panel";
import { Skeleton } from "@/components/ui/skeleton";
import { useAccounts } from "@/hooks/useAccounts";
import { useCategories } from "@/hooks/useCategories";
import {
  useDeleteTag,
  useRecurringSeries,
  useSaveTag,
  useStopRecurringSeries,
  useTags,
} from "@/hooks/useFeatureData";
import { CATEGORY_COLORS } from "@/lib/category-icons";
import { formatDate } from "@/lib/dates";
import { getErrorMessage } from "@/lib/errors";
import { formatMoney } from "@/lib/money";
import { useTheme, type ThemePreference } from "@/lib/theme";
import { cn } from "@/lib/utils";
import { RulesSection } from "./RulesSection";
import { SecuritySection } from "./SecuritySection";

const TABS = [
  { id: "seguranca", label: "Segurança", icon: ShieldCheck },
  { id: "fixos", label: "Lançamentos fixos", icon: Repeat },
  { id: "tags", label: "Tags", icon: Tags },
  { id: "regras", label: "Regras automáticas", icon: Wand2 },
  { id: "aparencia", label: "Aparência", icon: Palette },
  { id: "conta", label: "Excluir conta", icon: UserX },
] as const;

type TabId = (typeof TABS)[number]["id"];

export function SettingsPage() {
  const { tab } = useParams();
  if (!tab || !TABS.some((t) => t.id === tab)) {
    return <Navigate to="/configuracoes/seguranca" replace />;
  }
  const active = tab as TabId;

  return (
    <div className="space-y-5">
      <PageHeader
        title="Configurações"
        description="Segurança da conta, automações e preferências."
      />
      <div className="grid gap-5 lg:grid-cols-[13rem_1fr]">
        <nav
          className="-mx-4 flex gap-1 overflow-x-auto px-4 pb-1 lg:mx-0 lg:flex-col lg:overflow-visible lg:px-0"
          aria-label="Seções das configurações"
        >
          {TABS.map((t) => (
            <NavLink
              key={t.id}
              to={`/configuracoes/${t.id}`}
              className={({ isActive }) =>
                cn(
                  "flex shrink-0 items-center gap-2.5 rounded-xl px-3 py-2 text-sm font-medium whitespace-nowrap transition-colors",
                  isActive
                    ? "bg-card text-foreground shadow-card"
                    : "text-muted-foreground hover:bg-card/60 hover:text-foreground",
                  t.id === "conta" && !isActive && "text-destructive/80",
                )
              }
            >
              <t.icon className="size-4" /> {t.label}
            </NavLink>
          ))}
        </nav>
        <div className="min-w-0">
          {active === "seguranca" && <SecuritySection />}
          {active === "fixos" && <RecurringSection />}
          {active === "tags" && <TagsSection />}
          {active === "regras" && <RulesSection />}
          {active === "aparencia" && <AppearanceSection />}
          {active === "conta" && <DeleteAccountSection />}
        </div>
      </div>
    </div>
  );
}

function RecurringSection() {
  const { data: series, isPending } = useRecurringSeries();
  const { data: accounts } = useAccounts();
  const { data: categories } = useCategories();
  const stop = useStopRecurringSeries();
  const [stopping, setStopping] = useState<string | null>(null);
  const accountName = (id: string) => accounts?.find((a) => a.id === id)?.name ?? "Conta removida";
  const category = (id: string | null) => categories?.find((c) => c.id === id);
  const active = (series ?? []).filter((s) => s.active);
  const ended = (series ?? []).filter((s) => !s.active);

  return (
    <Panel
      title="Lançamentos fixos"
      description="Salário, aluguel, assinaturas: o fin-mec gera as próximas ocorrências como previstas, 12 meses à frente."
    >
      {isPending ? (
        <Skeleton className="h-40 rounded-xl" />
      ) : active.length === 0 && ended.length === 0 ? (
        <EmptyState
          icon={Repeat}
          title="Nenhum lançamento fixo"
          description="Ao criar um lançamento, escolha “Repetir” para ele virar fixo."
        />
      ) : (
        <ul className="-mx-1 divide-y divide-border/60">
          {[...active, ...ended].map((s) => {
            const cat = category(s.categoryId);
            return (
              <li
                key={s.id}
                className={cn("flex items-center gap-3 px-1 py-3", !s.active && "opacity-60")}
              >
                <CategoryIcon
                  icon={cat?.icon}
                  color={cat?.color}
                  className="size-9 [&_svg]:size-4"
                />
                <div className="min-w-0 flex-1">
                  <div className="truncate text-sm font-semibold">{s.description}</div>
                  <div className="text-xs text-muted-foreground">
                    {RECURRENCE_RULE_LABELS[s.recurrenceRule]} · {accountName(s.accountId)}
                    {s.active
                      ? s.nextPendingDate
                        ? ` · próxima em ${formatDate(s.nextPendingDate)}`
                        : ""
                      : s.endDate
                        ? ` · encerrado em ${formatDate(s.endDate)}`
                        : " · encerrado"}
                  </div>
                </div>
                <span
                  className={cn(
                    "num text-sm font-bold",
                    s.type === "EXPENSE" ? "text-destructive" : "text-success",
                  )}
                >
                  {s.type === "EXPENSE" ? "−" : "+"}
                  {formatMoney(s.amount)}
                </span>
                {s.active && (
                  <Button variant="outline" size="sm" onClick={() => setStopping(s.id)}>
                    Encerrar
                  </Button>
                )}
              </li>
            );
          })}
        </ul>
      )}

      <Dialog open={stopping !== null} onOpenChange={(o) => !o && setStopping(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Parar de repetir?</DialogTitle>
            <DialogDescription>
              As ocorrências previstas de hoje em diante serão canceladas. As já efetivadas
              continuam no histórico.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="ghost" onClick={() => setStopping(null)}>
              Voltar
            </Button>
            <Button
              variant="destructive"
              disabled={stop.isPending}
              onClick={() =>
                stopping && stop.mutate(stopping, { onSuccess: () => setStopping(null) })
              }
            >
              Encerrar lançamento fixo
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Panel>
  );
}

function TagsSection() {
  const { data: tags, isPending } = useTags();
  const saveTag = useSaveTag();
  const deleteTag = useDeleteTag();
  const [editing, setEditing] = useState<Tag | "new" | null>(null);
  const [name, setName] = useState("");
  const [color, setColor] = useState<string>(CATEGORY_COLORS[2]);
  const [deleting, setDeleting] = useState<Tag | null>(null);

  function open(tag: Tag | "new") {
    setEditing(tag);
    setName(tag === "new" ? "" : tag.name);
    setColor(tag === "new" ? CATEGORY_COLORS[2] : (tag.color ?? CATEGORY_COLORS[2]));
  }

  function save(e: React.FormEvent) {
    e.preventDefault();
    if (!name.trim() || editing === null) return;
    saveTag.mutate(
      { id: editing === "new" ? undefined : editing.id, payload: { name: name.trim(), color } },
      {
        onSuccess: () => {
          toast.success("Tag salva.");
          setEditing(null);
        },
      },
    );
  }

  return (
    <Panel
      title="Tags"
      description="Marque lançamentos de qualquer categoria: “viagem-2026”, “reembolsável”, “casamento”."
      action={
        <Button size="sm" onClick={() => open("new")}>
          Nova tag
        </Button>
      }
    >
      {isPending ? (
        <Skeleton className="h-32 rounded-xl" />
      ) : !tags || tags.length === 0 ? (
        <EmptyState
          icon={Tags}
          title="Nenhuma tag ainda"
          description="Crie aqui ou direto no formulário de lançamento."
        />
      ) : (
        <ul className="grid gap-2 sm:grid-cols-2">
          {tags.map((tag) => (
            <li
              key={tag.id}
              className="flex items-center gap-3 rounded-xl border border-border/60 bg-surface-2 px-3 py-2.5"
            >
              <span
                className="size-3 shrink-0 rounded-full"
                style={{ backgroundColor: tag.color ?? "var(--color-muted-foreground)" }}
              />
              <div className="min-w-0 flex-1">
                <div className="truncate text-sm font-semibold">{tag.name}</div>
                <div className="text-xs text-muted-foreground">
                  {tag.usageCount} lançamento{tag.usageCount === 1 ? "" : "s"}
                </div>
              </div>
              <Button
                variant="ghost"
                size="icon"
                className="size-8"
                aria-label={`Editar ${tag.name}`}
                onClick={() => open(tag)}
              >
                <Pencil className="size-4" />
              </Button>
              <Button
                variant="ghost"
                size="icon"
                className="size-8 hover:text-destructive"
                aria-label={`Excluir ${tag.name}`}
                onClick={() => setDeleting(tag)}
              >
                <Trash2 className="size-4" />
              </Button>
            </li>
          ))}
        </ul>
      )}

      <Dialog open={editing !== null} onOpenChange={(o) => !o && setEditing(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{editing === "new" ? "Nova tag" : "Editar tag"}</DialogTitle>
          </DialogHeader>
          <form className="space-y-4" onSubmit={save}>
            <div className="space-y-1.5">
              <Label htmlFor="tag-name">Nome</Label>
              <Input
                id="tag-name"
                value={name}
                onChange={(e) => setName(e.target.value)}
                maxLength={50}
                autoFocus
              />
            </div>
            <div className="space-y-1.5">
              <Label>Cor</Label>
              <ColorSwatchPicker value={color} onChange={setColor} />
            </div>
            <DialogFooter>
              <Button type="submit" disabled={!name.trim() || saveTag.isPending}>
                Salvar
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={deleting !== null} onOpenChange={(o) => !o && setDeleting(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Excluir a tag “{deleting?.name}”?</DialogTitle>
            <DialogDescription>
              Ela sai de {deleting?.usageCount ?? 0} lançamento(s). Os lançamentos continuam
              intactos.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="ghost" onClick={() => setDeleting(null)}>
              Voltar
            </Button>
            <Button
              variant="destructive"
              disabled={deleteTag.isPending}
              onClick={() =>
                deleting && deleteTag.mutate(deleting.id, { onSuccess: () => setDeleting(null) })
              }
            >
              Excluir tag
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Panel>
  );
}

const THEMES: { value: ThemePreference; label: string; description: string; icon: typeof Sun }[] = [
  { value: "light", label: "Claro", description: "Fundo claro o tempo todo", icon: Sun },
  { value: "dark", label: "Escuro", description: "Mais confortável à noite", icon: Moon },
  { value: "system", label: "Sistema", description: "Segue o seu dispositivo", icon: Monitor },
];

function AppearanceSection() {
  const { preference, setPreference } = useTheme();
  return (
    <Panel title="Tema" description="A preferência fica salva neste navegador.">
      <div className="grid gap-3 sm:grid-cols-3" role="radiogroup" aria-label="Tema">
        {THEMES.map((t) => (
          <button
            key={t.value}
            type="button"
            role="radio"
            aria-checked={preference === t.value}
            onClick={() => setPreference(t.value)}
            className={cn(
              "flex flex-col items-start gap-2 rounded-2xl border p-4 text-left transition-all",
              preference === t.value
                ? "border-primary bg-primary/6 ring-2 ring-primary/30"
                : "border-border hover:border-primary/40",
            )}
          >
            <t.icon
              className={cn(
                "size-5",
                preference === t.value ? "text-primary" : "text-muted-foreground",
              )}
            />
            <span className="text-sm font-semibold">{t.label}</span>
            <span className="text-xs text-muted-foreground">{t.description}</span>
          </button>
        ))}
      </div>
    </Panel>
  );
}

const CONFIRM_WORD = "EXCLUIR";

function DeleteAccountSection() {
  const { user, refresh } = useAuth();
  const navigate = useNavigate();
  const [password, setPassword] = useState("");
  const [code, setCode] = useState("");
  const [typed, setTyped] = useState("");
  const [error, setError] = useState<string | null>(null);
  const mutation = useMutation({
    mutationFn: () => deleteAccount(password, code),
    onSuccess: async () => {
      toast.success("Sua conta e todos os seus dados foram excluídos.");
      await refresh();
      void navigate("/login", { replace: true });
    },
    onError: (e) => setError(getErrorMessage(e, "Não foi possível excluir a conta.")),
  });

  return (
    <Panel title="Excluir conta" className="border-destructive/30">
      <div className="space-y-4">
        <p className="text-sm text-muted-foreground">
          Apaga definitivamente sua conta e{" "}
          <strong className="font-semibold text-foreground">todos</strong> os dados financeiros:
          contas, lançamentos, cartões, orçamentos, metas e histórico. Não há como desfazer. É o seu
          direito de eliminação de dados (LGPD, art. 18). Se quiser uma cópia antes, exporte seus
          lançamentos em CSV.
        </p>
        <form
          className="grid gap-3 sm:max-w-md"
          onSubmit={(e) => {
            e.preventDefault();
            setError(null);
            mutation.mutate();
          }}
        >
          <div className="space-y-1.5">
            <Label htmlFor="del-password">Senha</Label>
            <Input
              id="del-password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </div>
          {user?.mfaEnabled && (
            <div className="space-y-1.5">
              <Label htmlFor="del-code">Código do app ou de recuperação</Label>
              <Input
                id="del-code"
                autoComplete="one-time-code"
                value={code}
                onChange={(e) => setCode(e.target.value)}
              />
            </div>
          )}
          <div className="space-y-1.5">
            <Label htmlFor="del-confirm">
              Digite <strong>{CONFIRM_WORD}</strong> para confirmar
            </Label>
            <Input
              id="del-confirm"
              value={typed}
              onChange={(e) => setTyped(e.target.value)}
              autoComplete="off"
            />
          </div>
          {error && (
            <p
              role="alert"
              className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive"
            >
              {error}
            </p>
          )}
          <Button
            type="submit"
            variant="destructive"
            disabled={typed !== CONFIRM_WORD || !password || mutation.isPending}
          >
            <UserX /> Excluir minha conta para sempre
          </Button>
        </form>
      </div>
    </Panel>
  );
}
