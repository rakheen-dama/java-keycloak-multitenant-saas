import { redirect } from "next/navigation";

const BACKEND_URL = process.env.BACKEND_URL || "http://localhost:8080";

interface ExchangeResponse {
  token: string;
  customerId: string;
  customerName: string;
}

export default async function ExchangePage({
  searchParams,
}: {
  searchParams: Promise<{ token?: string; org?: string }>;
}) {
  const { token, org } = await searchParams;

  if (!token || !org) {
    return <ErrorPage message="Invalid link - missing token or organization." />;
  }

  try {
    const res = await fetch(`${BACKEND_URL}/api/portal/auth/exchange`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ token, orgId: org }),
      cache: "no-store",
    });

    if (!res.ok) {
      const body = await res.json().catch(() => null);
      const message =
        body?.detail ?? body?.message ?? "Invalid or expired link.";
      return <ErrorPage message={message} />;
    }

    const data: ExchangeResponse = await res.json();

    // Redirect to a client page that stores the token and navigates
    const params = new URLSearchParams({
      t: data.token,
      n: data.customerName,
    });
    redirect(`/auth/callback?${params.toString()}`);
  } catch {
    return <ErrorPage message="Unable to reach the server. Please try again." />;
  }
}

function ErrorPage({ message }: { message: string }) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 dark:bg-slate-950">
      <div className="w-full max-w-md space-y-4 px-6 text-center">
        <div className="mx-auto flex size-12 items-center justify-center rounded-full bg-red-100 dark:bg-red-900">
          <svg
            className="size-6 text-red-600 dark:text-red-400"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
            strokeWidth={2}
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"
            />
          </svg>
        </div>
        <h1 className="text-lg font-semibold text-slate-900 dark:text-slate-100">
          Link expired or invalid
        </h1>
        <p className="text-sm text-slate-600 dark:text-slate-400">{message}</p>
        <a
          href="/"
          className="inline-block rounded-md border border-slate-300 px-4 py-2 text-sm text-slate-700 hover:bg-slate-100 dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-800"
        >
          Request a new link
        </a>
      </div>
    </div>
  );
}
