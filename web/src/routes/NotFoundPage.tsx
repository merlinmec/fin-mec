import { Link } from "react-router-dom";
import { buttonVariants } from "@/components/ui/button-variants";

export function NotFoundPage() {
  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-3 p-6 text-center">
      <h1 className="text-4xl font-semibold tracking-tight">404</h1>
      <p className="max-w-sm text-sm text-muted-foreground">Essa página não existe.</p>
      <Link to="/" className={buttonVariants({ variant: "default" })}>
        Ir para o início
      </Link>
    </div>
  );
}
