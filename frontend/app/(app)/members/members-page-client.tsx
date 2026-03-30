"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { UserPlus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { MemberTable } from "@/components/members/member-table";
import { InviteDialog } from "@/components/members/invite-dialog";
import type { MemberResponse } from "@/app/(app)/members/actions";

interface MembersPageClientProps {
  members: MemberResponse[];
  currentMemberId: string;
  isOwner: boolean;
}

export function MembersPageClient({
  members,
  currentMemberId,
  isOwner,
}: MembersPageClientProps) {
  const [inviteOpen, setInviteOpen] = useState(false);
  const router = useRouter();

  function handleInviteSuccess() {
    router.refresh();
  }

  return (
    <div className="mx-auto max-w-6xl px-4 py-10 sm:px-6 lg:px-8">
      <div className="mb-8 flex items-center justify-between">
        <div>
          <h1 className="font-display text-2xl font-bold tracking-tight text-foreground">
            Members
          </h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Manage your organisation members.
          </p>
        </div>
        {isOwner && (
          <Button
            variant="accent"
            onClick={() => setInviteOpen(true)}
            data-testid="invite-member-btn"
          >
            <UserPlus className="mr-2 size-4" />
            Invite Member
          </Button>
        )}
      </div>

      <MemberTable
        members={members}
        currentMemberId={currentMemberId}
        isOwner={isOwner}
      />

      <InviteDialog
        open={inviteOpen}
        onOpenChange={setInviteOpen}
        onSuccess={handleInviteSuccess}
      />
    </div>
  );
}
