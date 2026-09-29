import { useMemo, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ArrowRight,
  CheckCircle2,
  Copy,
  FileUp,
  History,
  Landmark,
  Link2,
  RefreshCw,
  Sparkles,
  Undo2,
  Wand2,
} from "lucide-react";
import { toast } from "sonner";
import {
  commitImport,
  listImports,
  previewImport,
  saveRule,
  undoImport,
  type CommitRow,
  type CsvMapping,
  type ImportPreview,
  type ImportResult,
  type PreviewRow,
  type PreviewStatus,
} from "@/api/imports";
import type { Category } from "@/api/categories";
import { AccountSelect } from "@/components/AccountSelect";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { NativeSelect } from "@/components/ui/native-select";
import { PageHeader, Panel } from "@/components/ui/panel";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Skeleton } from "@/components/ui/skeleton";
import { useAccounts } from "@/hooks/useAccounts";
import { useCategories } from "@/hooks/useCategories";
import { invalidateFinancialViews } from "@/hooks/useFeatureData";
import { formatDate } from "@/lib/dates";
import { getErrorMessage } from "@/lib/errors";
import { formatMoney } from "@/lib/money";
import { cn } from "@/lib/utils";

const ACCEPT = ".ofx,.qfx,.csv,.txt";

/** Rótulo do resumo no topo da revisão, já no plural certo. */
function summaryLabel(status: PreviewStatus, count: number): string {
  const plural = count !== 1;
  switch (status) {
    case "NEW":
      return `${count} ${plural ? "novos" : "novo"}`;
    case "MATCHES_PENDING":
      return `${count} ${plural ? "efetivam previstos" : "efetiva previsto"}`;
    case "POSSIBLE_DUPLICATE":
      return `${count} ${plural ? "possíveis duplicados" : "possível duplicado"}`;
    case "ALREADY_IMPORTED":
      return `${count} já ${plural ? "importados" : "importado"}`;
  }
}

const STATUS_BADGE: Record<PreviewStatus, { label: string; className: string }> = {
  NEW: { label: "Novo", className: "bg-primary/10 text-primary" },
  MATCHES_PENDING: { label: "Efetiva previsto", className: "bg-info/12 text-info" },
  POSSIBLE_DUPLICATE: { label: "Possível duplicado", className: "bg-warning/15 text-warning" },
  ALREADY_IMPORTED: { label: "Já importado", className: "bg-muted text-muted-foreground" },
};

/** Decisão do usuário por linha, a partir da sugestão da pré-visualização. */
interface RowChoice {
  include: boolean;
  categoryId: string | null;
}

function defaultChoice(row: PreviewRow): RowChoice {
  return {
    include: row.status === "NEW" || row.status === "MATCHES_PENDING",
    categoryId: row.suggestedCategoryId,
  };
}

/** Sugestão de texto para uma regra a partir da descrição do banco (tira números e ruído). */
function rulePatternFrom(description: string): string {
  return description
    .replace(/\d+/g, " ")
    .replace(/[*/\\_.-]+/g, " ")
    .replace(
      /\b(compra|cartao|cartão|debito|débito|credito|crédito|pix|enviado|recebido|pag|pagto)\b/gi,
      " ",
    )
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, 60);
}

