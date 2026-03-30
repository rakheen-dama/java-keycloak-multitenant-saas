"use client";

import { useEffect, useState, useRef } from "react";
import { useParams, useRouter } from "next/navigation";
import { ArrowLeft, Loader2, AlertCircle, Send, MessageSquare } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { EmptyState } from "@/components/empty-state";
import { portalApi, PortalApiError, clearPortalAuth } from "@/lib/portal-api";
import Link from "next/link";
import type { PortalProject, PortalComment } from "@/lib/types";

export default function PortalProjectDetailPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const [project, setProject] = useState<PortalProject | null>(null);
  const [comments, setComments] = useState<PortalComment[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [newComment, setNewComment] = useState("");
  const [isSending, setIsSending] = useState(false);
  const commentEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    async function fetchData() {
      try {
        const [projectData, commentsData] = await Promise.all([
          portalApi.get<PortalProject>(`/portal/projects/${params.id}`),
          portalApi.get<PortalComment[]>(`/portal/projects/${params.id}/comments`),
        ]);
        setProject(projectData);
        setComments(commentsData);
      } catch (err) {
        if (err instanceof PortalApiError && err.status === 401) {
          clearPortalAuth();
          router.replace("/");
          return;
        }
        setError(err instanceof Error ? err.message : "Failed to load project");
      } finally {
        setIsLoading(false);
      }
    }
    fetchData();
  }, [params.id, router]);

  async function handleSubmitComment() {
    if (!newComment.trim()) return;
    setIsSending(true);
    try {
      const comment = await portalApi.post<PortalComment>(
        `/portal/projects/${params.id}/comments`,
        { content: newComment.trim() },
      );
      setComments((prev) => [...prev, comment]);
      setNewComment("");
      setTimeout(() => commentEndRef.current?.scrollIntoView({ behavior: "smooth" }), 100);
    } catch (err) {
      if (err instanceof PortalApiError && err.status === 401) {
        clearPortalAuth();
        router.replace("/");
        return;
      }
      setError(err instanceof Error ? err.message : "Failed to send comment");
    } finally {
      setIsSending(false);
    }
  }

  if (isLoading) {
    return (
      <div className="flex min-h-[40vh] items-center justify-center">
        <Loader2 className="size-8 animate-spin text-slate-400" />
      </div>
    );
  }

  if (error || !project) {
    return (
      <div className="space-y-4">
        <Link
          href="/projects"
          className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900 dark:text-slate-400"
        >
          <ArrowLeft className="size-4" />
          Back to projects
        </Link>
        <div className="flex items-center gap-2 rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700 dark:bg-red-950 dark:text-red-300" role="alert">
          <AlertCircle className="size-4 shrink-0" />
          {error ?? "Project not found"}
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <Link
        href="/projects"
        className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900 dark:text-slate-400"
      >
        <ArrowLeft className="size-4" />
        Back to projects
      </Link>

      {/* Project header */}
      <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-900">
        <div className="flex items-start justify-between">
          <div>
            <h1 className="font-display text-xl text-slate-950 dark:text-slate-50">
              {project.title}
            </h1>
            {project.description && (
              <p className="mt-2 text-sm text-slate-600 dark:text-slate-400">
                {project.description}
              </p>
            )}
          </div>
          <Badge variant="neutral">{project.status}</Badge>
        </div>
      </div>

      {/* Comments */}
      <div className="rounded-lg border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900">
        <div className="border-b border-slate-200 px-6 py-4 dark:border-slate-800">
          <h2 className="flex items-center gap-2 font-semibold text-slate-900 dark:text-slate-100">
            <MessageSquare className="size-5" />
            Comments
            <Badge variant="neutral">{comments.length}</Badge>
          </h2>
        </div>

        <div className="divide-y divide-slate-100 dark:divide-slate-800">
          {comments.length === 0 ? (
            <div className="px-6 py-8">
              <EmptyState
                icon={MessageSquare}
                title="No comments yet"
                description="Start the conversation by adding a comment below"
              />
            </div>
          ) : (
            comments.map((comment) => (
              <div key={comment.id} className="px-6 py-4">
                <div className="flex items-center gap-2">
                  <span className="text-sm font-medium text-slate-900 dark:text-slate-100">
                    {comment.authorName}
                  </span>
                  <Badge variant={comment.authorType === "CUSTOMER" ? "default" : "neutral"}>
                    {comment.authorType === "CUSTOMER" ? "Client" : "Team"}
                  </Badge>
                  <span className="text-xs text-slate-500">
                    {new Date(comment.createdAt).toLocaleDateString(undefined, {
                      month: "short",
                      day: "numeric",
                      hour: "2-digit",
                      minute: "2-digit",
                    })}
                  </span>
                </div>
                <p className="mt-1 whitespace-pre-wrap text-sm text-slate-700 dark:text-slate-300">
                  {comment.content}
                </p>
              </div>
            ))
          )}
          <div ref={commentEndRef} />
        </div>

        {/* Add comment */}
        <div className="border-t border-slate-200 px-6 py-4 dark:border-slate-800">
          <div className="flex gap-3">
            <Textarea
              value={newComment}
              onChange={(e) => setNewComment(e.target.value)}
              placeholder="Write a comment..."
              className="min-h-[80px] resize-none"
              onKeyDown={(e) => {
                if (e.key === "Enter" && (e.metaKey || e.ctrlKey)) {
                  handleSubmitComment();
                }
              }}
            />
            <Button
              variant="default"
              size="sm"
              onClick={handleSubmitComment}
              disabled={isSending || !newComment.trim()}
              className="self-end"
            >
              {isSending ? (
                <Loader2 className="size-4 animate-spin" />
              ) : (
                <Send className="size-4" />
              )}
            </Button>
          </div>
          <p className="mt-1 text-xs text-slate-500">Press Cmd+Enter to send</p>
        </div>
      </div>
    </div>
  );
}
