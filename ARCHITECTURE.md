# 🏗️ System Architecture – HealthAssist AI

This document describes the architecture and workflow of the HealthAssist AI chatbot.

---

## 🧠 Overview

The system follows a **Retrieval-Augmented Generation (RAG)** architecture to provide domain-specific, safe, and accurate responses.

---

## 🔄 High-Level Flow

User → API → Query Processing → Retrieval → LLM → Response

---

## ⚙️ Components

### 1. Client (Frontend)
- Sends user queries
- Displays chatbot responses

---

### 2. Backend (Spring Boot + Spring AI)
- Handles API requests
- Manages interaction with LLM
- Coordinates RAG pipeline

---

### 3. Query Processing Layer
- Rewrites user queries
- Removes noise (compression)
- Improves retrieval accuracy

---

### 4. Knowledge Base
- Located in `knowledge-base/`
- Contains healthcare documents, SOPs, FAQs
- Source of truth for responses

---

### 5. Embedding & Vector Store
- Converts documents into embeddings
- Stores vectors for similarity search
- Enables semantic retrieval

---

### 6. Retriever
- Fetches relevant documents
- Uses similarity threshold (e.g., ≥ 0.7)
- Filters based on metadata (if applicable)

---

### 7. LLM (via Spring AI)
- Receives:
  - system prompt  
  - retrieved context  
  - user query  
- Generates final response

---

## 🔍 RAG Pipeline

1. User query received  
2. Query transformation applied  
3. Relevant documents retrieved  
4. Context injected into prompt  
5. LLM generates grounded response  

---

## ⚠️ Safety Layer

- Prevents unsafe outputs  
- Blocks diagnosis or prescriptions  
- Ensures domain-restricted responses  

---

## 📡 API Endpoints (example)

- `POST /chat` → synchronous response  
- `GET /chat/stream` → streaming response  
- `GET /audit/{id}` → trace request logs  

---

## 🧱 System Properties

- Modular architecture  
- Stateless API design  
- Scalable microservice-compatible design  

---

## 🚀 Future Enhancements

- Add Redis caching  
- Add multi-tenant support  
- Add observability (logs + tracing)  
- Integrate external healthcare APIs  

---

## 📌 Summary

The system ensures:
- Accurate responses via RAG  
- Safety via prompt constraints  
- Scalability via modular backend  

---
