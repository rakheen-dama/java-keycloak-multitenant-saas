import Link from "next/link";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";

export default function LandingPage() {
  return (
    <Card>
      <CardHeader className="text-center">
        <CardTitle className="font-display text-2xl tracking-tight">
          Multitenant SaaS Starter
        </CardTitle>
        <CardDescription className="mt-1">
          Java 25 + Keycloak + Next.js — schema-per-tenant isolation, RBAC,
          and a production-ready foundation for your B2B platform.
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col items-center gap-4">
        <Button variant="accent" size="lg" asChild>
          <Link href="/request-access">Request Access</Link>
        </Button>
        <p className="text-center text-xs text-slate-500 dark:text-slate-400">
          Submit your details for admin review. You&apos;ll receive an email
          once your organisation has been provisioned.
        </p>
      </CardContent>
    </Card>
  );
}
