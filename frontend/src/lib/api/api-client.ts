export class ApiError extends Error {
  public readonly status: number

  /** Código estável opcional do ProblemDetail (ex.: STALE_VERSION). */
  public readonly code?: string

  constructor(
    status: number,
    message: string,
    code?: string,
  ) {
    super(message)

    this.status = status
    this.code = code
    this.name = "ApiError"
  }
}

async function readProblemCode(
  response: Response,
): Promise<string | undefined> {
  const contentType = response.headers.get("Content-Type") ?? ""

  if (!contentType.includes("json")) {
    return undefined
  }

  try {
    const body: unknown = await response.json()

    if (
      body &&
      typeof body === "object" &&
      "code" in body &&
      typeof body.code === "string"
    ) {
      return body.code
    }
  } catch {
    // Corpo ausente ou inválido: fica só o status.
  }

  return undefined
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

  if (options.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json")
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
    throw new ApiError(
      response.status,
      response.status === 401
        ? "E-mail ou senha inválidos."
        : "Não foi possível concluir a operação.",
      await readProblemCode(response),
    )
  }

  if (response.status === 204) {
    return undefined as T
  }

  return response.json()
}