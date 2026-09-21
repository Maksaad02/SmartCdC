import React from "react";

interface Props {
  children: React.ReactNode;
}

interface State {
  hasError: boolean;
}

/**
 * Filet de sécurité de rendu.
 *
 * Sans cela, une exception levée pendant le rendu d'une page démonte tout l'arbre
 * React et laisse une page blanche sans recours.
 */
class ErrorBoundary extends React.Component<Props, State> {
  state: State = { hasError: false };

  static getDerivedStateFromError(): State {
    return { hasError: true };
  }

  componentDidCatch(error: Error, info: React.ErrorInfo) {
    console.error("Erreur de rendu non interceptée:", error, info.componentStack);
  }

  handleReset = () => {
    this.setState({ hasError: false });
  };

  render() {
    if (this.state.hasError) {
      return (
        <div className="min-h-screen flex items-center justify-center bg-[#F3F4F6] p-6">
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
                onClick={this.handleReset}
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

    return this.props.children;
  }
}

export default ErrorBoundary;
