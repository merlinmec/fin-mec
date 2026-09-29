import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Pencil, Trash2, Wand2 } from "lucide-react";
import { toast } from "sonner";
import { deleteRule, listRules, saveRule, type CategorizationRule } from "@/api/imports";
import { CategoryIcon } from "@/components/CategoryIcon";
import { CategorySelect } from "@/components/CategorySelect";
import { EmptyState } from "@/components/EmptyState";
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
import { useCategories } from "@/hooks/useCategories";
import { getErrorMessage } from "@/lib/errors";

/**
 * Regras de categorização automática (Fase 15): aplicadas na pré-visualização da importação
 * de extrato. Também dá pra criar direto de uma linha do extrato, na tela de importação.
 */
export function RulesSection() {
  const queryClient = useQueryClient();
  const { data: rules, isPending } = useQuery({ queryKey: ["rules"], queryFn: listRules });
  const { data: categories } = useCategories();
  const [editing, setEditing] = useState<CategorizationRule | "new" | null>(null);
  const [pattern, setPattern] = useState("");
  const [categoryId, setCategoryId] = useState<string | undefined>(undefined);

  const save = useMutation({
    mutationFn: () =>
      saveRule({ pattern, categoryId: categoryId! }, editing === "new" ? undefined : editing?.id),
    onSuccess: () => {
      toast.success("Regra salva.");
      setEditing(null);
      void queryClient.invalidateQueries({ queryKey: ["rules"] });
    },
    onError: (err) => toast.error(getErrorMessage(err, "Não foi possível salvar a regra.")),
  });
  const remove = useMutation({
    mutationFn: (id: string) => deleteRule(id),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ["rules"] }),
    onError: (err) => toast.error(getErrorMessage(err)),
  });

  function open(rule: CategorizationRule | "new") {
    setEditing(rule);
    setPattern(rule === "new" ? "" : rule.pattern);
    setCategoryId(rule === "new" ? undefined : rule.categoryId);
  }

  return (
    <Panel
      title="Regras de categorização"
      description="Na importação de extrato, a descrição que contém o texto recebe a categoria automaticamente. Com várias regras casando, vale a mais específica."
      action={
        <Button size="sm" onClick={() => open("new")}>
          Nova regra
        </Button>
      }
    >
      {isPending ? (
        <Skeleton className="h-32 rounded-xl" />
      ) : !rules || rules.length === 0 ? (
        <EmptyState
          icon={Wand2}
          title="Nenhuma regra ainda"
          description="Ex.: “uber” → Transporte, “ifood” → Restaurantes. Também dá pra criar direto de uma linha do extrato."
        />
      ) : (
        <ul className="-mx-1 divide-y divide-border/60">
          {rules.map((rule) => {
            const category = categories?.find((c) => c.id === rule.categoryId);
            return (
              <li key={rule.id} className="flex items-center gap-3 px-1 py-2.5">
                <code className="rounded-md bg-muted px-2 py-1 font-mono text-xs">
                  {rule.pattern}
                </code>
                <span className="text-muted-foreground">→</span>
                <span className="flex min-w-0 flex-1 items-center gap-1.5 text-sm">
                  <CategoryIcon icon={category?.icon} color={category?.color} className="size-5" />
                  <span className="truncate">{category?.name ?? "Categoria removida"}</span>
                </span>
                <Button
                  variant="ghost"
                  size="icon"
                  className="size-8"
                  aria-label="Editar regra"
                  onClick={() => open(rule)}
                >
                  <Pencil className="size-4" />
                </Button>
                <Button
                  variant="ghost"
                  size="icon"
                  className="size-8 hover:text-destructive"
                  aria-label="Excluir regra"
                  disabled={remove.isPending}
                  onClick={() => remove.mutate(rule.id)}
                >
                  <Trash2 className="size-4" />
                </Button>
              </li>
            );
          })}
        </ul>
      )}

      <Dialog open={editing !== null} onOpenChange={(o) => !o && setEditing(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{editing === "new" ? "Nova regra" : "Editar regra"}</DialogTitle>
            <DialogDescription>
              Maiúsculas, acentos e números são ignorados na comparação.
            </DialogDescription>
          </DialogHeader>
          <form
            className="space-y-4"
            onSubmit={(e) => {
              e.preventDefault();
              save.mutate();
            }}
          >
            <div className="space-y-1.5">
              <Label htmlFor="rule-pattern">Se a descrição contiver</Label>
              <Input
                id="rule-pattern"
                value={pattern}
                onChange={(e) => setPattern(e.target.value)}
                maxLength={100}
                autoFocus
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="rule-category">Usar a categoria</Label>
              <CategorySelect
                id="rule-category"
                value={categoryId}
                onValueChange={setCategoryId}
                placeholder="Escolha uma categoria"
              />
            </div>
            <DialogFooter>
              <Button
                type="submit"
                disabled={pattern.trim().length < 2 || !categoryId || save.isPending}
              >
                Salvar
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </Panel>
  );
}
