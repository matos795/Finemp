const shortMonthLabels = [
    "jan", "fev", "mar", "abr", "mai", "jun",
    "jul", "ago", "set", "out", "nov", "dez",
]

/** Competência YYYY-MM como no protótipo (fmtYM): "jul/26". Só formata. */
export function formatCompetence(competence: string) {
    const match = /^(\d{2})(\d{2})-(\d{2})/.exec(competence)
    const month = match ? Number(match[3]) : 0

    if (!match || month < 1 || month > 12) {
        return competence
    }

    return `${shortMonthLabels[month - 1]}/${match[2]}`
}

/** Rótulos de mês do protótipo (MESES abreviados). */
export const monthLabels = [
    "Jan", "Fev", "Mar", "Abr", "Mai", "Jun",
    "Jul", "Ago", "Set", "Out", "Nov", "Dez",
]

const weekdayLabels = ["dom", "seg", "ter", "qua", "qui", "sex", "sáb"]

/** "02 seg" para YYYY-MM-DD, sem depender do fuso do navegador. */
export function dayLabel(isoDate: string) {
    const [year, month, day] = isoDate.split("-").map(Number)
    const weekday = new Date(Date.UTC(year, month - 1, day)).getUTCDay()

    return `${String(day).padStart(2, "0")} ${weekdayLabels[weekday]}`
}
