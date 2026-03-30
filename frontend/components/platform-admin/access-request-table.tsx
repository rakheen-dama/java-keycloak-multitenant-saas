"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { ClipboardList } from "lucide-react";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
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
import { ApproveDialog } from "@/components/platform-admin/approve-dialog";
import { RejectDialog } from "@/components/platform-admin/reject-dialog";
import type { AccessRequest } from "@/app/(platform-admin)/platform-admin/access-requests/actions";

const tabs = [
  { id: "ALL", label: "All" },
  { id: "PENDING", label: "Pending" },
  { id: "APPROVED", label: "Approved" },
  { id: "REJECTED", label: "Rejected" },
] as const;

type TabId = (typeof tabs)[number]["id"];

const statusBadgeVariant: Record<
  string,
  "warning" | "success" | "destructive" | "neutral"
> = {
  PENDING: "warning",
  APPROVED: "success",
  REJECTED: "destructive",
  PENDING_VERIFICATION: "neutral",
};

interface AccessRequestTableProps {
  requests: AccessRequest[];
}

export function AccessRequestTable({ requests }: AccessRequestTableProps) {
  const [activeTab, setActiveTab] = useState<TabId>("PENDING");
  const [approveTarget, setApproveTarget] = useState<AccessRequest | null>(
    null,
  );
  const [rejectTarget, setRejectTarget] = useState<AccessRequest | null>(null);
  const router = useRouter();

  const filtered =
    activeTab === "ALL"
      ? requests
      : requests.filter((r) => r.status === activeTab);

  const sorted = [...filtered].sort(
    (a, b) =>
      new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime(),
  );

  function handleSuccess() {
    router.refresh();
  }

  return (
    <div data-testid="access-requests-table">
      <Tabs
        value={activeTab}
        onValueChange={(v) => setActiveTab(v as TabId)}
      >
        <TabsList>
          {tabs.map((tab) => (
            <TabsTrigger
              key={tab.id}
              value={tab.id}
              data-testid={tab.id === "PENDING" ? "pending-tab" : undefined}
            >
              {tab.label}
            </TabsTrigger>
          ))}
        </TabsList>
        <TabsContent value={activeTab}>
          <div className="mt-6">
            {sorted.length === 0 ? (
              <div className="flex flex-col items-center gap-2 py-16 text-muted-foreground">
                <ClipboardList className="size-8 opacity-40" />
                <p className="text-sm">No access requests.</p>
              </div>
            ) : (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Org Name</TableHead>
                    <TableHead>Email</TableHead>
                    <TableHead>Name</TableHead>
                    <TableHead>Country</TableHead>
                    <TableHead>Industry</TableHead>
                    <TableHead>Submitted</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead className="text-right">Actions</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {sorted.map((request) => (
                    <TableRow
                      key={request.id}
                      data-testid={`request-row-${request.organizationName}`}
                    >
                      <TableCell className="font-medium">
                        {request.organizationName}
                      </TableCell>
                      <TableCell>{request.email}</TableCell>
                      <TableCell>{request.fullName}</TableCell>
                      <TableCell>{request.country}</TableCell>
                      <TableCell>{request.industry}</TableCell>
                      <TableCell>
                        {new Date(request.createdAt).toLocaleDateString(
                          "en-ZA",
                          {
                            year: "numeric",
                            month: "short",
                            day: "numeric",
                          },
                        )}
                      </TableCell>
                      <TableCell>
                        <Badge
                          variant={
                            statusBadgeVariant[request.status] ?? "neutral"
                          }
                        >
                          {request.status}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-right">
                        {request.status === "PENDING" && (
                          <div className="flex justify-end gap-2">
                            <Button
                              variant="accent"
                              size="sm"
                              data-testid="approve-btn"
                              onClick={() => setApproveTarget(request)}
                            >
                              Approve
                            </Button>
                            <Button
                              variant="destructive"
                              size="sm"
                              onClick={() => setRejectTarget(request)}
                            >
                              Reject
                            </Button>
                          </div>
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </div>
        </TabsContent>
      </Tabs>

      {approveTarget && (
        <ApproveDialog
          requestId={approveTarget.id}
          orgName={approveTarget.organizationName}
          email={approveTarget.email}
          open={!!approveTarget}
          onOpenChange={(open) => {
            if (!open) setApproveTarget(null);
          }}
          onSuccess={handleSuccess}
        />
      )}

      {rejectTarget && (
        <RejectDialog
          requestId={rejectTarget.id}
          orgName={rejectTarget.organizationName}
          email={rejectTarget.email}
          open={!!rejectTarget}
          onOpenChange={(open) => {
            if (!open) setRejectTarget(null);
          }}
          onSuccess={handleSuccess}
        />
      )}
    </div>
  );
}
