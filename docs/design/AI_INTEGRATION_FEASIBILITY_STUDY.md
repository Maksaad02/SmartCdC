# RecouvTek AI Integration: Feasibility Study & Technical Roadmap

**Project:** RecouvTek - Debt Collection Management System  
**Phase:** AI Integration - Intelligent Assistant Module  
**Date:** 2024  
**Author:** Senior Software Architect & AI Engineer

---

## Executive Summary

This document provides a comprehensive feasibility study and technical roadmap for integrating AI capabilities into RecouvTek, focusing on three core modules:
1. **Contextual RAG (PDFs)** - Document-based question answering
2. **Text-to-SQL** - Natural language database querying
3. **Agentic Extraction** - Automated PDF parsing and entity creation
4. **Debt Scoring** - Predictive analytics for payment probability

**Recommendation:** Hybrid Microservices Architecture with Python AI Service communicating via REST API with Spring Boot backend.

---

## 1. Architecture Decision: Microservices vs Monolith

### 1.1 Current State Analysis

**Existing Stack:**
- Spring Boot 3.4.5 (Java 21)
- MySQL 8.0 Database
- JWT Authentication
- Docker Compose Setup
- React Frontend (TypeScript)

**Key Entities:**
- `Creance` (Debt) - Core entity with status, amounts, dates
- `Client` - Debtor information
- `Reglement` (Payment) - Payment records
- `Relance` (Reminder) - Collection reminders

### 1.2 Architecture Recommendation: **Hybrid Microservices**

**Decision: Hybrid Microservices with AI Service as Separate Service**

**Rationale:**

✅ **Advantages:**
- **Technology Isolation**: Python ecosystem (LangChain, PyTorch, transformers) separate from Java
- **Independent Scaling**: AI service can scale independently based on PDF processing load
- **Resource Management**: GPU/CPU-intensive AI tasks don't impact Spring Boot performance
- **Development Velocity**: Python team can work independently on AI features
- **Deployment Flexibility**: Update AI models without redeploying entire backend
- **Cost Optimization**: AI service can use spot instances or different hardware

❌ **Monolith Disadvantages:**
- JVM memory overhead for ML models
- Limited Python ML library integration
- Tight coupling of AI features with business logic
- Difficult to scale AI components independently

### 1.3 Proposed Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    React Frontend (Port 3000)                │
└────────────────────────┬────────────────────────────────────┘
                         │
                         │ HTTP/REST
                         │
         ┌───────────────┴───────────────┐
         │                               │
         ▼                               ▼
┌──────────────────┐          ┌──────────────────┐
│  Spring Boot     │          │   AI Service     │
│  Backend         │◄────────►│   (Python)      │
│  (Port 8080)     │  REST    │   (Port 8000)    │
│                  │  API     │                  │
│  - Business      │          │  - RAG Engine    │
│    Logic         │          │  - Text-to-SQL   │
│  - Auth          │          │  - PDF Parser    │
│  - CRUD APIs     │          │  - ML Models     │
└────────┬─────────┘          └──────────────────┘
         │
         │ JDBC
         │
         ▼
┌──────────────────┐
│   MySQL 8.0      │
│   (Port 3306)    │
│                  │
│  - Business Data │
│  - Vector Store? │
└──────────────────┘
```

**Communication Pattern:**
- **Synchronous REST**: For real-time queries (Text-to-SQL, RAG)
- **Asynchronous (Optional)**: For batch PDF processing (via message queue if needed)

---

## 2. Tech Stack Recommendations

### 2.1 RAG (Retrieval-Augmented Generation) Stack

#### **Core Framework: LangChain (Recommended)**

**Why LangChain over LlamaIndex:**
- ✅ Better integration with multiple LLM providers (OpenAI, Ollama, HuggingFace)
- ✅ More flexible document processing pipelines
- ✅ Strong agent framework for complex workflows
- ✅ Active community and extensive documentation
- ✅ Better for production deployments

**Tech Stack:**
```python
# Core Dependencies
langchain==0.1.0+          # Main framework
langchain-community        # Community integrations
langchain-openai          # OpenAI integration (or langchain-ollama for local)
chromadb==0.4.0+          # Vector database (lightweight, embedded)
# OR
pgvector==0.2.0+          # PostgreSQL vector extension (if using Postgres)
langchain-postgres        # LangChain Postgres integration

