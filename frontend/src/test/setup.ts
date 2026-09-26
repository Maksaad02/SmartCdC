import "@testing-library/jest-dom/vitest";
import { afterEach } from "vitest";
import { cleanup } from "@testing-library/react";

// Sans globals, Testing Library ne demonte pas les composants entre les tests.
afterEach(() => {
  cleanup();
  localStorage.clear();
});

// jsdom n'implemente pas scrollIntoView (utilise par le chatbot pour suivre la conversation).
if (!Element.prototype.scrollIntoView) {
  Element.prototype.scrollIntoView = () => {};
}

// Radix (Select, Dialog) s'appuie sur des API de pointeur absentes de jsdom.
if (!Element.prototype.hasPointerCapture) {
  Element.prototype.hasPointerCapture = () => false;
}
if (!Element.prototype.releasePointerCapture) {
  Element.prototype.releasePointerCapture = () => {};
}
if (!Element.prototype.setPointerCapture) {
  Element.prototype.setPointerCapture = () => {};
}
