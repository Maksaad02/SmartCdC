# SmartCdC

Consolidated monorepo for the SmartCdC / RecOuVTeK debt-collection platform.

## Layout

- `backend/` — Spring Boot API (MySQL), formerly `RecOuVTeK_Back`
- `frontend/` — React + TypeScript client, formerly `RecOuVTeK_Front`
- `rag/` — RAG chatbot service (Spring AI + pgvector), formerly `RecouvChat_Rag`

Each folder's history is preserved from its original repository via `git subtree`
(the `rag/` history had two leaked OpenAI API keys scrubbed from past commits
before merging — see commit messages for details).
