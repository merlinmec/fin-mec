import { createBrowserRouter } from "react-router-dom";
import { AppShell } from "./layout/AppShell";
import { ProtectedRoute } from "./layout/ProtectedRoute";
import { LoginPage } from "@/routes/auth/LoginPage";
import { RegisterPage } from "@/routes/auth/RegisterPage";
import { DashboardPlaceholder } from "@/routes/DashboardPlaceholder";
import { AccountsPage } from "@/routes/accounts/AccountsPage";
import { CategoriesPage } from "@/routes/categories/CategoriesPage";
import { TransactionsPage } from "@/routes/transactions/TransactionsPage";
import { BudgetsPage } from "@/routes/budgets/BudgetsPage";

export const router = createBrowserRouter([
  {
    path: "/login",
    element: <LoginPage />,
  },
  {
    path: "/register",
    element: <RegisterPage />,
  },
  {
    element: <ProtectedRoute />,
    children: [
      {
        element: <AppShell />,
        children: [
          { index: true, element: <DashboardPlaceholder /> },
          { path: "contas", element: <AccountsPage /> },
          { path: "categorias", element: <CategoriesPage /> },
          { path: "lancamentos", element: <TransactionsPage /> },
          { path: "orcamento", element: <BudgetsPage /> },
        ],
      },
    ],
  },
]);
