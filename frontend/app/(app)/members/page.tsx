import { listMembers, getCurrentMember } from "./actions";
import { MembersPageClient } from "./members-page-client";

export const dynamic = "force-dynamic";

export default async function MembersPage() {
  const [membersResult, currentMemberResult] = await Promise.all([
    listMembers(),
    getCurrentMember(),
  ]);

  if (!membersResult.success || !currentMemberResult.success) {
    return (
      <div className="mx-auto max-w-6xl px-4 py-10 sm:px-6 lg:px-8">
        <div className="mb-8">
          <h1 className="font-display text-2xl font-bold tracking-tight text-foreground">
            Members
          </h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Manage your organisation members.
          </p>
        </div>
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700 dark:border-red-800 dark:bg-red-950 dark:text-red-300">
          {membersResult.error ??
            currentMemberResult.error ??
            "Failed to load members."}
        </div>
      </div>
    );
  }

  const members = membersResult.data ?? [];
  const currentMember = currentMemberResult.data!;
  const isOwner = currentMember.role === "owner";

  return (
    <MembersPageClient
      members={members}
      currentMemberId={currentMember.id}
      isOwner={isOwner}
    />
  );
}
