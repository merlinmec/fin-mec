import { createBrowserRouter } from "react-router-dom";
import { AppShell } from "./layout/AppShell";
import { ProtectedRoute } from "./layout/ProtectedRoute";
import { LoginPlaceholder } from "@/routes/LoginPlaceholder";
import { DashboardPlaceholder } from "@/routes/DashboardPlaceholder";

export const router = createBrowserRouter([
  {
    path: "/login",
    element: <LoginPlaceholder />,
  },
  {
    element: <ProtectedRoute />,
    children: [
      {
        element: <AppShell />,
        children: [{ index: true, element: <DashboardPlaceholder /> }],
      },
    ],
  },
]);
