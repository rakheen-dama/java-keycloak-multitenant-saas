"use client";

import { useState } from "react";
import { MessageSquare, Send, Loader2 } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { portalApi } from "@/lib/portal-api";

interface PortalComment {
  id: string;
  projectId: string;
  content: string;
  authorType: "MEMBER" | "CUSTOMER";
  authorId: string;
  authorName: string;
  createdAt: string;
}

interface PortalCommentSectionProps {
  projectId: string;
  initialComments: PortalComment[];
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

export function PortalCommentSection({
  projectId,
  initialComments,
}: PortalCommentSectionProps) {
  const [comments, setComments] = useState<PortalComment[]>(initialComments);
  const [newComment, setNewComment] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    const content = newComment.trim();
    if (!content || isSubmitting) return;
    setIsSubmitting(true);
    try {
      const comment = await portalApi.post<PortalComment>(
        `/api/portal/projects/${projectId}/comments`,
        { content },
      );
      setComments((prev) => [...prev, comment]);
      setNewComment("");
    } catch {
      // silently fail — user can retry
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div className="space-y-4">
      <h3 className="flex items-center gap-2 text-sm font-semibold text-slate-900 dark:text-slate-100">
        <MessageSquare className="size-4" />
        Comments ({comments.length})
      </h3>

      {comments.length === 0 ? (
        <div className="flex flex-col items-center gap-2 py-8 text-slate-500">
          <MessageSquare className="size-6 opacity-40" />
          <p className="text-sm">No comments yet. Start the conversation.</p>
        </div>
      ) : (
        <div className="divide-y divide-slate-100 dark:divide-slate-800">
          {comments.map((comment) => (
            <div key={comment.id} className="py-3">
              <div className="flex items-center justify-between gap-2">
                <div className="flex items-center gap-2">
                  <span className="text-sm font-medium text-slate-900 dark:text-slate-100">
                    {comment.authorName}
                  </span>
                  <Badge
                    variant={
                      comment.authorType === "MEMBER" ? "member" : "neutral"
                    }
                  >
                    {comment.authorType === "MEMBER" ? "Team" : "Client"}
                  </Badge>
                </div>
                <span className="text-xs text-slate-500">
                  {formatDate(comment.createdAt)}
                </span>
              </div>
              <p className="mt-1 whitespace-pre-wrap text-sm text-slate-600 dark:text-slate-400">
                {comment.content}
              </p>
            </div>
          ))}
        </div>
      )}

      <form
        onSubmit={handleSubmit}
        className="flex gap-2 border-t border-slate-200 pt-4 dark:border-slate-800"
      >
        <Textarea
          value={newComment}
          onChange={(e) => setNewComment(e.target.value)}
          placeholder="Write a comment..."
          className="min-h-[80px] resize-none"
          maxLength={2000}
          data-testid="portal-comment-input"
        />
        <Button
          type="submit"
          variant="default"
          size="icon"
          className="mt-auto shrink-0"
          disabled={!newComment.trim() || isSubmitting}
          data-testid="portal-comment-submit"
        >
          {isSubmitting ? (
            <Loader2 className="size-4 animate-spin" />
          ) : (
            <Send className="size-4" />
          )}
        </Button>
      </form>
    </div>
  );
}
