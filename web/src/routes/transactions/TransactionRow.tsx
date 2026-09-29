import { useState } from "react";
import {
  ArrowDownLeft,
  ArrowUpRight,
  Check,
  Layers,
  MoreHorizontal,
  Paperclip,
  Pencil,
  Repeat,
  XCircle,
} from "lucide-react";
import type { Account } from "@/api/accounts";
import type { Category } from "@/api/categories";
import type { Tag } from "@/api/tags";
import type { EditScope, Transaction } from "@/api/transactions";
import { CategoryIcon } from "@/components/CategoryIcon";
import { TagChip } from "@/components/TagPicker";
import { memberLabel, useHousehold } from "@/hooks/useHousehold";
import { AttachmentsDialog } from "./AttachmentsDialog";
import { Button } from "@/components/ui/button";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { cn } from "@/lib/utils";
import { formatMoney } from "@/lib/money";
import { useCancelTransaction, useConfirmTransaction } from "./hooks";

interface TransactionRowProps {
  transaction: Transaction;
  accountsById: Map<string, Account>;
  categoriesById: Map<string, Category>;
  tagsById: Map<string, Tag>;
  onEdit: (transaction: Transaction) => void;
}

/**
 * Uma linha da lista de lançamentos (agrupada por dia na TransactionsPage).
 * Lista em vez de tabela: o mesmo componente serve no celular sem rolagem
 * horizontal, e a linha comporta metadados ricos (categoria, conta, tags,
 * parcela, fixo) sem virar uma dúzia de colunas.
 */
