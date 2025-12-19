// src/components/Chatbot.tsx
import React, { useState } from "react";
import { Send, MessageCircle, Loader2 } from "lucide-react";
import { askChatbot } from "../utils/chatbotApi";

interface Message {
  id: number;
  sender: "user" | "bot";
  text: string;
}

const Chatbot: React.FC = () => {
  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSend = async () => {
    if (!input.trim() || loading) return;

    const userMessage: Message = { id: Date.now(), sender: "user", text: input };
    setMessages(prev => [...prev, userMessage]);
    setInput("");
    setLoading(true);
    setError(null);

    try {
      // Call the real RAG chatbot API
      const botResponse = await askChatbot(userMessage.text);

      const botMessage: Message = {
        id: Date.now() + 1,
        sender: "bot",
        text: botResponse
      };
      setMessages(prev => [...prev, botMessage]);
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : "Une erreur est survenue";
      setError(errorMessage);

      // Add error message to chat
      const botMessage: Message = {
        id: Date.now() + 1,
        sender: "bot",
        text: `❌ ${errorMessage}`
      };
      setMessages(prev => [...prev, botMessage]);
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <button
        onClick={() => setOpen(!open)}
        className="fixed bottom-6 right-6 bg-blue-500 text-white p-4 rounded-full shadow-lg hover:bg-blue-600 transition z-50"
        aria-label="Ouvrir le chatbot"
      >
        <MessageCircle className="w-6 h-6" />
      </button>

      {open && (
        <div className="fixed bottom-20 right-6 w-96 bg-white border border-gray-300 rounded-lg shadow-xl flex flex-col z-50">
          <div className="flex justify-between items-center bg-blue-500 text-white px-4 py-3 rounded-t-lg">
            <span className="font-semibold">RecOuVTek Chatbot AI</span>
            <button
              onClick={() => setOpen(false)}
              className="hover:bg-blue-600 rounded px-2 py-1 transition"
              aria-label="Fermer"
            >
              ✕
            </button>
          </div>

          <div className="flex-1 p-4 overflow-y-auto space-y-3 h-96 bg-gray-50">
            {messages.length === 0 && (
              <div className="text-center text-gray-500 mt-8">
                <MessageCircle className="w-12 h-12 mx-auto mb-2 text-gray-400" />
                <p className="text-sm">Posez-moi des questions sur RecOuvTek !</p>
              </div>
            )}

            {messages.map(msg => (
              <div
                key={msg.id}
                className={`flex ${msg.sender === "user" ? "justify-end" : "justify-start"}`}
              >
                <div
                  className={`p-3 rounded-lg max-w-[80%] ${msg.sender === "user"
                      ? "bg-blue-500 text-white rounded-br-none"
                      : "bg-white border border-gray-200 text-gray-800 rounded-bl-none shadow-sm"
                    }`}
                >
                  <p className="text-sm whitespace-pre-wrap">{msg.text}</p>
                </div>
              </div>
            ))}

            {loading && (
              <div className="flex justify-start">
                <div className="bg-white border border-gray-200 p-3 rounded-lg rounded-bl-none shadow-sm">
                  <div className="flex items-center space-x-2 text-gray-500">
                    <Loader2 className="w-4 h-4 animate-spin" />
                    <span className="text-sm">Le bot réfléchit...</span>
                  </div>
                </div>
              </div>
            )}
          </div>

          <div className="flex border-t border-gray-200 bg-white rounded-b-lg">
            <input
              type="text"
              value={input}
              onChange={e => setInput(e.target.value)}
              onKeyDown={e => e.key === "Enter" && !e.shiftKey && handleSend()}
              placeholder="Posez votre question..."
              disabled={loading}
              className="flex-1 p-3 outline-none rounded-bl-lg disabled:bg-gray-50 disabled:text-gray-400"
            />
            <button
              onClick={handleSend}
              disabled={loading || !input.trim()}
              className="px-4 bg-blue-500 text-white hover:bg-blue-600 transition disabled:bg-gray-300 disabled:cursor-not-allowed rounded-br-lg"
              aria-label="Envoyer"
            >
              {loading ? (
                <Loader2 className="w-5 h-5 animate-spin" />
              ) : (
                <Send className="w-5 h-5" />
              )}
            </button>
          </div>
        </div>
      )}
    </>
  );
};

export default Chatbot;

