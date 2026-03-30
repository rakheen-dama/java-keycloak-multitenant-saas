import { MessageSquare } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { CommentForm } from "@/components/projects/comment-form";
import type { CommentResponse } from "@/app/(app)/projects/actions";

interface CommentSectionProps {
  projectId: string;
  comments: CommentResponse[];
  currentMemberId: string;
}

function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleDateString("en-ZA", {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export function CommentSection({
  projectId,
  comments,
  currentMemberId,
}: CommentSectionProps) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <MessageSquare className="size-5" />
          Comments ({comments.length})
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-6">
        {comments.length === 0 ? (
          <div className="flex flex-col items-center gap-2 py-8 text-muted-foreground">
            <MessageSquare className="size-8 opacity-40" />
            <p className="text-sm">No comments yet. Start the conversation.</p>
          </div>
        ) : (
          <div className="space-y-4">
            {comments.map((comment) => (
              <div
                key={comment.id}
                className="rounded-lg border border-slate-200 p-4 dark:border-slate-800"
              >
                <div className="mb-2 flex items-center gap-2">
                  <span className="text-sm font-medium text-foreground">
                    {comment.authorName}
                  </span>
                  <Badge
                    variant={
                      comment.authorType === "MEMBER" ? "member" : "neutral"
                    }
                  >
                    {comment.authorType === "MEMBER" ? "Team" : "Client"}
                  </Badge>
                  <span className="text-xs text-muted-foreground">
                    {formatDate(comment.createdAt)}
                  </span>
                </div>
                <p className="text-sm text-foreground whitespace-pre-wrap">
                  {comment.content}
                </p>
              </div>
            ))}
          </div>
        )}

        <div className="border-t border-slate-200 pt-6 dark:border-slate-800">
          <CommentForm
            projectId={projectId}
            currentMemberId={currentMemberId}
          />
        </div>
      </CardContent>
    </Card>
  );
}