# Document Processing
pypdf==3.17.0+            # PDF parsing
python-docx               # Word documents
unstructured              # Advanced document parsing
pdfplumber                # Alternative PDF parser

# OCR (if needed for scanned PDFs)
pytesseract               # Tesseract OCR wrapper
pillow                    # Image processing
```

**Vector Store Options:**

| Option | Pros | Cons | Recommendation |
|-------|------|------|----------------|
| **ChromaDB** | Lightweight, embedded, easy setup | Not production-grade for large scale | ✅ **Best for PFE** |
| **pgvector** | Native Postgres integration, ACID compliance | Requires Postgres migration | ⚠️ Consider if already on Postgres |
| **Pinecone** | Managed, scalable | Cost, external dependency | ❌ Overkill for PFE |
| **Qdrant** | Fast, self-hosted | Additional infrastructure | ⚠️ Good alternative |

**Recommendation: ChromaDB for PFE scope** (can migrate to pgvector later)

#### **LLM Provider Options:**

1. **OpenAI GPT-4/GPT-3.5-turbo** (Recommended for PFE)
   - Best accuracy and reliability
   - Easy API integration
   - Cost: ~$0.01-0.03 per query

2. **Ollama (Local)** (Alternative)
   - Free, runs locally
   - Models: Llama 2, Mistral, etc.
   - Requires GPU or powerful CPU

3. **HuggingFace Inference API**
   - Free tier available
   - Various models

**Recommendation: Start with OpenAI GPT-3.5-turbo, document Ollama as alternative**

### 2.2 Text-to-SQL Stack

**Tech Stack:**
```python
# Core
langchain==0.1.0+
langchain-sql-agent       # SQL agent framework
sqlalchemy==2.0+          # Database abstraction
pymysql                   # MySQL connector

# Schema Understanding
langchain-community       # For SQLDatabaseChain
```

**Approach:**
1. **SQLDatabaseChain** (LangChain) - Simple, works well for structured queries
2. **Custom Agent** with ReAct pattern - More flexible, better error handling

**Database Schema Injection:**
- Auto-generate schema description from JPA entities
- Include table names, columns, relationships
- Add business context (e.g., "Creance = Debt, StatutCreance = Debt Status")

### 2.3 Agentic Extraction Stack

**Tech Stack:**
```python
# PDF Processing
pypdf==3.17.0+
pdfplumber                # Better for tables
unstructured              # Advanced extraction

# LLM for Extraction
langchain==0.1.0+
langchain-openai          # For structured extraction

# Structured Output
pydantic==2.0+            # Data validation
langchain-pydantic        # Pydantic integration

# HTTP Client
httpx==0.25.0+            # Async HTTP client
requests                  # Sync alternative
```

**Extraction Strategy:**
1. **LLM-based Extraction** (Recommended)
   - Use GPT-4 with structured output (Pydantic models)
   - Prompt engineering for debt document formats
   - Fallback to regex for simple cases

2. **Hybrid Approach**
   - OCR for scanned documents
   - LLM for understanding and extraction
   - Validation against business rules

### 2.4 OCR Stack (For Scanned PDFs)

```python
pytesseract==0.3.10       # Tesseract wrapper
pillow==10.0+             # Image processing
pdf2image                 # PDF to image conversion
```

---

## 3. Data Mining: Debt Scoring Algorithm Recommendation

### 3.1 Association Rules vs. Classification/Clustering

**Your Question:** Should I use Apriori/FP-Growth for debt recovery patterns?

**Answer: NO - Use Classification/Regression for Debt Scoring**

### 3.2 Why NOT Association Rules?

**Association Rules (Apriori, FP-Growth) are for:**
- Market basket analysis (e.g., "Customers who buy X also buy Y")
- Finding frequent patterns in transactions
- Cross-selling recommendations

**They are NOT suitable for:**
- ❌ Predicting payment probability (supervised learning problem)
- ❌ Scoring individual debts (requires labeled outcomes)
- ❌ Time-series prediction (payment dates)

### 3.3 Recommended Approach: **Supervised Learning for Debt Scoring**

#### **Algorithm Recommendation: Gradient Boosting (XGBoost/LightGBM)**

**Why Gradient Boosting:**
- ✅ Handles mixed data types (numeric, categorical, dates)
- ✅ Feature importance interpretation
- ✅ Handles missing values well
- ✅ Production-ready, fast inference
- ✅ Works well with small-medium datasets (common in PFE)

**Alternative Algorithms:**
1. **Random Forest** - Simpler, good baseline
2. **Logistic Regression** - Interpretable, good for probability
3. **Neural Networks** - Overkill for PFE, requires more data

#### **Target Variable: Payment Probability Score (0-1)**

**Possible Targets:**
- Binary: `will_pay` (1) vs `won't_pay` (0) within X days
- Regression: Days until payment
- Multi-class: Payment probability buckets (High/Medium/Low)

