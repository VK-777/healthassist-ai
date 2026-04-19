import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ChatRequest, ChatResponse, AuditTrail } from '../models/chat.models';

@Injectable({ providedIn: 'root' })
export class ChatService {

  private readonly BASE_URL = 'http://localhost:8080';

  constructor(private http: HttpClient) {}

  sendMessage(request: ChatRequest): Observable<ChatResponse> {
    return this.http.post<ChatResponse>(`${this.BASE_URL}/ai/chat/sync`, request, {
      headers: new HttpHeaders({ 'Content-Type': 'application/json' })
    });
  }

  getAuditTrail(workflowId: string): Observable<AuditTrail> {
    return this.http.get<AuditTrail>(`${this.BASE_URL}/audit/${workflowId}`);
  }

  streamMessage(request: ChatRequest, onToken: (token: string) => void, onDone: () => void, onError: (err: any) => void): AbortController {
    const controller = new AbortController();

    fetch(`${this.BASE_URL}/ai/chat/async`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
      signal: controller.signal
    }).then(response => {
      const reader = response.body?.getReader();
      const decoder = new TextDecoder();
      if (!reader) { onError('No response body'); return; }

      let buffer = '';

      const read = (): void => {
        reader.read().then(({ done, value }) => {
          if (done) { onDone(); return; }
          buffer += decoder.decode(value, { stream: true });

          // Process all complete lines (ending with \n)
          let newlineIdx: number;
          while ((newlineIdx = buffer.indexOf('\n')) !== -1) {
            const line = buffer.substring(0, newlineIdx);
            buffer = buffer.substring(newlineIdx + 1);

            // Skip empty lines (SSE event separators)
            if (line.trim() === '') continue;

            if (line.startsWith('data:')) {
              const token = line.substring(5);
              if (token.trim() === '[DONE]') { onDone(); return; }
              onToken(token);
            }
          }
          read();
        }).catch(onError);
      };
      read();
    }).catch(onError);

    return controller;
  }
}
