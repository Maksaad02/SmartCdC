# AI Service Quick Start Guide

This guide helps you quickly set up the Python AI service for RecouvTek.

## Prerequisites

- Python 3.10+
- Docker & Docker Compose
- OpenAI API key (or use Ollama for local)

## Step 1: Create AI Service Structure

```bash
mkdir RecOuVTeK_AI
cd RecOuVTeK_AI
```

## Step 2: Create requirements.txt

```txt
# FastAPI & Server
fastapi==0.104.1
uvicorn[standard]==0.24.0
python-multipart==0.0.6
pydantic==2.5.0
pydantic-settings==2.1.0

# LangChain & AI
langchain==0.1.0
langchain-community==0.0.10
langchain-openai==0.0.2
openai==1.3.0

# Vector Store
chromadb==0.4.15

# PDF Processing
pypdf==3.17.0
pdfplumber==0.10.3
unstructured==0.10.30

# Database
sqlalchemy==2.0.23
pymysql==1.1.0

# ML & Data Science
scikit-learn==1.3.2
xgboost==2.0.2
pandas==2.1.3
numpy==1.26.2
joblib==1.3.2

# HTTP Client
httpx==0.25.2
requests==2.31.0

# Authentication
python-jose[cryptography]==3.3.0
passlib[bcrypt]==1.7.4

# Utilities
python-dotenv==1.0.0
```

## Step 3: Create Basic FastAPI App

Create `app/main.py`:

```python
from fastapi import FastAPI, Depends, HTTPException, UploadFile, File
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
import os
from app.utils.auth import verify_token

app = FastAPI(
    title="RecouvTek AI Service",
    version="1.0.0",
    description="AI-powered features for debt collection management"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

security = HTTPBearer()

@app.get("/health")
async def health_check():
    return {"status": "healthy", "service": "ai-service"}

@app.post("/api/ai/query")
async def text_to_sql(
    query: str,
    credentials: HTTPAuthorizationCredentials = Depends(security)
):
    """Convert natural language to SQL and execute"""
    # Verify token
    if not verify_token(credentials.credentials):
        raise HTTPException(status_code=401, detail="Invalid token")
    
    # TODO: Implement SQL service
    return {"result": "Query executed", "data": []}

@app.post("/api/ai/documents/upload")
async def upload_document(
    file: UploadFile = File(...),
    credentials: HTTPAuthorizationCredentials = Depends(security)
):
    """Upload PDF document for RAG"""
    if not verify_token(credentials.credentials):
        raise HTTPException(status_code=401, detail="Invalid token")
    
    # TODO: Implement RAG service
    return {"document_id": "123", "status": "uploaded"}

@app.post("/api/ai/ask")
async def ask_question(
    question: str,
    document_id: str = None,
    credentials: HTTPAuthorizationCredentials = Depends(security)
):
    """Ask questions about uploaded documents"""
    if not verify_token(credentials.credentials):
        raise HTTPException(status_code=401, detail="Invalid token")
    
    # TODO: Implement RAG query
    return {"answer": "Sample answer"}

@app.post("/api/ai/extract")
async def extract_debt(
    file: UploadFile = File(...),
    credentials: HTTPAuthorizationCredentials = Depends(security)
):
    """Extract debt information from PDF and create entity"""
    if not verify_token(credentials.credentials):
        raise HTTPException(status_code=401, detail="Invalid token")
    
    # TODO: Implement extraction service
    return {"status": "extracted", "creance_id": 1}
```

## Step 4: Create Authentication Utility

Create `app/utils/auth.py`:

```python
import jwt
import os
from typing import Optional

SECRET_KEY = os.getenv("JWT_SECRET_KEY", "<votre-JWT_SECRET>")

def verify_token(token: str) -> bool:
    """Verify JWT token from Spring Boot"""
    try:
        decoded = jwt.decode(token, SECRET_KEY, algorithms=["HS256"])
        return True
    except jwt.ExpiredSignatureError:
        return False
    except jwt.InvalidTokenError:
        return False
```

## Step 5: Create Configuration

Create `app/config.py`:

```python
from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    # Database
    database_url: str = "mysql+pymysql://root:Maksaad@localhost:3306/rec_ouv_db"
    
    # OpenAI
    openai_api_key: str = ""
    
    # Spring Boot
    spring_boot_url: str = "http://localhost:8080"
    
    # JWT
    jwt_secret_key: str = "<votre-JWT_SECRET>"
    
    class Config:
        env_file = ".env"

settings = Settings()
```

## Step 6: Create Dockerfile

```dockerfile
FROM python:3.11-slim

WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y \
    gcc \
    g++ \
    && rm -rf /var/lib/apt/lists/*

# Copy requirements
COPY requirements.txt .

# Install Python dependencies
RUN pip install --no-cache-dir -r requirements.txt

# Copy application
COPY . .

# Expose port
EXPOSE 8000

# Run application
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
```

## Step 7: Update docker-compose.yml

Add to your existing `docker-compose.yml`:

```yaml
  ai-service:
    build:
      context: ./RecOuVTeK_AI
    ports:
      - "8000:8000"
    environment:
      - DATABASE_URL=jdbc:mysql://database:3306/recouvdb?user=root&password=${MYSQL_ROOT_PASSWORD}
      - OPENAI_API_KEY=${OPENAI_API_KEY}
      - SPRING_BOOT_URL=http://backend:8080
      - JWT_SECRET_KEY=${JWT_SECRET_KEY:-<votre-JWT_SECRET>}
    depends_on:
      - database
      - backend
    volumes:
      - ./RecOuVTeK_AI/app:/app/app
      - ai_models:/app/models
      - ai_documents:/app/documents

volumes:
  ai_models:
  ai_documents:
```

## Step 8: Create .env file

Create `RecOuVTeK_AI/.env`:

```env
OPENAI_API_KEY=your_openai_api_key_here
DATABASE_URL=mysql+pymysql://root:Maksaad@database:3306/recouvdb
SPRING_BOOT_URL=http://backend:8080
JWT_SECRET_KEY=<votre-JWT_SECRET>
```

## Step 9: Run the Service

```bash
# Build and start
docker-compose up --build ai-service

# Or run locally
cd RecOuVTeK_AI
pip install -r requirements.txt
uvicorn app.main:app --reload
```

## Step 10: Test the Service

```bash
# Health check
curl http://localhost:8000/health

# Test with JWT token (get from Spring Boot login)
curl -X POST http://localhost:8000/api/ai/query \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"query": "How many debts are in progress?"}'
```

## Next Steps

1. Implement RAG service (see Phase 3 in main document)
2. Implement SQL service (see Phase 2)
3. Implement extraction service (see Phase 4)
4. Implement scoring service (see Phase 5)

## Troubleshooting

**Issue: Cannot connect to database**
- Check database URL in .env
- Ensure database service is running
- Check network connectivity in Docker

**Issue: OpenAI API errors**
- Verify API key is set
- Check API quota/credits
- Consider using Ollama for local development

**Issue: JWT token validation fails**
- Ensure JWT_SECRET_KEY matches Spring Boot secret
- Check token expiration
- Verify token format

