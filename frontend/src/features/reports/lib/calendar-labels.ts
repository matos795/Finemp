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