export function ImportPage() {
  const queryClient = useQueryClient();
  const { data: accounts } = useAccounts();
  const { data: categories } = useCategories();
  const [chosenAccountId, setAccountId] = useState<string | undefined>(undefined);
  // Sem escolha explícita, usa a primeira conta ativa (o seletor vazio parecia quebrado).
  const accountId = chosenAccountId ?? accounts?.find((a) => !a.archived)?.id;
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<ImportPreview | null>(null);
  const [choices, setChoices] = useState<Record<string, RowChoice>>({});
  const [result, setResult] = useState<ImportResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  const analyze = useMutation({
    mutationFn: ({ f, mapping }: { f: File; mapping?: CsvMapping }) =>
      previewImport(f, accountId!, mapping),
    onSuccess: (data) => {
      setPreview(data);
      setChoices(Object.fromEntries(data.rows.map((row) => [row.externalId, defaultChoice(row)])));
      setError(null);
    },
    onError: (err) => setError(getErrorMessage(err, "Não foi possível ler o arquivo.")),
  });

  const commit = useMutation({
    mutationFn: () => {
      const rows: CommitRow[] = preview!.rows.map((row) => {
        const choice = choices[row.externalId];
        const signed = row.type === "EXPENSE" ? -row.amount : row.amount;
        const action =
          row.status === "ALREADY_IMPORTED" || !choice.include
            ? "SKIP"
            : row.status === "MATCHES_PENDING"
              ? "MATCH"
              : "CREATE";
        return {
          externalId: row.externalId,
          date: row.date,
          description: row.description,
          amount: signed,
          action,
          categoryId: choice.categoryId,
          tagIds: row.suggestedTagId ? [row.suggestedTagId] : undefined,
          matchTransactionId: row.matchTransactionId,
        };
      });
      return commitImport({
        accountId: accountId!,
        fileName: preview!.fileName,
        format: preview!.format,
        rows,
      });
    },
    onSuccess: (data) => {
      setResult(data);
      invalidateFinancialViews(queryClient);
      void queryClient.invalidateQueries({ queryKey: ["imports"] });
    },
    onError: (err) => toast.error(getErrorMessage(err, "Não foi possível importar.")),
  });

  function reset() {
    setFile(null);
    setPreview(null);
    setChoices({});
    setResult(null);
    setError(null);
  }

  function pick(f: File | undefined) {
    if (!f) return;
    if (!accountId) {
      setError("Escolha primeiro em qual conta o extrato vai entrar.");
      return;
    }
    setFile(f);
    setResult(null);
    analyze.mutate({ f });
  }

  const selectedCount = preview
    ? preview.rows.filter((r) => r.status !== "ALREADY_IMPORTED" && choices[r.externalId]?.include)
        .length
    : 0;
  const accountName = accounts?.find((a) => a.id === accountId)?.name;

  return (
    <div className="space-y-5">
      <PageHeader
        title="Importar extrato"
        description="Traga os lançamentos do banco em OFX ou CSV. Nada é gravado antes de você revisar."
      />

      {result ? (
        <ImportDone result={result} accountName={accountName} onAgain={reset} />
      ) : !preview ? (
        <Panel>
          <div className="grid gap-5 lg:grid-cols-[18rem_1fr]">
            <div className="space-y-2">
              <span className="text-sm font-medium">1. Conta de destino</span>
              <AccountSelect value={accountId} onValueChange={setAccountId} />
              <p className="text-xs text-muted-foreground">
                OFX é o formato mais confiável: cada lançamento vem com um identificador do banco,
                então reimportar o mesmo período nunca duplica nada.
              </p>
            </div>
            <DropZone disabled={analyze.isPending} busy={analyze.isPending} onFile={pick} />
          </div>
          {error && (
            <p
              role="alert"
              className="mt-4 rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive"
            >
              {error}
            </p>
          )}
        </Panel>
      ) : (
        <Review
          preview={preview}
          choices={choices}
          setChoices={setChoices}
          categories={categories ?? []}
          accountName={accountName}
          busy={analyze.isPending}
          onRemap={(mapping) => file && analyze.mutate({ f: file, mapping })}
          onRefresh={() =>
            file && analyze.mutate({ f: file, mapping: preview.mapping ?? undefined })
          }
          onCancel={reset}
          onCommit={() => commit.mutate()}
          committing={commit.isPending}
          selectedCount={selectedCount}
          error={error}
        />
      )}

      <ImportHistory />
    </div>
  );
}

