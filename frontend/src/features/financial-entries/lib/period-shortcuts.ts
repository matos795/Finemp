/** Primeiro e último dia do mês de uma data ISO ("Este mês" do protótipo). */
export function currentMonthRange(todayIso: string) {
    const [year, month] = todayIso.split("-").map(Number)
    const lastDay = new Date(Date.UTC(year, month, 0)).getUTCDate()
    const prefix = todayIso.slice(0, 7)

    return {
        from: `${prefix}-01`,
        to: `${prefix}-${String(lastDay).padStart(2, "0")}`,
    }
}
