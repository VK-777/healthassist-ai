# HealthAssist AI – RAG-based Healthcare Chatbot

A domain-specific AI chatbot built using **Spring AI**, implementing a **Retrieval-Augmented Generation (RAG)** pipeline for safe and accurate healthcare assistance.

---

## 🚀 Key Highlights

- RAG-based architecture using healthcare knowledge base  
- Domain-constrained responses with safety guardrails  
- Real-time chat using Spring Boot and LLM integration  
- Query transformation (rewrite, compression, translation)  
- Metadata filtering and reranking for accurate retrieval  

---

## 🧠 Architecture

User → API → Spring AI → Retriever → LLM → Response

Pipeline:
1. Query transformation  
2. Retrieval from knowledge base  
3. Context injection  
4. LLM response generation  

---

## 🔍 RAG Pipeline

- Documents stored in `knowledge-base/`  
- Converted to embeddings  
- Stored in vector database  
- Retrieved using similarity search  
- Injected into prompt for grounded responses  

---

## ⚠️ Safety Constraints

- No medical diagnosis or prescriptions  
- Responses strictly based on knowledge base  
- Safe fallback when no context found  
- Escalation guidance for critical queries  

---

## 📂 Project Structure

```bash
healthassist-ai/
│
├── backend/
├── frontend/
├── knowledge-base/
├── docs/
├── ARCHITECTURE.md
├── LICENSE
├── PROMPT.md
├── README.md
└── .gitignore
```


---

## ▶️ How to Run

### Backend
```bash
mvn spring-boot:run
```

### Frontend
```bash
npm install
ng serve
```

---

## 📌 Features

- Domain-specific AI chatbot  
- RAG-based retrieval system  
- Secure and controlled responses  
- Modular backend architecture  

---

## 👨‍💻 Author

Vedant Kumar  
https://github.com/VK-777
