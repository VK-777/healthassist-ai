export interface ChatRequest {
  message: string;
  sessionId: string;
}

export interface ChatResponse {
  response: string;
  citations: string[];
  workflowId: string;
  disclaimer: string;
}

export interface AuditStage {
  stage: string;
  timestamp: string;
  details: string;
}

export interface AuditTrail {
  workflowId: string;
  stages: AuditStage[];
}

export interface Message {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  citations?: string[];
  workflowId?: string;
  disclaimer?: string;
  isHighRisk?: boolean;
  timestamp: Date;
  isLoading?: boolean;
}
