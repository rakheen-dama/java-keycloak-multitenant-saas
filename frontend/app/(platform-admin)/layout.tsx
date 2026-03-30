import { redirect } from "next/navigation";
import { cookies } from "next/headers";

export const dynamic = "force-dynamic";

const GATEWAY_URL = process.env.GATEWAY_URL ?? "http://localhost:8443";

export default async function PlatformAdminLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const cookieStore = await cookies();
  const sessionCookie = cookieStore.get("SESSION");

  let isPlatformAdmin = false;
  try {
    const res = await fetch(`${GATEWAY_URL}/bff/me`, {
      headers: {
        Accept: "application/json",
        ...(sessionCookie
          ? { Cookie: `SESSION=${sessionCookie.value}` }
          : {}),
      },
      cache: "no-store",
    });
    if (res.ok) {
      const session = await res.json();
      isPlatformAdmin =
        session.authenticated === true && session.isPlatformAdmin === true;
    }
  } catch {
    // Gateway unreachable — treat as unauthenticated
  }

  if (!isPlatformAdmin) {
    redirect("/");
  }

  return (
    <div className="min-h-screen bg-slate-50">
      <header className="border-b bg-white px-6 py-3">
        <div className="mx-auto flex max-w-5xl items-center justify-between">
          <div>
            <h1 className="text-sm font-semibold text-slate-900">
              Platform Admin
            </h1>
            <p className="text-xs text-slate-500">
              Manage access requests and tenant provisioning
            </p>
          </div>
          <a
            href="/logout"
            className="rounded-md px-3 py-1.5 text-sm text-slate-600 transition-colors hover:bg-slate-100 hover:text-slate-900"
          >
            Sign out
          </a>
        </div>
      </header>
      <main className="mx-auto max-w-5xl px-6 py-8">{children}</main>
    </div>
  );
}
