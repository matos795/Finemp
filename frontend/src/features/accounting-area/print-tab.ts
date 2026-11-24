/**
 * imprimirAba do protótipo: A4 paisagem, cabeçalho de impressão (.imp-cab),
 * classes imp-x/imp-inv na raiz da área (o "body" do CSS escopado) e
 * .imp-alvo na section da aba, com zoom para caber a tabela mais larga.
 * Tudo é desfeito depois da impressão.
 */
export function printAccountingTab(screenId: string, title: string, companyName: string) {
    const root = document.querySelector<HTMLElement>(".ctbp")
    const section = document.getElementById(`p-${screenId}`)

    if (!root || !section) {
        window.print()
        return
    }

    const page = document.createElement("style")
    page.textContent = "@page{size:A4 landscape;margin:8mm}"
    document.head.appendChild(page)

    const header = document.createElement("div")
    header.className = "imp-cab"

    for (const [tag, text] of [
        ["b", companyName],
        ["span", title],
        ["span", document.getElementById("legend")?.textContent ?? ""],
        ["span", `Impresso em ${new Date().toLocaleString("pt-BR")}`],
    ] as const) {
        const element = document.createElement(tag)
        element.textContent = text
        header.appendChild(element)
    }

    section.prepend(header)
    root.classList.add("imp-x", "imp-inv")
    section.classList.add("imp-alvo")

    let width = section.clientWidth
    section.querySelectorAll("table").forEach((table) => {
        width = Math.max(width, table.scrollWidth + 40)
    })
    const zoom = Math.min(1, 1060 / Math.max(1, width))
    section.style.setProperty("zoom", zoom < 0.98 ? String(zoom) : "")

    let done = false
    const finish = () => {
        if (done) return
        done = true
        root.classList.remove("imp-x", "imp-inv")
        section.classList.remove("imp-alvo")
        section.style.removeProperty("zoom")
        header.remove()
        page.remove()
    }

    window.addEventListener("afterprint", finish, { once: true })
    window.setTimeout(() => {
        window.print()
        window.setTimeout(finish, 600)
    }, 120)
}
