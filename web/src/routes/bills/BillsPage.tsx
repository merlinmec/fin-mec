import { useMemo, useState } from "react";
import type { Bill, BillStatus } from "@/api/bills";
import { BILL_STATUS_LABELS } from "@/api/bills";
import { useCategories } from "@/hooks/useCategories";
import { Button } from "@/components/ui/button";
import { NativeSelect } from "@/components/ui/native-select";
import { useBills } from "./hooks";
import { BillRow } from "./BillRow";
import { BillFormDialog } from "./BillFormDialog";
import { PayBillDialog } from "./PayBillDialog";

export function BillsPage() {
  const [status, setStatus] = useState<BillStatus | "">("");
  const [formOpen, setFormOpen] = useState(false);
  const [editingBill, setEditingBill] = useState<Bill | undefined>(undefined);
  const [payingBill, setPayingBill] = useState<Bill | undefined>(undefined);
  const [payOpen, setPayOpen] = useState(false);

  const { data: bills, isPending, isError } = useBills(status || undefined);
  const { data: categories } = useCategories();
  const categoriesById = useMemo(() => new Map((categories ?? []).map((c) => [c.id, c])), [categories]);

  function openCreate() {
    setEditingBill(undefined);
    setFormOpen(true);
  }

  function openEdit(bill: Bill) {
    setEditingBill(bill);
    setFormOpen(true);
  }

  function openPay(bill: Bill) {
    setPayingBill(bill);
    setPayOpen(true);
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-xl font-semibold tracking-tight">Contas a pagar</h1>
        <div className="flex items-center gap-3">
          <NativeSelect value={status} onChange={(e) => setStatus(e.target.value as BillStatus | "")} className="w-40">
            <option value="">Todos os status</option>
            {(Object.keys(BILL_STATUS_LABELS) as BillStatus[]).map((s) => (
              <option key={s} value={s}>
                {BILL_STATUS_LABELS[s]}
              </option>
            ))}
          </NativeSelect>
          <Button onClick={openCreate}>Nova conta a pagar</Button>
        </div>
      </div>

      {isPending && <p className="text-sm text-muted-foreground">Carregando contas a pagar…</p>}

      {isError && (
        <p className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
          Não foi possível carregar as contas a pagar. Tente recarregar a página.
        </p>
      )}

      {bills && bills.length === 0 && (
        <div className="rounded-xl border border-dashed border-border p-8 text-center text-sm text-muted-foreground">
          Nenhuma conta a pagar por aqui.
        </div>
      )}

      {bills && bills.length > 0 && (
        <div className="overflow-x-auto rounded-xl border border-border">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-border text-left text-muted-foreground">
                <th className="py-2 pr-4 pl-4 font-medium">Descrição</th>
                <th className="py-2 pr-4 font-medium">Vencimento</th>
                <th className="py-2 pr-4 text-right font-medium">Valor</th>
                <th className="py-2 pr-4 font-medium">Status</th>
                <th className="py-2 pr-4 font-medium" />
              </tr>
            </thead>
            <tbody>
              {bills.map((bill) => (
                <BillRow
                  key={bill.id}
                  bill={bill}
                  category={bill.categoryId ? categoriesById.get(bill.categoryId) : undefined}
                  onEdit={openEdit}
                  onPay={openPay}
                />
              ))}
            </tbody>
          </table>
        </div>
      )}

      <BillFormDialog open={formOpen} onOpenChange={setFormOpen} bill={editingBill} />
      <PayBillDialog open={payOpen} onOpenChange={setPayOpen} bill={payingBill} />
    </div>
  );
}
