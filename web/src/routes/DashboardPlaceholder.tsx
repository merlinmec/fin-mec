/**
 * Placeholder da FE-0 para a rota raiz autenticada. O Dashboard real (consumindo
 * GET /api/dashboard) chega na FE-8.
 */
export function DashboardPlaceholder() {
  return (
    <div className="space-y-2">
      <h1 className="text-xl font-semibold tracking-tight">Dashboard</h1>
      <p className="text-sm text-muted-foreground">
        Sem conteúdo ainda. As telas de domínio chegam a partir da FE-2.
      </p>
    </div>
  );
}
