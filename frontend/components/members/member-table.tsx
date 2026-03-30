"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { MoreHorizontal, Users } from "lucide-react";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { ChangeRoleDialog } from "@/components/members/change-role-dialog";
import { RemoveMemberDialog } from "@/components/members/remove-member-dialog";
import type { MemberResponse } from "@/app/(app)/members/actions";

interface MemberTableProps {
  members: MemberResponse[];
  currentMemberId: string;
  isOwner: boolean;
}

const roleBadgeVariant: Record<string, "owner" | "member" | "neutral"> = {
  owner: "owner",
  member: "member",
};

const statusBadgeVariant: Record<string, "success" | "warning" | "neutral"> = {
  ACTIVE: "success",
  SUSPENDED: "warning",
};

function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleDateString("en-ZA", {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
}

export function MemberTable({
  members,
  currentMemberId,
  isOwner,
}: MemberTableProps) {
  const [changeRoleTarget, setChangeRoleTarget] =
    useState<MemberResponse | null>(null);
  const [removeTarget, setRemoveTarget] = useState<MemberResponse | null>(null);
  const router = useRouter();

  function handleSuccess() {
    router.refresh();
  }

  const sorted = [...members].sort(
    (a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime(),
  );

  if (sorted.length === 0) {
    return (
      <div className="flex flex-col items-center gap-2 py-16 text-muted-foreground">
        <Users className="size-8 opacity-40" />
        <p className="text-sm">No members found.</p>
      </div>
    );
  }

  return (
    <>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Name</TableHead>
            <TableHead>Email</TableHead>
            <TableHead>Role</TableHead>
            <TableHead>Status</TableHead>
            <TableHead>Joined</TableHead>
            {isOwner && (
              <TableHead className="w-12">
                <span className="sr-only">Actions</span>
              </TableHead>
            )}
          </TableRow>
        </TableHeader>
        <TableBody>
          {sorted.map((member) => {
            const isSelf = member.id === currentMemberId;
            const showActions = isOwner && !isSelf;

            return (
              <TableRow key={member.id}>
                <TableCell className="font-medium">
                  {member.displayName ?? "--"}
                  {isSelf && (
                    <span className="ml-2 text-xs text-muted-foreground">
                      (you)
                    </span>
                  )}
                </TableCell>
                <TableCell>{member.email}</TableCell>
                <TableCell>
                  <Badge variant={roleBadgeVariant[member.role] ?? "neutral"}>
                    {member.role}
                  </Badge>
                </TableCell>
                <TableCell>
                  <Badge
                    variant={statusBadgeVariant[member.status] ?? "neutral"}
                  >
                    {member.status}
                  </Badge>
                </TableCell>
                <TableCell className="text-muted-foreground">
                  {formatDate(member.createdAt)}
                </TableCell>
                {isOwner && (
                  <TableCell>
                    {showActions && (
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <Button
                            variant="ghost"
                            size="sm"
                            className="size-8 p-0"
                          >
                            <MoreHorizontal className="size-4" />
                            <span className="sr-only">Actions</span>
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                          <DropdownMenuItem
                            onSelect={() => setChangeRoleTarget(member)}
                          >
                            Change Role
                          </DropdownMenuItem>
                          <DropdownMenuItem
                            className="text-destructive"
                            onSelect={() => setRemoveTarget(member)}
                          >
                            Remove
                          </DropdownMenuItem>
                        </DropdownMenuContent>
                      </DropdownMenu>
                    )}
                  </TableCell>
                )}
              </TableRow>
            );
          })}
        </TableBody>
      </Table>

      {changeRoleTarget && (
        <ChangeRoleDialog
          member={changeRoleTarget}
          open={!!changeRoleTarget}
          onOpenChange={(open) => {
            if (!open) setChangeRoleTarget(null);
          }}
          onSuccess={handleSuccess}
        />
      )}

      {removeTarget && (
        <RemoveMemberDialog
          member={removeTarget}
          open={!!removeTarget}
          onOpenChange={(open) => {
            if (!open) setRemoveTarget(null);
          }}
          onSuccess={handleSuccess}
        />
      )}
    </>
  );
}
