/** Hoje no fuso do navegador, em YYYY-MM-DD (sem converter para UTC). */
export function todayIso() {
    const today = new Date()
    const month = String(today.getMonth() + 1).padStart(2, "0")
    const day = String(today.getDate()).padStart(2, "0")

    return `${today.getFullYear()}-${month}-${day}`
}
