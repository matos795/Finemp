/**
 * Ícones do menu do protótipo homologado (constante IC), viewBox 0 0 20 20.
 * Marcação estática copiada do protótipo; não recebe dado externo.
 */
export const menuIcons = {
    fluxo: "<path d=\"M3 15l4-5 3 3 6-8\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.8\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/><path d=\"M12 5h4v4\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.8\" stroke-linecap=\"round\"/>",
    cad: "<circle cx=\"8\" cy=\"7\" r=\"3\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.8\"/><path d=\"M2.5 16c.8-3 3-4.5 5.5-4.5s4.7 1.5 5.5 4.5M14 6h4M14 9.5h4\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.8\" stroke-linecap=\"round\"/>",
    ctr: "<path d=\"M5 2.5h7l3.5 3.5v11.5H5z\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.8\" stroke-linejoin=\"round\"/><path d=\"M8 9h5M8 12h5M8 15h3\" stroke=\"currentColor\" stroke-width=\"1.6\" stroke-linecap=\"round\"/>",
    orc: "<circle cx=\"10\" cy=\"10\" r=\"7\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.8\"/><path d=\"M10 3v7h7\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.8\" stroke-linejoin=\"round\"/>",
    ctb: "<path d=\"M3 4h14M3 8h14M3 12h14M3 16h14M10 4v12\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.6\" stroke-linecap=\"round\"/>",
    imp: "<path d=\"M6 8l4-4 4 4M10 4v9M4 14v2.5h12V14\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.8\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/>",
    rel: "<path d=\"M4 17V9M8.5 17V5M13 17v-6M17.5 17V3\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\"/>",
    div: "<rect x=\"2.5\" y=\"5\" width=\"15\" height=\"10.5\" rx=\"2\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.8\"/><path d=\"M2.5 8.5h15M6 12.5h3\" stroke=\"currentColor\" stroke-width=\"1.8\" stroke-linecap=\"round\"/>",
    pln: "<path d=\"M4 3.5h12v14H4z\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.7\" stroke-linejoin=\"round\"/><path d=\"M7 8l1.5 1.5L11 7M7 13l1.5 1.5L11 12M13 8.5h1.5M13 13.5h1.5\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.6\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/>",
    crm: "<circle cx=\"10\" cy=\"10\" r=\"7\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.8\"/><circle cx=\"10\" cy=\"10\" r=\"3.5\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.8\"/><circle cx=\"10\" cy=\"10\" r=\"1\" fill=\"currentColor\"/>",
    est: "<path d=\"M3 6.5L10 3l7 3.5v7L10 17l-7-3.5z\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.7\" stroke-linejoin=\"round\"/><path d=\"M3 6.5L10 10l7-3.5M10 10v7\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.7\" stroke-linejoin=\"round\"/>",
    pat: "<rect x=\"3\" y=\"6\" width=\"14\" height=\"10\" rx=\"1.5\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.7\"/><path d=\"M7 6V4.5h6V6M3 10.5h14\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.7\" stroke-linejoin=\"round\"/>",
    ajuda: "<circle cx=\"10\" cy=\"10\" r=\"7.5\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.7\"/><path d=\"M7.8 7.6a2.3 2.3 0 114 1.6c-.9.6-1.6 1-1.6 2.1M10.2 14v.3\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.7\" stroke-linecap=\"round\"/>",
    dem: "<circle cx=\"4.5\" cy=\"10\" r=\"1.7\" fill=\"currentColor\"/><circle cx=\"10\" cy=\"10\" r=\"1.7\" fill=\"currentColor\"/><circle cx=\"15.5\" cy=\"10\" r=\"1.7\" fill=\"currentColor\"/>",
    pes: "<circle cx=\"7\" cy=\"6.5\" r=\"2.6\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.7\"/><circle cx=\"14\" cy=\"7.5\" r=\"2.1\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.6\"/><path d=\"M2.5 16.5c.6-3 2.4-4.5 4.5-4.5s3.9 1.5 4.5 4.5M12 12.3c.6-.3 1.3-.4 2-.4 1.8 0 3.2 1.3 3.6 4\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.7\" stroke-linecap=\"round\"/>",
    bkp: "<ellipse cx=\"10\" cy=\"5\" rx=\"6\" ry=\"2.5\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.7\"/><path d=\"M4 5v10c0 1.4 2.7 2.5 6 2.5s6-1.1 6-2.5V5M4 10c0 1.4 2.7 2.5 6 2.5s6-1.1 6-2.5\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.7\"/>",
    grp: "<rect x=\"2.5\" y=\"10\" width=\"6\" height=\"7\" rx=\"1\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.7\"/><rect x=\"11.5\" y=\"10\" width=\"6\" height=\"7\" rx=\"1\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.7\"/><rect x=\"7\" y=\"2.5\" width=\"6\" height=\"5\" rx=\"1\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.7\"/><path d=\"M10 7.5V9M5.5 10V9h9v1\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.6\"/>",
} as const

export type MenuIconName = keyof typeof menuIcons

