// src/components/Chatbot.tsx
import React, { useState } from "react";
import { Send, MessageCircle } from "lucide-react";

interface Message {
  id: number;
  sender: "user" | "bot";
  text: string;
}

const Chatbot: React.FC = () => {
  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState("");

  const handleSend = () => {
    if (!input.trim()) return;

    const userMessage: Message = { id: Date.now(), sender: "user", text: input };
    setMessages(prev => [...prev, userMessage]);
    setInput("");

    // Simuler réponse du bot
    setTimeout(() => {
      const botMessage: Message = {
        id: Date.now() + 1,
        sender: "bot",
        text: `Vous avez dit: "${userMessage.text}"`
      };
      setMessages(prev => [...prev, botMessage]);
    }, 1000);
  };

  return (
    <>
      <button
        onClick={() => setOpen(!open)}
        className="fixed bottom-6 right-6 bg-blue-500 text-white p-4 rounded-full shadow-lg hover:bg-blue-600 transition"
      >
        <MessageCircle className="w-6 h-6" />
      </button>

      {open && (
        <div className="fixed bottom-20 right-6 w-80 bg-white border border-gray-300 rounded-lg shadow-lg flex flex-col">
          <div className="flex justify-between items-center bg-blue-500 text-white px-4 py-2 rounded-t-lg">
            <span>RecOuVTek Chatbot</span>
            <button onClick={() => setOpen(false)}>X</button>
          </div>

          <div className="flex-1 p-3 overflow-y-auto space-y-2 h-64">
            {messages.map(msg => (
              <div
                key={msg.id}
                className={`p-2 rounded-md max-w-[75%] ${
                  msg.sender === "user" ? "bg-blue-100 self-end text-right" : "bg-gray-100 self-start"
                }`}
              >
                {msg.text}
              </div>
            ))}
          </div>

          <div className="flex border-t border-gray-200">
            <input
              type="text"
              value={input}
              onChange={e => setInput(e.target.value)}
              onKeyDown={e => e.key === "Enter" && handleSend()}
              placeholder="Écrire un message..."
              className="flex-1 p-2 outline-none"
            />
            <button
              onClick={handleSend}
              className="px-3 bg-blue-500 text-white hover:bg-blue-600 transition"
            >
              <Send className="w-5 h-5" />
            </button>
          </div>
        </div>
      )}
    </>
  );
};

export default Chatbot;