function DropZone({
  onFile,
  disabled,
  busy,
}: {
  onFile: (f: File | undefined) => void;
  disabled: boolean;
  busy: boolean;
}) {
  const input = useRef<HTMLInputElement>(null);
  const [over, setOver] = useState(false);
  return (
    <div
      onDragOver={(e) => {
        e.preventDefault();
        setOver(true);
      }}
      onDragLeave={() => setOver(false)}
      onDrop={(e) => {
        e.preventDefault();
        setOver(false);
        onFile(e.dataTransfer.files[0]);
      }}
      className={cn(
        "flex flex-col items-center justify-center gap-3 rounded-2xl border-2 border-dashed px-6 py-10 text-center transition-colors",
        over ? "border-primary bg-primary/6" : "border-border bg-surface-2",
      )}
    >
      <span className="flex size-12 items-center justify-center rounded-full bg-primary/10 text-primary">
        {busy ? <RefreshCw className="size-6 animate-spin" /> : <FileUp className="size-6" />}
      </span>
      <div>
        <p className="text-sm font-semibold">
          {busy ? "Lendo o extrato…" : "2. Arraste o arquivo aqui"}
        </p>
        <p className="mt-0.5 text-xs text-muted-foreground">
          OFX, QFX ou CSV · até 2 MB · até 2.000 lançamentos
        </p>
      </div>
      <Button variant="outline" disabled={disabled} onClick={() => input.current?.click()}>
        Escolher arquivo
      </Button>
      <input
        ref={input}
        type="file"
        accept={ACCEPT}
        className="hidden"
        onChange={(e) => {
          onFile(e.target.files?.[0]);
          e.target.value = "";
        }}
      />
    </div>
  );
}

interface ReviewProps {
  preview: ImportPreview;
  choices: Record<string, RowChoice>;
  setChoices: React.Dispatch<React.SetStateAction<Record<string, RowChoice>>>;
  categories: Category[];
  accountName?: string;
  busy: boolean;
  onRemap: (mapping: CsvMapping) => void;
  onRefresh: () => void;
  onCancel: () => void;
  onCommit: () => void;
  committing: boolean;
  selectedCount: number;
  error: string | null;
}