#### **Feature Engineering from Your Entities:**

```python
# From Creance Entity
features = {
    # Temporal Features
    'days_overdue': days_between(echeance, today),
    'days_since_emission': days_between(dateEmission, today),
    'age_of_debt': days_between(dateEmission, today),
    
    # Financial Features
    'debt_amount': montantFacture,
    'amount_paid_ratio': montantEncaisse / montantFacture,
    'penalties_amount': montantPenalites,
    'total_due': montantFacture + montantPenalites,
    
    # Status Features
    'current_status': statut,  # EN_RETARD, PENALISEE, etc.
    'has_partial_payment': montantEncaisse > 0,
    
    # Client History Features (aggregated)
    'client_total_debts': count(client.creances),
    'client_paid_debts': count(client.creances where statut=PAYEE),
    'client_payment_rate': client_paid_debts / client_total_debts,
    'client_avg_days_to_pay': average(days_to_payment for paid debts),
    
    # Agent Features
    'agent_success_rate': agent.paid_debts / agent.total_debts,
    
    # Reminder Features
    'num_reminders_sent': count(relances),
    'days_since_last_reminder': days_between(last_relance.date, today),
    
    # Payment History Features
    'num_payments': count(reglements),
    'avg_payment_amount': average(reglements.montant),
    'last_payment_date': max(reglements.dateReglement),
}
```

#### **Implementation Stack:**

```python
# ML Framework
scikit-learn==1.3.0+      # Baseline models
xgboost==2.0+              # Gradient boosting
lightgbm==4.0+             # Alternative gradient boosting
pandas==2.0+               # Data manipulation
numpy==1.24+               # Numerical operations

# Model Persistence
joblib==1.3+               # Model serialization
pickle                    # Alternative

# Evaluation
scikit-learn               # Metrics (accuracy, precision, recall, ROC-AUC)
matplotlib                 # Visualization
seaborn                    # Statistical plots
```

#### **Model Training Pipeline:**

```python
# 1. Data Collection
# Query historical Creance data with outcomes
# Label: paid within 30/60/90 days? (binary classification)

# 2. Feature Engineering
# Extract features from Creance, Client, Reglement, Relance

# 3. Train/Test Split
# 80% train, 20% test (or time-based split)

# 4. Model Training
# XGBoost with hyperparameter tuning

# 5. Evaluation
# ROC-AUC, Precision-Recall, Feature Importance

# 6. Deployment
# Save model, create inference API endpoint
```

#### **Integration with Spring Boot:**

```java
// Add score field to Creance entity
@Column(name = "payment_probability_score")
private Double paymentProbabilityScore;

// Service method
public void updateDebtScores() {
    // Call AI service /api/ai/score-debts
    // Update scores in batch
}
```

---

## 4. Implementation Roadmap (Phased Approach)

### Phase 1: Foundation & Infrastructure (Week 1-2)

**Objectives:**
- Set up Python AI service
- Establish communication with Spring Boot
- Basic health checks

**Tasks:**
1. Create Python FastAPI service structure
2. Dockerize AI service
3. Add AI service to docker-compose.yml
4. Create REST API endpoints skeleton
5. Implement authentication (JWT token validation)
6. Database connection for Text-to-SQL

**Deliverables:**
- ✅ AI service running on port 8000
- ✅ Health check endpoint
- ✅ JWT authentication working
- ✅ Database connection established

