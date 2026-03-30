"use client";

import { useState, useRef } from "react";
import { useRouter } from "next/navigation";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import { addComment } from "@/app/(app)/projects/actions";

interface CommentFormProps {
  projectId: string;
  currentMemberId: string;
}

export function CommentForm({ projectId }: CommentFormProps) {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const formRef = useRef<HTMLFormElement>(null);
  const router = useRouter();

  async function handleSubmit(formData: FormData) {
    setError(null);
    const content = formData.get("content")?.toString().trim() ?? "";
    if (!content) {
      setError("Comment cannot be empty.");
      return;
    }

    setIsSubmitting(true);
    try {
      const result = await addComment(projectId, content);
      if (result.success) {
        formRef.current?.reset();
        toast.success("Comment added.");
        router.refresh();
      } else {
        setError(result.error ?? "Failed to add comment.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <form ref={formRef} action={handleSubmit} className="space-y-3">
      <div className="space-y-2">
        <Label htmlFor="comment-content">Add a comment</Label>
        <Textarea
          id="comment-content"
          name="content"
          placeholder="Write a comment..."
          required
          disabled={isSubmitting}
          data-testid="comment-content-input"
        />
      </div>
      {error && (
        <p className="text-sm text-destructive" role="alert">
          {error}
        </p>
      )}
      <div className="flex justify-end">
        <Button
          type="submit"
          variant="accent"
          disabled={isSubmitting}
          data-testid="post-comment-btn"
        >
          {isSubmitting ? "Posting..." : "Post Comment"}
        </Button>
      </div>
    </form>
  );
}