function Review({
  preview,
  choices,
  setChoices,
  categories,
  accountName,
  busy,
  onRemap,
  onRefresh,
  onCancel,
  onCommit,
  committing,
  selectedCount,
  error,
}: ReviewProps) {
  const byType = useMemo(
    () => ({
      EXPENSE: categories.filter((c) => c.type === "EXPENSE"),
      INCOME: categories.filter((c) => c.type === "INCOME"),
    }),
    [categories],
  );
  const update = (id: string, patch: Partial<RowChoice>) =>
    setChoices((prev) => ({ ...prev, [id]: { ...prev[id], ...patch } }));

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-2">
        <span className="text-sm text-muted-foreground">
          <strong className="font-semibold text-foreground">{preview.fileName}</strong> →{" "}
          {accountName}
        </span>
        <div className="flex-1" />
        <SummaryChip status="NEW" count={preview.newCount} />
        <SummaryChip status="MATCHES_PENDING" count={preview.matchCount} />
        <SummaryChip status="POSSIBLE_DUPLICATE" count={preview.possibleDuplicateCount} />
        <SummaryChip status="ALREADY_IMPORTED" count={preview.alreadyImportedCount} />
      </div>

      {preview.format === "CSV" && preview.columns && preview.mapping && (
        <CsvMappingBar
          columns={preview.columns}
          mapping={preview.mapping}
          busy={busy}
          onApply={onRemap}
        />
      )}

      {error && (
        <p className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</p>
      )}

      <ul
        className={cn(
          "divide-y divide-border/60 overflow-hidden rounded-2xl border border-border/60 bg-card shadow-card",
          busy && "opacity-60",
        )}
      >
        {preview.rows.map((row) => {
          const choice = choices[row.externalId];
          const locked = row.status === "ALREADY_IMPORTED";
          const isMatch = row.status === "MATCHES_PENDING";
          return (
            <li
              key={row.externalId}
              className={cn(
                "grid grid-cols-[auto_1fr_auto] items-center gap-x-3 gap-y-2 px-4 py-3 sm:grid-cols-[auto_6rem_1fr_12rem_8rem]",
                (!choice?.include || locked) && "bg-surface-2/60",
              )}
            >
              <input
                type="checkbox"
                aria-label={`Importar ${row.description}`}
                className="size-4 accent-[var(--color-primary)]"
                checked={!locked && !!choice?.include}
                disabled={locked}
                onChange={(e) => update(row.externalId, { include: e.target.checked })}
              />
              <span className="hidden text-xs text-muted-foreground sm:block">
                {formatDate(row.date)}
              </span>
              <div className="min-w-0">
                <div
                  className={cn(
                    "truncate text-sm font-medium",
                    (!choice?.include || locked) && "text-muted-foreground",
                  )}
                >
                  {row.description}
                </div>
                <div className="mt-0.5 flex flex-wrap items-center gap-1.5 text-[11px]">
                  <span className="text-muted-foreground sm:hidden">{formatDate(row.date)}</span>
                  <span
                    className={cn(
                      "rounded-full px-1.5 py-0.5 font-semibold",
                      STATUS_BADGE[row.status].className,
                    )}
                  >
                    {STATUS_BADGE[row.status].label}
                  </span>
                  {isMatch && row.matchDescription && (
                    <span className="inline-flex items-center gap-1 text-muted-foreground">
                      <Link2 className="size-3" /> {row.matchDescription} de{" "}
                      {formatDate(row.matchDate!)} · {formatMoney(row.matchAmount!)}
                    </span>
                  )}
                  {row.status === "POSSIBLE_DUPLICATE" && row.matchDescription && (
                    <span className="inline-flex items-center gap-1 text-muted-foreground">
                      <Copy className="size-3" /> parece com “{row.matchDescription}”
                    </span>
                  )}
                  {row.suggestionSource && !locked && (
                    <span
                      className="inline-flex items-center gap-1 text-muted-foreground"
                      title="Categoria sugerida"
                    >
                      <Sparkles className="size-3" />
                      {row.suggestionSource === "RULE" ? "pela regra" : "pelo histórico"}
                    </span>
                  )}
                </div>
              </div>
              <div className="col-span-3 flex items-center gap-1.5 sm:col-span-1">
                <NativeSelect
                  aria-label="Categoria"
                  className="h-8 text-xs"
                  disabled={locked || isMatch || !choice?.include}
                  value={choice?.categoryId ?? ""}
                  onChange={(e) => update(row.externalId, { categoryId: e.target.value || null })}
                >
                  <option value="">Sem categoria</option>
                  {byType[row.type].map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name}
                    </option>
                  ))}
                </NativeSelect>
                {!locked && !isMatch && (
                  <RuleButton
                    description={row.description}
                    categories={byType[row.type]}
                    defaultCategoryId={choice?.categoryId ?? null}
                    onCreated={onRefresh}
                  />
                )}
              </div>
              <span
                className={cn(
                  "num col-start-3 row-start-1 text-right text-sm font-bold sm:col-start-auto sm:row-start-auto",
                  row.type === "EXPENSE" ? "text-destructive" : "text-success",
                )}
              >
                {row.type === "EXPENSE" ? "−" : "+"}
                {formatMoney(row.amount)}
              </span>
            </li>
          );
        })}
      </ul>

      <div className="sticky bottom-20 z-30 flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-border bg-card/95 p-3 shadow-float backdrop-blur-md lg:bottom-4">
        <p className="text-sm text-muted-foreground">
          {selectedCount === 0
            ? "Nenhuma linha selecionada."
            : `${selectedCount} linha(s) serão gravadas.`}
        </p>
        <div className="flex gap-2">
          <Button variant="ghost" onClick={onCancel} disabled={committing}>
            Cancelar
          </Button>
          <Button onClick={onCommit} disabled={committing || selectedCount === 0}>
            {committing ? "Importando…" : `Importar ${selectedCount}`}
            <ArrowRight />
          </Button>
        </div>
      </div>
    </div>
  );
}

