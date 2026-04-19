import { Component, OnInit, ViewChild, ElementRef, AfterViewChecked } from '@angular/core';
import { v4 as uuidv4 } from 'uuid';
import { ChatService } from '../../services/chat.service';
import { Message, AuditTrail, AuditStage } from '../../models/chat.models';

@Component({
  selector: 'app-chat',
  templateUrl: './chat.component.html',
  styleUrls: ['./chat.component.scss']
})
export class ChatComponent implements OnInit, AfterViewChecked {

  @ViewChild('messagesEnd') messagesEnd!: ElementRef;
  @ViewChild('inputField') inputField!: ElementRef;

  messages: Message[] = [];
  inputText = '';
  sessionId: string = '';
  isLoading = false;
  errorMessage = '';
  isStreamingMode = false;

  // Audit panel state
  auditTrail: AuditTrail | null = null;
  auditLoading = false;
  showAuditPanel = false;
  selectedWorkflowId = '';

  private HIGH_RISK_KEYWORDS = [
    'chest pain', 'chest tightness', 'tightness in my chest', 'tightness in chest',
    'chest pressure', 'sharp chest', 'shortness of breath', 'difficulty breathing',
    'can\'t breathe', 'cannot breathe', 'severe bleeding', 'uncontrolled bleeding',
    'stroke', 'sudden weakness', 'slurred speech', 'loss of consciousness',
    'unconscious', 'heart attack', 'palpitation', 'seizure', 'severe chest',
    'numbness'
  ];

  constructor(private chatService: ChatService) {}

  ngOnInit(): void {
    this.sessionId = 'session-' + uuidv4().substring(0, 8);
    this.addWelcomeMessage();
  }

  ngAfterViewChecked(): void {
    this.scrollToBottom();
  }

  private addWelcomeMessage(): void {
    this.messages.push({
      id: uuidv4(),
      role: 'assistant',
      content: 'Hello! I\'m **HealthAssist AI**, your hospital support assistant.\n\nI can help you with:\n- 🏥 Hospital navigation & visiting hours\n- 📋 Department information & appointments\n- 💊 Insurance & discharge queries\n- 🚨 Triage guidance & symptom routing\n\nHow can I assist you today?',
      timestamp: new Date()
    });
  }

  sendMessage(): void {
    if (this.isStreamingMode) {
      this.sendMessageAsync();
      return;
    }
    const text = this.inputText.trim();
    if (!text || this.isLoading) return;

    const isHighRisk = this.HIGH_RISK_KEYWORDS.some(k => text.toLowerCase().includes(k));

    const userMsg: Message = {
      id: uuidv4(),
      role: 'user',
      content: text,
      timestamp: new Date(),
      isHighRisk
    };
    this.messages.push(userMsg);
    this.inputText = '';
    this.errorMessage = '';

    const loadingMsg: Message = {
      id: uuidv4(),
      role: 'assistant',
      content: '',
      timestamp: new Date(),
      isLoading: true
    };
    this.messages.push(loadingMsg);
    this.isLoading = true;

    this.chatService.sendMessage({ message: text, sessionId: this.sessionId }).subscribe({
      next: (res) => {
        const idx = this.messages.findIndex(m => m.id === loadingMsg.id);
        if (idx !== -1) {
          this.messages[idx] = {
            id: loadingMsg.id,
            role: 'assistant',
            content: res.response,
            citations: res.citations,
            workflowId: res.workflowId,
            disclaimer: res.disclaimer,
            isHighRisk,
            timestamp: new Date(),
            isLoading: false
          };
        }
        this.isLoading = false;
      },
      error: (err) => {
        const idx = this.messages.findIndex(m => m.id === loadingMsg.id);
        if (idx !== -1) this.messages.splice(idx, 1);
        this.errorMessage = 'Failed to get a response. Please check your connection and try again.';
        this.isLoading = false;
      }
    });
  }

  sendMessageAsync(): void {
    const text = this.inputText.trim();
    if (!text || this.isLoading) return;

    const isHighRisk = this.HIGH_RISK_KEYWORDS.some(k => text.toLowerCase().includes(k));

    const userMsg: Message = {
      id: uuidv4(),
      role: 'user',
      content: text,
      timestamp: new Date(),
      isHighRisk
    };
    this.messages.push(userMsg);
    this.inputText = '';
    this.errorMessage = '';

    const assistantMsg: Message = {
      id: uuidv4(),
      role: 'assistant',
      content: '',
      timestamp: new Date(),
      isHighRisk,
      isLoading: true
    };
    this.messages.push(assistantMsg);
    this.isLoading = true;

    this.chatService.streamMessage(
      { message: text, sessionId: this.sessionId },
      (token) => {
        const idx = this.messages.findIndex(m => m.id === assistantMsg.id);
        if (idx !== -1) {
          this.messages[idx].isLoading = false;
          this.messages[idx].content += token;
        }
      },
      () => {
        this.isLoading = false;
      },
      (err) => {
        const idx = this.messages.findIndex(m => m.id === assistantMsg.id);
        if (idx !== -1) this.messages.splice(idx, 1);
        this.errorMessage = 'Streaming failed. Please try again.';
        this.isLoading = false;
      }
    );
  }

  toggleStreamingMode(): void {
    this.isStreamingMode = !this.isStreamingMode;
  }

  onKeyDown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.sendMessage();
    }
  }

  openAuditPanel(workflowId: string): void {
    this.selectedWorkflowId = workflowId;
    this.showAuditPanel = true;
    this.auditTrail = null;
    this.auditLoading = true;

    this.chatService.getAuditTrail(workflowId).subscribe({
      next: (trail) => {
        this.auditTrail = trail;
        this.auditLoading = false;
      },
      error: () => {
        this.auditLoading = false;
      }
    });
  }

  closeAuditPanel(): void {
    this.showAuditPanel = false;
  }

  parseStageDetails(details: string): any {
    try { return JSON.parse(details); } catch { return details; }
  }

  getStageIcon(stage: string): string {
    const icons: Record<string, string> = {
      'USER_QUERY': 'bi-person-fill',
      'FILTER': 'bi-funnel-fill',
      'LLM_CALL': 'bi-cpu-fill',
      'TRIAGE': 'bi-heart-pulse-fill'
    };
    return icons[stage] || 'bi-circle-fill';
  }

  getStageColor(stage: string): string {
    const colors: Record<string, string> = {
      'USER_QUERY': 'text-info',
      'FILTER': 'text-warning',
      'LLM_CALL': 'text-success',
      'TRIAGE': 'text-danger'
    };
    return colors[stage] || 'text-secondary';
  }

  formatTimestamp(ts: string): string {
    return new Date(ts).toLocaleTimeString();
  }

  clearChat(): void {
    this.messages = [];
    this.sessionId = 'session-' + uuidv4().substring(0, 8);
    this.addWelcomeMessage();
  }

  private scrollToBottom(): void {
    try {
      this.messagesEnd?.nativeElement?.scrollIntoView({ behavior: 'smooth' });
    } catch {}
  }

  formatContent(content: string): string {
    // Basic markdown-like formatting
    return content
      .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
      .replace(/\*(.*?)\*/g, '<em>$1</em>')
      .replace(/\n/g, '<br>');
  }
}
