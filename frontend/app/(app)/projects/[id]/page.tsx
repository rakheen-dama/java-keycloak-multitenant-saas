import { getProject, getComments, getCurrentMember, getOrgId } from "../actions";
import { ProjectDetail } from "@/components/projects/project-detail";
import { CommentSection } from "@/components/projects/comment-section";

export const dynamic = "force-dynamic";

interface ProjectDetailPageProps {
  params: Promise<{ id: string }>;
}

export default async function ProjectDetailPage({
  params,
}: ProjectDetailPageProps) {
  const { id } = await params;

  const [projectResult, commentsResult, memberResult, orgIdResult] =
    await Promise.all([
      getProject(id),
      getComments(id),
      getCurrentMember(),
      getOrgId(),
    ]);

  if (!projectResult.success || !projectResult.data) {
    return (
      <div className="mx-auto max-w-6xl px-4 py-10 sm:px-6 lg:px-8">
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700 dark:border-red-800 dark:bg-red-950 dark:text-red-300">
          {projectResult.error ?? "Failed to load project."}
        </div>
      </div>
    );
  }

  const project = projectResult.data;
  const comments = commentsResult.success ? (commentsResult.data ?? []) : [];
  const currentMemberId = memberResult.success
    ? (memberResult.data?.id ?? "")
    : "";
  const orgId = orgIdResult.success ? (orgIdResult.data ?? "") : "";

  return (
    <div className="mx-auto max-w-6xl px-4 py-10 sm:px-6 lg:px-8">
      <div className="space-y-8">
        <ProjectDetail project={project} orgId={orgId} />

        <CommentSection
          projectId={project.id}
          comments={comments}
          currentMemberId={currentMemberId}
        />
      </div>
    </div>
  );
}
