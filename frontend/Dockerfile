# ---- Build ----
FROM node:22-alpine AS builder

WORKDIR /app

# Dependances d'abord, pour profiter du cache Docker. `npm ci` respecte
# strictement package-lock.json : deux builds du meme commit donnent les memes
# versions (l'ancien `rm package-lock.json && npm install` les laissait deriver).
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .

# Variables lues au BUILD par Vite (figees dans le bundle, jamais de secret ici).
# Par defaut, chemins relatifs : nginx relaie /api et /chat vers les services.
ARG VITE_API_URL=/api
ARG VITE_CHATBOT_API_URL=
ENV VITE_API_URL=$VITE_API_URL \
    VITE_CHATBOT_API_URL=$VITE_CHATBOT_API_URL

RUN npm run build

# ---- Runtime : nginx sans privileges (utilisateur non-root, port 8080) ----
FROM nginxinc/nginx-unprivileged:1.27-alpine

# Cibles des proxys, modifiables au lancement (rendues dans la conf nginx).
ENV BACKEND_UPSTREAM=http://backend:8080 \
    RAG_UPSTREAM=http://rag:8082

COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/healthz || exit 1
