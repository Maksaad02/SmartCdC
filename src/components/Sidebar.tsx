
// import React from "react";
// import { Link, useLocation } from "react-router-dom";
// import { useAuth } from "../contexts/AuthContext";
// import { 
//   BarChart3, 
//   FileTextIcon, 
//   BanknoteIcon, 
//   Bell, 
//   Users, 
//   Settings, 
//   LogOut 
// } from "lucide-react";
// import { cn } from "@/lib/utils";

// const Sidebar: React.FC = () => {
//   const location = useLocation();
//   const { logout, currentUser } = useAuth();
  
//   const isActive = (path: string) => {
//     return location.pathname === path;
//   };

//   return (
//     <div className="w-64 h-screen bg-debt-blue text-white flex flex-col fixed">
//       <div className="p-5 border-b border-debt-lightBlue">
//         <h1 className="text-2xl font-bold">RecOuVTek</h1>
//       </div>
      
//       <div className="flex-1 overflow-y-auto py-4">
//         <nav className="space-y-1 px-2">
//           <Link 
//             to="/dashboard" 
//             className={cn(
//               "flex items-center px-4 py-3 text-sm rounded-md transition-colors",
//               isActive("/dashboard") 
//                 ? "bg-debt-lightBlue text-white" 
//                 : "text-gray-200 hover:bg-debt-lightBlue hover:text-white"
//             )}
//           >
//             <BarChart3 className="mr-3 h-5 w-5" />
//             Dashboard
//           </Link>
          
//           <Link 
//             to="/debts" 
//             className={cn(
//               "flex items-center px-4 py-3 text-sm rounded-md transition-colors",
//               isActive("/debts") 
//                 ? "bg-debt-lightBlue text-white" 
//                 : "text-gray-200 hover:bg-debt-lightBlue hover:text-white"
//             )}
//           >
//             <FileTextIcon className="mr-3 h-5 w-5" />
//             Créances
//           </Link>
          
//           <Link 
//             to="/payments" 
//             className={cn(
//               "flex items-center px-4 py-3 text-sm rounded-md transition-colors",
//               isActive("/payments") 
//                 ? "bg-debt-lightBlue text-white" 
//                 : "text-gray-200 hover:bg-debt-lightBlue hover:text-white"
//             )}
//           >
//             <BanknoteIcon className="mr-3 h-5 w-5" />
//             Règlements
//           </Link>
          
//           <Link 
//             to="/reminders" 
//             className={cn(
//               "flex items-center px-4 py-3 text-sm rounded-md transition-colors",
//               isActive("/reminders") 
//                 ? "bg-debt-lightBlue text-white" 
//                 : "text-gray-200 hover:bg-debt-lightBlue hover:text-white"
//             )}
//           >
//             <Bell className="mr-3 h-5 w-5" />
//             Relances
//           </Link>
          
//           <Link 
//             to="/clients" 
//             className={cn(
//               "flex items-center px-4 py-3 text-sm rounded-md transition-colors",
//               isActive("/clients") 
//                 ? "bg-debt-lightBlue text-white" 
//                 : "text-gray-200 hover:bg-debt-lightBlue hover:text-white"
//             )}
//           >
//             <Users className="mr-3 h-5 w-5" />
//             Clients
//           </Link>
//           {currentUser?.role === 'admin' && (
//             <Link
//               to="/utilisateurs"
//               className={cn(
//                 "flex items-center px-4 py-3 text-sm rounded-md transition-colors",
//                 isActive("/users")
//                   ? "bg-debt-lightBlue text-white"
//                   : "text-gray-200 hover:bg-debt-lightBlue hover:text-white"
//               )}
//             >
//               <Users className="mr-3 h-5 w-5" />
//               Utilisateurs
//             </Link>
//           )}
//         </nav>
//       </div>
      
//       <div className="p-4 border-t border-debt-lightBlue">
//         <Link 
//           to="/profile" 
//           className="flex items-center px-4 py-3 text-sm rounded-md text-gray-200 hover:bg-debt-lightBlue hover:text-white transition-colors"
//         >
//           <div className="h-8 w-8 rounded-full bg-debt-lightBlue flex items-center justify-center text-white font-medium mr-3">
//             {currentUser?.name.charAt(0).toUpperCase() || "U"}
//           </div>
//           <div className="flex-1 truncate">
//             <div className="font-medium">{currentUser?.name || "Utilisateur"}</div>
//             <div className="text-xs text-gray-300 truncate">{currentUser?.email || "user@example.com"}</div>
//           </div>
//         </Link>
        
//         <div className="mt-4 flex justify-between">
//           {/* <Link 
//             to="/settings" 
//             className="flex items-center px-4 py-2 text-sm rounded-md text-gray-200 hover:bg-debt-lightBlue hover:text-white transition-colors"
//           >
//             <Settings className="h-5 w-5" />
//           </Link> */}
          
//           <button 
//             onClick={() => logout()}
//             className="flex items-center px-4 py-2 text-sm rounded-md text-gray-200 hover:bg-debt-lightBlue hover:text-white transition-colors"
//           >
//             <LogOut className="h-5 w-5" />
//           </button>
//         </div>
//       </div>
//     </div>
//   );
// };

// export default Sidebar;
