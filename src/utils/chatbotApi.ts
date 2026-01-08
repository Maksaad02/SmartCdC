/**
 * API utility for communicating with the RAG Chatbot backend
 * Backend: Spring Boot + Spring AI + OpenAI GPT-4o + pgvector
 * Port: 8082
 */

const CHATBOT_API_URL = import.meta.env.VITE_CHATBOT_API_URL || "http://localhost:8082";

/**
 * Send a question to the RAG chatbot and get an intelligent response
 * @param question The user's question
 * @returns The chatbot's response as a string
 * @throws Error if the API call fails
 */
export const askChatbot = async (question: string): Promise<string> => {
    if (!question.trim()) {
        throw new Error("Question cannot be empty");
    }

    try {
        const response = await fetch(
            `${CHATBOT_API_URL}/chat/ask?question=${encodeURIComponent(question)}`,
            {
                method: 'GET',
                headers: {
                    'Content-Type': 'text/plain',
                },
            }
        );

        if (!response.ok) {
            if (response.status === 404) {
                throw new Error("Service chatbot non disponible. Vérifiez que le backend est démarré.");
            } else if (response.status === 500) {
                throw new Error("Erreur du serveur chatbot. Veuillez réessayer.");
            } else {
                throw new Error(`Erreur chatbot: ${response.status}`);
            }
        }

        const answer = await response.text();
        return answer;
    } catch (error) {
        if (error instanceof Error) {
            throw error;
        }
        throw new Error("Impossible de contacter le service chatbot");
    }
};
