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

  return <>{children}</>;
}
