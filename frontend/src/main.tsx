import { createRoot } from 'react-dom/client'
import App from './App.tsx'
import ErrorBoundary from './components/ErrorBoundary.tsx'
import './index.css'

// Boundary racine, AU-DESSUS des providers et du routeur : une erreur levee dans
// AuthProvider ou QueryClientProvider laissait sinon une page blanche.
createRoot(document.getElementById("root")!).render(
  <ErrorBoundary variant="app">
    <App />
  </ErrorBoundary>
);
