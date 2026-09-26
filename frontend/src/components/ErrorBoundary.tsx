import React from "react";
import { reportError } from "@/lib/reportError";

interface Props {
  children: React.ReactNode;
  /**
   * Quand cette valeur change (ex. la route), l'erreur est effacee : sans cela,
   * naviguer vers une autre page laissait l'ecran d'erreur affiche.
   */
  resetKey?: unknown;
  /**
   * "app" : boundary racine, hors du routeur et des providers ; "Reessayer"
   * recharge la page. "page" : boundary de route, "Reessayer" remonte le
   * sous-arbre sans recharger.
   */
  variant?: "app" | "page";
}

interface State {
  hasError: boolean;
  /** Incremente a chaque reessai : sert de `key` pour remonter vraiment le sous-arbre. */
  attempt: number;
}

/**
 * Filet de securite de rendu.
 *
 * Sans cela, une exception levee pendant le rendu d'une page demonte tout
 * l'arbre React et laisse une page blanche sans recours.
 */
class ErrorBoundary extends React.Component<Props, State> {
  state: State = { hasError: false, attempt: 0 };

  static getDerivedStateFromError(): Partial<State> {
    return { hasError: true };
  }

  componentDidCatch(error: Error, info: React.ErrorInfo) {
    reportError(error, { componentStack: info.componentStack });
  }

  componentDidUpdate(prevProps: Props) {
    if (this.state.hasError && prevProps.resetKey !== this.props.resetKey) {
      this.setState(s => ({ hasError: false, attempt: s.attempt + 1 }));
    }
  }

  handleRetry = () => {
    if (this.props.variant === "app") {
      window.location.reload();
      return;
    }
    // Sans changer `attempt`, React re-rendrait le meme composant qui
    // re-leverait aussitot la meme erreur.
    this.setState(s => ({ hasError: false, attempt: s.attempt + 1 }));
  };

  render() {
    if (this.state.hasError) {
      return (
        <div
          role="alert"
          className="min-h-screen flex items-center justify-center bg-[#F3F4F6] p-6"
        >
          <div className="max-w-md w-full bg-white border border-gray-200 rounded-lg shadow-sm p-6 text-center">
            <h1 className="text-lg font-semibold text-gray-900 mb-2">
              Une erreur est survenue
            </h1>
            <p className="text-sm text-gray-600 mb-6">
              La page n'a pas pu s'afficher. Vous pouvez réessayer ou revenir à
              l'accueil.
            </p>
            <div className="flex gap-3 justify-center">
              <button
                onClick={this.handleRetry}
                className="px-4 py-2 rounded bg-blue-500 text-white text-sm hover:bg-blue-600 transition"
              >
                Réessayer
              </button>
              <a
                href="/"
                className="px-4 py-2 rounded border border-gray-300 text-gray-700 text-sm hover:bg-gray-50 transition"
              >
                Accueil
              </a>
            </div>
          </div>
        </div>
      );
    }

    return <React.Fragment key={this.state.attempt}>{this.props.children}</React.Fragment>;
  }
}

export default ErrorBoundary;
