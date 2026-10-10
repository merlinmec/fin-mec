import type { KeyboardEvent } from "react";

const STEP: Record<string, number> = { ArrowRight: 1, ArrowDown: 1, ArrowLeft: -1, ArrowUp: -1 };

/**
 * Teclado do padrão ARIA de radio group para os seletores feitos com botões (role="radio"):
 * setas andam entre as opções (com volta ao início) e já selecionam, Home/End vão às pontas.
 * Vai no onKeyDown do elemento com role="radiogroup"; os botões usam radioTabIndex para que o
 * grupo inteiro seja uma parada só de Tab.
 */
export function handleRadioGroupKeyDown(event: KeyboardEvent<HTMLElement>) {
  const radios = Array.from(
    event.currentTarget.querySelectorAll<HTMLElement>('[role="radio"]:not([disabled])'),
  );
  const current = radios.indexOf(document.activeElement as HTMLElement);
  if (current < 0 || radios.length === 0) return;

  let next: number;
  if (event.key in STEP) next = (current + STEP[event.key] + radios.length) % radios.length;
  else if (event.key === "Home") next = 0;
  else if (event.key === "End") next = radios.length - 1;
  else return;

  event.preventDefault();
  radios[next].focus();
  radios[next].click();
}

/** Roving tabindex: só a opção marcada entra no Tab; sem nenhuma marcada, a primeira. */
export function radioTabIndex(checked: boolean, index: number, anyChecked: boolean): 0 | -1 {
  return checked || (!anyChecked && index === 0) ? 0 : -1;
}