**File Structure:**
```
RecOuVTeK_AI/
├── app/
│   ├── __init__.py
│   ├── main.py              # FastAPI app
│   ├── config.py            # Configuration
│   ├── models/              # Pydantic models
│   ├── services/
│   │   ├── rag_service.py
│   │   ├── sql_service.py
│   │   ├── extraction_service.py
│   │   └── scoring_service.py
│   └── utils/
│       ├── auth.py
│       └── db_connection.py
├── requirements.txt
├── Dockerfile
└── README.md
```

### Phase 2: Text-to-SQL Implementation (Week 3-4)

**Objectives:**
- Natural language to SQL conversion
- Query execution and result formatting

**Tasks:**
1. Generate database schema description from Spring Boot entities
2. Implement SQLDatabaseChain with LangChain
3. Create prompt templates for SQL generation
4. Add query validation and error handling
5. Test with sample queries
6. Create Spring Boot endpoint to proxy requests

**Deliverables:**
- ✅ `/api/ai/query` endpoint
- ✅ Handles queries like "How many debts are in progress?"
- ✅ Returns formatted JSON responses
- ✅ Error handling for invalid queries

**Example Queries to Support:**
- "How many debts are in progress?"
- "Show me debts over 1000 DHS"
- "What's the total amount of unpaid debts?"
- "List clients with more than 3 unpaid debts"

### Phase 3: RAG Implementation (Week 5-6)

**Objectives:**
- PDF upload and processing
- Vector store setup
- Question answering from documents

**Tasks:**
1. Implement PDF upload endpoint
2. Set up ChromaDB vector store
3. Create document chunking strategy
4. Implement embedding generation (OpenAI or local)
5. Build RAG chain with LangChain
6. Create question-answering endpoint
7. Add document management (list, delete)

**Deliverables:**
- ✅ `/api/ai/documents/upload` - Upload PDF
- ✅ `/api/ai/documents` - List documents
- ✅ `/api/ai/ask` - Ask questions about documents
- ✅ Vector store with document embeddings
- ✅ Context-aware answers

**Technical Details:**
- Chunk size: 1000 characters with 200 overlap
- Embedding model: `text-embedding-ada-002` (OpenAI) or `all-MiniLM-L6-v2` (local)
- Retrieval: Top 3-5 most relevant chunks
- LLM: GPT-3.5-turbo for generation

### Phase 4: Agentic Extraction (Week 7-8)

**Objectives:**
- Parse PDFs and extract structured data
- Automatically create Creance entities via Spring Boot API

**Tasks:**
1. Implement PDF parsing (pypdf, pdfplumber)
2. Create Pydantic models for extraction (DebtorName, Amount, Date, etc.)
3. Build LLM-based extraction pipeline
4. Implement validation logic
5. Create HTTP client to call Spring Boot `/api/creances` endpoint
6. Add error handling and retry logic
7. Support multiple PDF formats

**Deliverables:**
- ✅ `/api/ai/extract` - Extract and create debt from PDF
- ✅ Automatic POST to Spring Boot backend
- ✅ Extraction accuracy > 85%
- ✅ Support for common debt document formats

**Extraction Schema:**
```python
class DebtExtraction(BaseModel):
    debtor_name: str
    amount: float
    invoice_number: str
    emission_date: date
    due_date: date
    client_email: Optional[str]
    client_phone: Optional[str]
```

### Phase 5: Debt Scoring Model (Week 9-10)

**Objectives:**
- Train ML model for payment probability
- Integrate scoring into system

**Tasks:**
1. Collect historical data (Creance with outcomes)
2. Feature engineering pipeline
3. Train XGBoost model
4. Evaluate model performance
5. Create inference endpoint
6. Add batch scoring job
7. Integrate with Creance entity (add score field)
8. Create dashboard visualization

**Deliverables:**
- ✅ Trained model (ROC-AUC > 0.75 target)
- ✅ `/api/ai/score-debt/{id}` - Score single debt
- ✅ `/api/ai/score-all` - Batch scoring
- ✅ Score field in Creance entity
- ✅ Feature importance analysis

### Phase 6: Integration & Testing (Week 11-12)

**Objectives:**
- End-to-end testing
- Performance optimization
- Documentation

