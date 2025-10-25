import type { Route } from "./+types/home";
import { useEffect } from "react";
import { useNavigate } from "react-router";
import { Login } from "../components/Login";
import { useAuth } from "../contexts/AuthContext";

export function meta({}: Route.MetaArgs) {
  return [
    { title: "MyBaaS - Tenant Management" },
    { name: "description", content: "Tenant self-management portal" },
  ];
}

export default function Home() {
  const { isAuthenticated } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    if (isAuthenticated) {
      navigate("/applications");
    }
  }, [isAuthenticated, navigate]);

  return <Login />;
}