function SummaryChip({ status, count }: { status: PreviewStatus; count: number }) {
  if (count === 0) return null;
  return (
    <span
      className={cn(
        "rounded-full px-2.5 py-1 text-xs font-semibold",
        STATUS_BADGE[status].className,
      )}
    >
      {summaryLabel(status, count)}
    </span>
  );
}

function CsvMappingBar({
  columns,
  mapping,
  busy,
  onApply,
}: {
  columns: string[];
  mapping: CsvMapping;
  busy: boolean;
  onApply: (mapping: CsvMapping) => void;
}) {
  const [draft, setDraft] = useState(mapping);
  const select = (key: "dateColumn" | "descriptionColumn" | "amountColumn", label: string) => (
    <label className="block min-w-36 flex-1 space-y-1">
      <span className="text-xs font-medium text-muted-foreground">{label}</span>
      <NativeSelect
        value={draft[key]}
        onChange={(e) => setDraft({ ...draft, [key]: Number(e.target.value) })}
      >
        {columns.map((c, i) => (
          <option key={i} value={i}>
            {c}
          </option>
        ))}
      </NativeSelect>
    </label>
  );
  return (
    <Panel
      title="Colunas do CSV"
      description="Detectadas automaticamente. Se algo saiu errado (valor na coluna errada, data inválida), ajuste aqui."
    >
      <div className="flex flex-wrap items-end gap-3">
        {select("dateColumn", "Data")}
        {select("descriptionColumn", "Descrição")}
        {select("amountColumn", "Valor")}
        <label className="flex h-9 items-center gap-2 text-sm">
          <input
            type="checkbox"
            className="size-4 accent-[var(--color-primary)]"
            checked={draft.invertSign}
            onChange={(e) => setDraft({ ...draft, invertSign: e.target.checked })}
          />
          Despesas vêm positivas
        </label>
        <Button variant="outline" disabled={busy} onClick={() => onApply(draft)}>
          <RefreshCw /> Reprocessar
        </Button>
      </div>
    </Panel>
  );
}

function RuleButton({
  description,
  categories,
  defaultCategoryId,
  onCreated,
}: {
  description: string;
  categories: Category[];
  defaultCategoryId: string | null;
  onCreated: () => void;
}) {
  const [open, setOpen] = useState(false);
  const [pattern, setPattern] = useState(() => rulePatternFrom(description));
  const [categoryId, setCategoryId] = useState(defaultCategoryId ?? "");
  const mutation = useMutation({
    mutationFn: () => saveRule({ pattern, categoryId }),
    onSuccess: () => {
      toast.success("Regra criada — as sugestões foram atualizadas.");
      setOpen(false);
      onCreated();
    },
    onError: (err) => toast.error(getErrorMessage(err, "Não foi possível criar a regra.")),
  });
  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          variant="ghost"
          size="icon"
          className="size-8 shrink-0 text-muted-foreground"
          aria-label="Criar regra a partir desta linha"
          title="Criar regra"
        >
          <Wand2 className="size-4" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-80 space-y-3 p-3">
        <p className="text-sm font-semibold">Sempre que a descrição contiver…</p>
        <Input value={pattern} onChange={(e) => setPattern(e.target.value)} maxLength={100} />
        <label className="block space-y-1">
          <span className="text-xs text-muted-foreground">…usar a categoria</span>
          <NativeSelect value={categoryId} onChange={(e) => setCategoryId(e.target.value)}>
            <option value="" disabled>
              Escolha uma categoria
            </option>
            {categories.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </NativeSelect>
        </label>
        <p className="text-[11px] text-muted-foreground">
          Maiúsculas, acentos e números são ignorados na comparação.
        </p>
        <Button
          className="w-full"
          disabled={!categoryId || pattern.trim().length < 2 || mutation.isPending}
          onClick={() => mutation.mutate()}
        >
          Criar regra
        </Button>
      </PopoverContent>
    </Popover>
  );
}

