// ---- Portal Documents ----

export type DocumentScope = "ORG" | "PROJECT" | "CUSTOMER";

export interface PresignDownloadResponse {
  presignedUrl: string;
  expiresInSeconds: number;
}

// ---- Portal (from PortalAuthController, PortalProjectController, PortalDocumentController) ----

export interface PortalProject {
  id: string;
  name: string;
  description: string | null;
  documentCount: number;
  createdAt: string;
  // Detail-only fields (present when fetching single project)
  status?: string;
  commentCount?: number;
}

export interface PortalDocument {
  id: string;
  fileName: string;
  contentType: string;
  size: number;
  scope: DocumentScope;
  projectId: string | null;
  projectName: string | null;
  uploadedAt: string | null;
  createdAt: string;
}

export interface PortalAuthResponse {
  token: string;
  customerName: string;
  expiresIn: number;
}

export interface MagicLinkResponse {
  message: string;
  magicLink?: string;
}

export interface PortalProfile {
  contactId: string;
  customerId: string;
  customerName: string;
  email: string;
  displayName: string;
  role: string;
}

// ---- Portal Comments (from PortalCommentController) ----

export interface PortalComment {
  id: string;
  authorName: string;
  content: string;
  createdAt: string;
}

// ---- Portal Summary (from PortalSummaryController) ----

export interface PortalSummary {
  projectId: string;
  totalHours: number;
  billableHours: number;
  lastActivityAt: string | null;
}

// ---- Portal Tasks (from PortalProjectController) ----

export interface PortalTask {
  id: string;
  name: string;
  status: string;
  assigneeName: string | null;
  sortOrder: number;
}

// ---- Pending Acceptances (from PortalAcceptanceController) ----

export interface PendingAcceptance {
  id: string;
  documentTitle: string | null;
  requestToken: string;
  sentAt: string | null;
  expiresAt: string | null;
  status: string;
}

// ---- Portal Proposals (from PortalProposalController / PortalProposalService) ----

export interface PortalProposalSummary {
  id: string;
  proposalNumber: string;
  title: string;
  status: string;
  feeModel: string;
  feeAmount: number;
  feeCurrency: string;
  sentAt: string | null;
}

export interface PortalProposalDetail extends PortalProposalSummary {
  contentHtml: string | null;
  milestonesJson: string | null;
  expiresAt: string | null;
  orgName: string | null;
  orgLogoUrl: string | null;
  orgBrandColor: string | null;
}

export interface PortalAcceptResponse {
  proposalId: string;
  status: string;
  acceptedAt: string | null;
  projectName: string | null;
  message: string;
}

export interface PortalDeclineResponse {
  proposalId: string;
  status: string;
  declinedAt: string | null;
}
