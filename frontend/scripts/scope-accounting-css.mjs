// Gera src/styles/accounting-prototype.css a partir do CSS do protótipo contábil
// (FRONTEND_CONTABIL_REFERENCE/fonte/prototipo.css), sem renomear classes.
//
// Todo seletor passa a valer só dentro da Área da Contabilidade (.ctbp):
//   :root, html, body      -> .ctbp
//   body.x Y               -> .ctbp.x Y
//   html:has(body.x)       -> .ctbp.x
//   qualquer outro seletor -> .ctbp <seletor>
// Regras de @keyframes não são alteradas.
//
// Uso: node scripts/scope-accounting-css.mjs <prototipo.css> <saida.css>
// O protótipo não é versionado (contém dados reais em outros arquivos do pacote);
// só o CSS gerado entra no repositório.
import fs from "node:fs"
import postcss from "postcss"

const ROOT = ".ctbp"

export function scopeSelector(selector) {
    const s = selector.trim()

    if (s === ":root" || s === "html" || s === "body") {
        return ROOT
    }

    // html:has(body.x) — a raiz da área faz o papel de html e de body.
    const htmlHasBody = /^html:has\(body((?:\.[\w-]+)+)\)(.*)$/.exec(s)

    if (htmlHasBody) {
        return `${ROOT}${htmlHasBody[1]}${htmlHasBody[2]}`
    }

    const bodyClass = /^body(\.[\w-]+)+(\s|$)/.exec(s)

    if (bodyClass) {
        return `${ROOT}${s.slice(4)}`
    }

    if (s.startsWith("html ") || s.startsWith("body ")) {
        return `${ROOT} ${s.slice(5).trim()}`
    }

    return `${ROOT} ${s}`
}

export function scopeCss(css) {
    const root = postcss.parse(css)

    root.walkRules((rule) => {
        if (rule.parent?.type === "atrule" && /keyframes$/i.test(rule.parent.name)) {
            return
        }

        rule.selectors = [...new Set(rule.selectors.map(scopeSelector))]
    })

    return root.toString()
}

const isCli = process.argv[1] && import.meta.url.endsWith(process.argv[1].split("/").pop())

if (isCli) {
    const [input, output] = process.argv.slice(2)

    if (!input || !output) {
        console.error("Uso: node scripts/scope-accounting-css.mjs <prototipo.css> <saida.css>")
        process.exit(1)
    }

    const header =
        "/* Gerado por scripts/scope-accounting-css.mjs a partir do CSS do protótipo contábil\n" +
        "   (FRONTEND_CONTABIL_REFERENCE, v105). Classes preservadas; escopo .ctbp (Área da\n" +
        "   Contabilidade). Não editar à mão: ajustes vão em accounting-area.css. */\n"

    fs.writeFileSync(output, header + scopeCss(fs.readFileSync(input, "utf8")))
}
