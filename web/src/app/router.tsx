import { createBrowserRouter } from "react-router-dom";
import { AppShell } from "./layout/AppShell";
import { ProtectedRoute } from "./layout/ProtectedRoute";
import { RouteErrorBoundary } from "./layout/RouteErrorBoundary";
import { LoginPage } from "@/routes/auth/LoginPage";
import { RegisterPage } from "@/routes/auth/RegisterPage";
import { NotFoundPage } from "@/routes/NotFoundPage";
import { DashboardPage } from "@/routes/dashboard/DashboardPage";
import { AccountsPage } from "@/routes/accounts/AccountsPage";
import { CategoriesPage } from "@/routes/categories/CategoriesPage";
import { TransactionsPage } from "@/routes/transactions/TransactionsPage";
import { BillsPage } from "@/routes/bills/BillsPage";
import { BudgetsPage } from "@/routes/budgets/BudgetsPage";
import { CreditCardsPage } from "@/routes/creditcards/CreditCardsPage";
import { CreditCardDetailPage } from "@/routes/creditcards/CreditCardDetailPage";
import { InvoiceDetailPage } from "@/routes/creditcards/InvoiceDetailPage";

export const router = createBrowserRouter([
  {
    path: "/login",
    element: <LoginPage />,
    errorElement: <RouteErrorBoundary />,
  },
  {
    path: "/register",
    element: <RegisterPage />,
    errorElement: <RouteErrorBoundary />,
  },
  {
    element: <ProtectedRoute />,
    errorElement: <RouteErrorBoundary />,
    children: [
      {
        element: <AppShell />,
        children: [
          { index: true, element: <DashboardPage />, errorElement: <RouteErrorBoundary /> },
          { path: "contas", element: <AccountsPage />, errorElement: <RouteErrorBoundary /> },
          { path: "categorias", element: <CategoriesPage />, errorElement: <RouteErrorBoundary /> },
          { path: "lancamentos", element: <TransactionsPage />, errorElement: <RouteErrorBoundary /> },
          { path: "contas-a-pagar", element: <BillsPage />, errorElement: <RouteErrorBoundary /> },
          { path: "orcamento", element: <BudgetsPage />, errorElement: <RouteErrorBoundary /> },
          { path: "cartoes", element: <CreditCardsPage />, errorElement: <RouteErrorBoundary /> },
          { path: "cartoes/:cardId", element: <CreditCardDetailPage />, errorElement: <RouteErrorBoundary /> },
          {
            path: "cartoes/:cardId/faturas/:invoiceId",
            element: <InvoiceDetailPage />,
            errorElement: <RouteErrorBoundary />,
          },
        ],
      },
    ],
  },
  {
    path: "*",
    element: <NotFoundPage />,
  },
]);