**Tasks:**
1. Integration testing
2. Load testing
3. Error handling improvements
4. API documentation (OpenAPI/Swagger)
5. User guide
6. Deployment guide
7. Presentation preparation

**Deliverables:**
- ✅ Complete system integration
- ✅ API documentation
- ✅ User manual
- ✅ Deployment scripts
- ✅ Demo ready

---

## 5. Technical Implementation Details

### 5.1 AI Service Structure (FastAPI)

```python
# app/main.py
from fastapi import FastAPI, Depends, HTTPException, UploadFile
from fastapi.security import HTTPBearer
from app.services.rag_service import RAGService
from app.services.sql_service import SQLService
from app.services.extraction_service import ExtractionService
from app.services.scoring_service import ScoringService

app = FastAPI(title="RecouvTek AI Service", version="1.0.0")
security = HTTPBearer()

@app.post("/api/ai/query")
async def text_to_sql(query: str, token: str = Depends(security)):
    # Validate JWT token
    # Convert natural language to SQL
    # Execute query
    # Return results
    pass

@app.post("/api/ai/documents/upload")
async def upload_document(file: UploadFile, token: str = Depends(security)):
    # Validate token
    # Process PDF
    # Store in vector database
    # Return document ID
    pass

@app.post("/api/ai/ask")
async def ask_question(question: str, document_id: str = None, token: str = Depends(security)):
    # RAG query
    # Return answer
    pass

@app.post("/api/ai/extract")
async def extract_debt(file: UploadFile, token: str = Depends(security)):
    # Extract structured data
    # POST to Spring Boot /api/creances
    # Return created entity
    pass

@app.post("/api/ai/score-debt/{debt_id}")
async def score_debt(debt_id: int, token: str = Depends(security)):
    # Fetch debt data
    # Calculate features
    # Run model inference
    # Return score
    pass
```

### 5.2 Spring Boot Integration

**New Controller:**
```java
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AIController {
    
    private final RestTemplate restTemplate;
    
    @PostMapping("/query")
    public ResponseEntity<?> queryDatabase(@RequestBody String query) {
        // Forward to AI service
        // Return response
    }
    
    @PostMapping("/documents/upload")
    public ResponseEntity<?> uploadDocument(@RequestParam("file") MultipartFile file) {
        // Forward to AI service
    }
    
    // Proxy endpoints to AI service
}
```

**Configuration:**
```java
@Configuration
public class AIServiceConfig {
    
    @Value("${ai.service.url:http://ai-service:8000}")
    private String aiServiceUrl;
    
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

### 5.3 Docker Compose Update

```yaml
services:
  ai-service:
    build:
      context: ./RecOuVTeK_AI
    ports:
      - "8000:8000"
    environment:
      - DATABASE_URL=jdbc:mysql://database:3306/recouvdb
      - OPENAI_API_KEY=${OPENAI_API_KEY}
      - SPRING_BOOT_URL=http://backend:8080
    depends_on:
      - database
      - backend
