import { redirect } from "next/navigation";
import { cookies } from "next/headers";
import { AppSidebar } from "@/components/app-sidebar";
import { AppHeader } from "@/components/app-header";

export const dynamic = "force-dynamic";

const GATEWAY_URL = process.env.GATEWAY_URL ?? "http://localhost:8443";

interface SessionData {
  authenticated: boolean;
  email?: string;
  name?: string;
  orgId?: string;
  isPlatformAdmin?: boolean;
}

export default async function AppLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const cookieStore = await cookies();
  const sessionCookie = cookieStore.get("SESSION");

  let session: SessionData = { authenticated: false };
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
      session = await res.json();
    }
  } catch {
    // Gateway unreachable — treat as unauthenticated
  }

  if (!session.authenticated) {
    redirect("/oauth2/authorization/keycloak");
  }

  if (session.isPlatformAdmin && !session.orgId) {
    redirect("/platform-admin/access-requests");
  }

  // Preflight: trigger member sync before rendering any child page.
  // On first login, MemberFilter.syncOrCreate() needs one completed request
  // to commit the member row. Without this, parallel dashboard calls race.
  try {
    await fetch(`${GATEWAY_URL}/api/members/me`, {
      headers: {
        Accept: "application/json",
        ...(sessionCookie
          ? { Cookie: `SESSION=${sessionCookie.value}` }
          : {}),
      },
      cache: "no-store",
    });
  } catch {
    // Best-effort — dashboard will retry if this fails
  }

  const orgSlug = session.orgId ?? "unknown";
  const userName = session.name ?? "User";
  const userEmail = session.email ?? "";

  return (
    <div className="flex h-screen overflow-hidden">
      <AppSidebar
        orgSlug={orgSlug}
        userName={userName}
        userEmail={userEmail}
      />
      <div className="flex flex-1 flex-col overflow-hidden">
        <AppHeader
          orgName={orgSlug}
          userName={userName}
          userEmail={userEmail}
        />
        <main id="main-content" className="flex-1 overflow-y-auto bg-slate-50">
          {children}
        </main>
      </div>
    </div>
  );
}
