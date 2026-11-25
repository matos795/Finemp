import { useEffect, useState } from "react"

/** Valor estável depois de `delay` ms sem mudança (busca digitada). */
export function useDebounced<T>(value: T, delay: number) {
    const [debounced, setDebounced] = useState(value)

    useEffect(() => {
        const timer = window.setTimeout(() => setDebounced(value), delay)

        return () => window.clearTimeout(timer)
    }, [value, delay])

    return debounced
}