```

---

## 6. Cost Estimation

### 6.1 Development Costs (PFE Scope)

**Free/Open Source:**
- LangChain, ChromaDB, scikit-learn, XGBoost
- Ollama (local LLM) - Free
- Docker, MySQL

**Paid Services (Optional):**
- OpenAI API: ~$20-50/month for development/testing
- Cloud hosting (if needed): $10-30/month

**Total Estimated Cost: $30-80/month** (mostly optional)

### 6.2 Production Costs (Future)

- OpenAI API: $100-500/month (depending on usage)
- Vector database hosting: $20-100/month
- Model inference hosting: $50-200/month

---

## 7. Risk Assessment & Mitigation

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| LLM API costs exceed budget | High | Medium | Use Ollama for local development, OpenAI only for demo |
| PDF extraction accuracy low | High | Medium | Implement validation rules, human review workflow |
| Text-to-SQL generates incorrect queries | High | Medium | Add query validation, sandbox execution |
| Model training data insufficient | Medium | High | Use synthetic data generation, feature engineering |
| Integration complexity | Medium | Medium | Phased approach, thorough testing |

---

## 8. Success Metrics

### 8.1 Technical Metrics

- **RAG Accuracy**: > 80% relevant answers
- **Text-to-SQL**: > 90% correct query generation
- **Extraction Accuracy**: > 85% field extraction accuracy
- **Scoring Model**: ROC-AUC > 0.75

### 8.2 Business Metrics

- Time saved on manual data entry
- Improved debt collection rate
- User adoption rate

---

## 9. Next Steps

1. **Immediate Actions:**
   - Review and approve this roadmap
   - Set up development environment
   - Create AI service repository structure

2. **Week 1 Tasks:**
   - Initialize Python FastAPI project
   - Set up Docker configuration
   - Implement basic authentication

3. **Ongoing:**
   - Weekly progress reviews
   - Iterative development
   - Continuous testing

---

## 10. References & Resources

### Documentation
- [LangChain Documentation](https://python.langchain.com/)
- [FastAPI Documentation](https://fastapi.tiangolo.com/)
- [XGBoost Documentation](https://xgboost.readthedocs.io/)
- [ChromaDB Documentation](https://www.trychroma.com/)

### Tutorials
- LangChain RAG Tutorial
- FastAPI + Spring Boot Integration
- XGBoost for Classification

### Research Papers
- "Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks" (Lewis et al., 2020)
- "XGBoost: A Scalable Tree Boosting System" (Chen & Guestrin, 2016)

---

## Appendix A: Sample Code Snippets

### A.1 RAG Service Example

```python
from langchain.document_loaders import PyPDFLoader
from langchain.text_splitter import RecursiveCharacterTextSplitter
from langchain.vectorstores import Chroma
from langchain.embeddings import OpenAIEmbeddings
from langchain.chains import RetrievalQA
from langchain.llms import OpenAI

class RAGService:
    def __init__(self):
        self.embeddings = OpenAIEmbeddings()
        self.vectorstore = Chroma(embedding_function=self.embeddings)
        self.llm = OpenAI(temperature=0)
        
    def upload_document(self, pdf_path: str):
        loader = PyPDFLoader(pdf_path)
        documents = loader.load()
        
        text_splitter = RecursiveCharacterTextSplitter(
            chunk_size=1000,
            chunk_overlap=200
        )
        chunks = text_splitter.split_documents(documents)
        
        self.vectorstore.add_documents(chunks)
        
    def ask(self, question: str):
        qa_chain = RetrievalQA.from_chain_type(
            llm=self.llm,
            chain_type="stuff",
            retriever=self.vectorstore.as_retriever()
        )
        return qa_chain.run(question)
```

### A.2 Text-to-SQL Example

```python
from langchain.utilities import SQLDatabase
from langchain.chains import SQLDatabaseChain
from langchain.llms import OpenAI

class SQLService:
    def __init__(self, db_url: str):
        self.db = SQLDatabase.from_uri(db_url)
        self.llm = OpenAI(temperature=0)
        self.chain = SQLDatabaseChain.from_llm(
            self.llm, 
            self.db, 
            verbose=True
        )
        
    def query(self, natural_language_query: str):
        result = self.chain.run(natural_language_query)
        return result
```

### A.3 Extraction Service Example

```python
from langchain.document_loaders import PyPDFLoader
from langchain.llms import OpenAI
from langchain.output_parsers import PydanticOutputParser
from pydantic import BaseModel
import httpx

class DebtExtraction(BaseModel):
    debtor_name: str
    amount: float
    invoice_number: str
    emission_date: str
    due_date: str

class ExtractionService:
    def __init__(self):
        self.llm = OpenAI(temperature=0)
        self.parser = PydanticOutputParser(pydantic_object=DebtExtraction)
        
    def extract_and_create(self, pdf_path: str, spring_boot_url: str):
        # Load PDF
        loader = PyPDFLoader(pdf_path)
        text = loader.load()[0].page_content
        
        # Extract with LLM
        prompt = f"""
        Extract debt information from this document:
        {text}
        
        {self.parser.get_format_instructions()}
        """
        
        result = self.llm(prompt)
        extracted = self.parser.parse(result)
        
        # POST to Spring Boot
        response = httpx.post(
            f"{spring_boot_url}/api/creances",
            json=extracted.dict()
        )
        
        return response.json()
```

---

**End of Document**

*This document serves as a living guide. Update as implementation progresses.*