export function TransactionRow({
  transaction,
  accountsById,
  categoriesById,
  tagsById,
  onEdit,
}: TransactionRowProps) {
  const [menuOpen, setMenuOpen] = useState(false);
  const [cancelOpen, setCancelOpen] = useState(false);
  const [attachmentsOpen, setAttachmentsOpen] = useState(false);
  const cancelTransaction = useCancelTransaction();
  const confirm = useConfirmTransaction();

  const account = accountsById.get(transaction.accountId);
  const category = transaction.categoryId ? categoriesById.get(transaction.categoryId) : undefined;
  const isTransfer = transaction.type === "TRANSFER";
  const isOutflow = transaction.type === "EXPENSE" || transaction.transferDirection === "OUT";
  const canceled = transaction.status === "CANCELED";
  const pending = transaction.status === "PENDING";
  const isOccurrence = transaction.recurrenceSeriesId !== null;
  const { data: household } = useHousehold();
  // "Quem lançou" só com mais de uma pessoa no household, e só quando se sabe: lançamentos de
  // antes da Fase 17 e os automáticos (banco, recorrência) não têm autor registrado.
  const authorMember =
    household && household.members.length > 1 && transaction.createdBy
      ? household.members.find((m) => m.userId === transaction.createdBy)
      : undefined;
  const author = authorMember ? memberLabel(authorMember.email, authorMember.you) : null;
  const tags = transaction.tagIds
    .map((id) => tagsById.get(id))
    .filter((t): t is Tag => t !== undefined);

  function cancel(scope: EditScope) {
    cancelTransaction.mutate(
      { id: transaction.id, scope },
      { onSettled: () => setCancelOpen(false) },
    );
  }

  return (
    <li
      className={cn(
        "group flex items-center gap-3 px-4 py-3 transition-colors hover:bg-surface-2",
        canceled && "opacity-55",
      )}
    >
      {isTransfer ? (
        <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-muted text-muted-foreground">
          {transaction.transferDirection === "OUT" ? (
            <ArrowUpRight className="size-4" />
          ) : (
            <ArrowDownLeft className="size-4" />
          )}
        </span>
      ) : (
        <CategoryIcon
          icon={category?.icon}
          color={category?.color}
          className="size-9 [&_svg]:size-4"
        />
      )}

      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-1.5">
          <span className={cn("truncate text-sm font-semibold", canceled && "line-through")}>
            {transaction.description}
          </span>
          {isOccurrence && (
            <Repeat
              className="size-3.5 shrink-0 text-muted-foreground"
              aria-label="Lançamento fixo"
            />
          )}
        </div>
        <div className="mt-0.5 flex flex-wrap items-center gap-x-1.5 gap-y-1 text-xs text-muted-foreground">
          <span>
            {isTransfer
              ? `Transferência (${transaction.transferDirection === "OUT" ? "saída" : "entrada"})`
              : (category?.name ?? "Sem categoria")}
          </span>
          <span aria-hidden>·</span>
          <span className="truncate">{account?.name ?? "Conta removida"}</span>
          {transaction.attachmentCount > 0 && (
            <button
              type="button"
              onClick={() => setAttachmentsOpen(true)}
              className="inline-flex items-center gap-0.5 rounded hover:text-foreground"
              aria-label={`${transaction.attachmentCount} comprovante(s)`}
              title="Ver comprovantes"
            >
              <Paperclip className="size-3" /> {transaction.attachmentCount}
            </button>
          )}
          {author && (
            <>
              <span aria-hidden>·</span>
              <span title="Quem lançou">por {author}</span>
            </>
          )}
          {transaction.installmentNumber && (
            <span className="inline-flex items-center gap-0.5">
              <Layers className="size-3" /> {transaction.installmentNumber}/
              {transaction.installmentTotal}
            </span>
          )}
          {tags.map((tag) => (
            <TagChip key={tag.id} tag={tag} className="py-0 text-[10px]" />
          ))}
        </div>
      </div>

      <div className="flex shrink-0 flex-col items-end gap-1">
        <span
          className={cn(
            "num text-sm font-bold",
            canceled ? "text-muted-foreground" : isOutflow ? "text-destructive" : "text-success",
          )}
        >
          {isOutflow ? "−" : "+"}
          {formatMoney(transaction.amount)}
        </span>
        {pending && (
          <button
            type="button"
            onClick={() => confirm.mutate(transaction.id)}
            disabled={confirm.isPending}
            className="inline-flex items-center gap-1 rounded-full bg-warning/12 px-2 py-0.5 text-[11px] font-semibold text-warning transition-colors hover:bg-success/15 hover:text-success"
            title="Efetivar"
          >
            <Check className="size-3" /> Previsto · efetivar
          </button>
        )}
        {canceled && (
          <span className="text-[11px] font-medium text-muted-foreground">Cancelado</span>
        )}
      </div>

      {!canceled && (
        <Popover open={menuOpen} onOpenChange={setMenuOpen}>
          <PopoverTrigger asChild>
            <Button
              variant="ghost"
              size="icon"
              className="size-8 shrink-0 text-muted-foreground"
              aria-label="Ações do lançamento"
            >
              <MoreHorizontal className="size-4" />
            </Button>
          </PopoverTrigger>
          <PopoverContent className="w-52 p-1.5">
            {!isTransfer && (
              <MenuButton
                icon={<Pencil className="size-4" />}
                onClick={() => {
                  setMenuOpen(false);
                  onEdit(transaction);
                }}
              >
                Editar
              </MenuButton>
            )}
            {pending && (
              <MenuButton
                icon={<Check className="size-4" />}
                onClick={() =>
                  confirm.mutate(transaction.id, { onSettled: () => setMenuOpen(false) })
                }
              >
                {isOutflow ? "Marcar como pago" : "Marcar como recebido"}
              </MenuButton>
            )}
            {!isTransfer && (
              <MenuButton
                icon={<Paperclip className="size-4" />}
                onClick={() => {
                  setMenuOpen(false);
                  setAttachmentsOpen(true);
                }}
              >
                Comprovantes
                {transaction.attachmentCount > 0 ? ` (${transaction.attachmentCount})` : ""}
              </MenuButton>
            )}
            <MenuButton
              destructive
              icon={<XCircle className="size-4" />}
              onClick={() => {
                setMenuOpen(false);
                setCancelOpen(true);
              }}
            >
              {isTransfer ? "Cancelar transferência" : "Cancelar lançamento"}
            </MenuButton>
          </PopoverContent>
        </Popover>
      )}

      {attachmentsOpen && (
        <AttachmentsDialog
          transaction={transaction}
          open={attachmentsOpen}
          onOpenChange={setAttachmentsOpen}
        />
      )}

      <Dialog open={cancelOpen} onOpenChange={setCancelOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Cancelar “{transaction.description}”?</DialogTitle>
            <DialogDescription>
              {isTransfer
                ? "As duas pernas da transferência serão canceladas. O lançamento fica no histórico como cancelado (estorno), sem afetar o saldo."
                : isOccurrence
                  ? "Este é um lançamento fixo. Cancele só esta ocorrência ou encerre a repetição a partir dela."
                  : "O lançamento fica no histórico como cancelado (estorno) e deixa de contar no saldo e no orçamento."}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="ghost" onClick={() => setCancelOpen(false)}>
              Voltar
            </Button>
            {isOccurrence && (
              <Button
                variant="outline"
                disabled={cancelTransaction.isPending}
                onClick={() => cancel("THIS_AND_FUTURE")}
              >
                Este e os próximos
              </Button>
            )}
            <Button
              variant="destructive"
              disabled={cancelTransaction.isPending}
              onClick={() => cancel("THIS")}
            >
              {isOccurrence ? "Só este" : "Cancelar lançamento"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </li>
  );
}

function MenuButton({
  icon,
  children,
  onClick,
  destructive = false,
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
        "flex w-full items-center gap-2.5 rounded-md px-2.5 py-2 text-left text-sm transition-colors",
        destructive ? "text-destructive hover:bg-destructive/10" : "hover:bg-accent",
      )}
    >
      {icon}
      {children}
    </button>
  );
}
