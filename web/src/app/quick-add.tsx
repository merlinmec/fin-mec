import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import type { EntryType } from "@/api/transactions";
import { TransactionFormDialog } from "@/routes/transactions/TransactionFormDialog";
import { TransferFormDialog } from "@/routes/transactions/TransferFormDialog";

interface QuickAddValue {
  openEntry: (type?: EntryType) => void;
  openTransfer: () => void;
}

const QuickAddContext = createContext<QuickAddValue | null>(null);

// eslint-disable-next-line react-refresh/only-export-components
export function useQuickAdd(): QuickAddValue {
  const ctx = useContext(QuickAddContext);
  if (!ctx) throw new Error("useQuickAdd deve ser usado dentro de <QuickAddProvider>");
  return ctx;
}

/** true quando o foco está num campo editável — atalho de tecla única não pode disparar ali. */
// eslint-disable-next-line react-refresh/only-export-components
export function isTypingTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  return target.isContentEditable || ["INPUT", "TEXTAREA", "SELECT"].includes(target.tagName);
}

/**
 * Lançamento rápido de qualquer tela: o botão flutuante (mobile), a paleta de
 * comandos e a tecla "N" abrem o mesmo formulário. Lançar é a ação mais
 * frequente do app — não pode depender de navegar até Lançamentos antes.
 */
export function QuickAddProvider({ children }: { children: ReactNode }) {
  const [entryType, setEntryType] = useState<EntryType | null>(null);
  const [transferOpen, setTransferOpen] = useState(false);

  const openEntry = useCallback((type: EntryType = "EXPENSE") => setEntryType(type), []);
  const openTransfer = useCallback(() => setTransferOpen(true), []);

  useEffect(() => {
    function onKeyDown(e: KeyboardEvent) {
      if (
        e.key.toLowerCase() === "n" &&
        !e.ctrlKey &&
        !e.metaKey &&
        !e.altKey &&
        !isTypingTarget(e.target)
      ) {
        if (document.querySelector("[role=dialog]")) return;
        e.preventDefault();
        openEntry("EXPENSE");
      }
    }
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [openEntry]);

  const value = useMemo(() => ({ openEntry, openTransfer }), [openEntry, openTransfer]);

  return (
    <QuickAddContext.Provider value={value}>
      {children}
      <TransactionFormDialog
        open={entryType !== null}
        onOpenChange={(open) => !open && setEntryType(null)}
        defaultType={entryType ?? undefined}
      />
      <TransferFormDialog open={transferOpen} onOpenChange={setTransferOpen} />
    </QuickAddContext.Provider>
  );
}
