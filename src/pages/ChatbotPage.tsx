import React from "react";
import Chatbot from "../components/Chatbot"; // le composant que je t'ai donné précédemment

const ChatbotPage: React.FC = () => {
  return (
    <div className="min-h-screen bg-gray-50 flex justify-center items-center p-6">
      <Chatbot />
    </div>
  );
};

export default ChatbotPage;
