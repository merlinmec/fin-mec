import { Skeleton } from "@/components/ui/skeleton";

/** Placeholder de carregamento pras listas em grid de cards (Orçamento). */
export function CardGridSkeleton({ cards = 3 }: { cards?: number }) {
  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {Array.from({ length: cards }).map((_, i) => (
        <div key={i} className="space-y-3 rounded-xl border border-border bg-card p-4">
          <Skeleton className="h-4 w-2/3" />
          <Skeleton className="h-2 w-full" />
          <Skeleton className="h-4 w-1/2" />
        </div>
      ))}
    </div>
  );
}
