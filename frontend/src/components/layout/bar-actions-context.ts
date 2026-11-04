import { createContext } from "react"

/** Elemento da barra (.barra-acoes) onde cada tela coloca suas ações. */
export const BarActionsContext =
    createContext<HTMLElement | null>(null)
