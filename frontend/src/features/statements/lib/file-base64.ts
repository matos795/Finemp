/** Conteúdo do arquivo em Base64, para reapresentá-lo na confirmação. */
export function readFileAsBase64(
    file: File,
): Promise<string> {
    return new Promise((resolve, reject) => {
        const reader = new FileReader()

        reader.onload = () => {
            const result = String(reader.result ?? "")
            resolve(result.slice(result.indexOf(",") + 1))
        }

        reader.onerror = () =>
            reject(reader.error ?? new Error("Não foi possível ler o arquivo."))

        reader.readAsDataURL(file)
    })
}