function ImportDone({
  result,
  accountName,
  onAgain,
}: {
  result: ImportResult;
  accountName?: string;
  onAgain: () => void;
}) {
  return (
    <Panel>
      <div className="flex flex-col items-center gap-3 py-6 text-center">
        <CheckCircle2 className="size-12 text-success" />
        <div>
          <p className="text-lg font-bold">
            Extrato importado{accountName ? ` em ${accountName}` : ""}
          </p>
          <p className="mt-1 text-sm text-muted-foreground">
            {result.created} lançamento(s) criado(s), {result.matched} previsto(s) efetivado(s),{" "}
            {result.skipped} ignorado(s).
          </p>
        </div>
        <div className="flex flex-wrap justify-center gap-2">
          <Link
            to="/lancamentos"
            className="inline-flex h-9 items-center gap-2 rounded-md bg-primary px-4 text-sm font-medium text-primary-foreground hover:bg-primary/90"
          >
            Ver lançamentos
          </Link>
          <Button variant="outline" onClick={onAgain}>
            Importar outro arquivo
          </Button>
        </div>
      </div>
    </Panel>
  );
}

function ImportHistory() {
  const queryClient = useQueryClient();
  const { data: batches, isPending } = useQuery({ queryKey: ["imports"], queryFn: listImports });
  const { data: accounts } = useAccounts();
  const undo = useMutation({
    mutationFn: (id: string) => undoImport(id),
    onSuccess: (res) => {
      toast.success(`Importação desfeita: ${res.canceled} lançamento(s) cancelado(s).`);
      invalidateFinancialViews(queryClient);
      void queryClient.invalidateQueries({ queryKey: ["imports"] });
    },
    onError: (err) => toast.error(getErrorMessage(err)),
  });

  if (isPending) return <Skeleton className="h-32 rounded-2xl" />;
  if (!batches || batches.length === 0) return null;

  return (
    <Panel
      title="Importações recentes"
      description="Desfazer cancela os lançamentos que a importação criou (previstos efetivados continuam efetivados)."
    >
      <ul className="-mx-1 divide-y divide-border/60">
        {batches.map((b) => (
          <li
            key={b.id}
            className={cn("flex items-center gap-3 px-1 py-2.5", b.undoneAt && "opacity-60")}
          >
            <span
              className="flex size-8 shrink-0 items-center justify-center rounded-full bg-muted text-muted-foreground"
              title={b.format === "BANK_SYNC" ? "Sincronização bancária" : `Arquivo ${b.format}`}
            >
              {b.format === "BANK_SYNC" ? (
                <Landmark className="size-4" />
              ) : (
                <History className="size-4" />
              )}
            </span>
            <div className="min-w-0 flex-1">
              <div className="truncate text-sm font-medium">{b.fileName}</div>
              <div className="text-xs text-muted-foreground">
                {accounts?.find((a) => a.id === b.accountId)?.name ?? "Conta"} ·{" "}
                {new Date(b.createdAt).toLocaleString("pt-BR", {
                  dateStyle: "short",
                  timeStyle: "short",
                })}{" "}
                · {b.createdCount} criado(s), {b.matchedCount} efetivado(s)
                {b.undoneAt ? " · desfeita" : ""}
              </div>
            </div>
            {!b.undoneAt && b.createdCount > 0 && (
              <Button
                variant="outline"
                size="sm"
                disabled={undo.isPending}
                onClick={() => undo.mutate(b.id)}
              >
                <Undo2 /> Desfazer
              </Button>
            )}
          </li>
        ))}
      </ul>
    </Panel>
  );
}
