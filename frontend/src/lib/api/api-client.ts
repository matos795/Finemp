export class ApiError extends Error {
  public readonly status: number

  constructor(
    status: number,
    message: string,
  ) {
    super(message)

    this.status = status
    this.name = "ApiError"
  }
}

interface CsrfResponse {
  headerName: string
  token: string
}

async function getCsrfToken(): Promise<CsrfResponse> {
  const response = await fetch("/api/auth/csrf", {
    credentials: "include",
  })

  if (!response.ok) {
    throw new ApiError(
      response.status,
      "Não foi possível iniciar a sessão segura.",
    )
  }

  return response.json()
}

export async function apiRequest<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const method = options.method?.toUpperCase() ?? "GET"

  const headers = new Headers(options.headers)

  const isFormData =
    options.body instanceof FormData

  if (
    options.body
    && !isFormData
    && !headers.has("Content-Type")
  ) {
    headers.set(
      "Content-Type",
      "application/json",
    )
  }

  if (!["GET", "HEAD", "OPTIONS"].includes(method)) {
    const csrf = await getCsrfToken()
    headers.set(csrf.headerName, csrf.token)
  }

  const response = await fetch(path, {
    ...options,
    headers,
    credentials: "include",
  })

  if (!response.ok) {
    let message =
      response.status === 401
        ? "E-mail ou senha inválidos."
        : "Não foi possível concluir a operação."

    const contentType =
      response.headers.get("content-type")

    if (contentType?.includes("application/json")) {
      try {
        const body =
          await response.json() as {
            title?: string
            detail?: string
          }

        if (body.detail) {
          message = body.detail
        } else if (body.title) {
          message = body.title
        }
      } catch {
        // Mantém a mensagem padrão.
      }
    }

    throw new ApiError(
      response.status,
      message,
    )
  }

  if (response.status === 204) {
    return undefined as T
  }

  return response.json()
}