/**
 * API utility for communicating with the RAG Chatbot backend
 * Backend: Spring Boot + Spring AI + OpenAI GPT-4o + pgvector
 */

const CHATBOT_API_URL = import.meta.env.VITE_CHATBOT_API_URL || "http://localhost:8082";

const MAX_QUESTION_LENGTH = 2000;

/**
 * Envoie une question au chatbot RAG.
 *
 * POST et non GET : la question voyageait en paramètre d'URL et atterrissait
 * donc dans les journaux d'accès, l'historique du navigateur et l'en-tête
 * Referer, alors qu'elle porte des données de recouvrement.
 *
 * Le jeton de l'agent est transmis : le service chatbot était ouvert sans
 * authentification et renvoyait les données de tous les clients.
 */
export const askChatbot = async (question: string): Promise<string> => {
    if (!question.trim()) {
        throw new Error("La question ne peut pas être vide");
    }
    if (question.length > MAX_QUESTION_LENGTH) {
        throw new Error(`Question trop longue (max ${MAX_QUESTION_LENGTH} caractères)`);
    }

    const token = localStorage.getItem("auth_token");
    if (!token) {
        throw new Error("Session expirée. Veuillez vous reconnecter.");
    }

    let response: Response;
    try {
        response = await fetch(`${CHATBOT_API_URL}/chat/ask`, {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                Authorization: `Bearer ${token}`,
            },
            body: JSON.stringify({ question }),
        });
    } catch {
        throw new Error("Impossible de contacter le service chatbot");
    }

    if (!response.ok) {
        switch (response.status) {
            case 401:
                throw new Error("Session expirée. Veuillez vous reconnecter.");
            case 403:
                throw new Error("Vous n'avez pas accès à cette information.");
            case 404:
                throw new Error("Service chatbot non disponible. Vérifiez que le backend est démarré.");
            case 429:
                throw new Error("Trop de requêtes. Veuillez patienter un instant.");
            case 500:
                throw new Error("Erreur du serveur chatbot. Veuillez réessayer.");
            default:
                throw new Error(`Erreur chatbot: ${response.status}`);
        }
    }

    return response.text();
};
