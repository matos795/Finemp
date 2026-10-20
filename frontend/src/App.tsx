import { useState } from 'react'
import './App.css'

type Tab = 'classification' | 'dre'

type ClassificationStatus = 'PENDING' | 'CLASSIFIED'

type DreClassification = {
  id: string
  dreClassification: string
  dreOpenClassification: string
  ebitdaClassification: string
}

type AccountingAccountClassification = {
  accountId: string
  accountingSourceId: string
  accountKey: string
  description: string
  status: ClassificationStatus
  classificationId: string | null
  dreClassification: string | null
  dreOpenClassification: string | null
  ebitdaClassification: string | null
}

type DreLine = {
  dreClassification: string
  dreOpenClassification: string
  ebitdaClassification: string
  total: number
}

type DreResult = {
  lines: DreLine[]
  unclassifiedAccountCount: number
}

function App() {
  const [tab, setTab] = useState<Tab>('classification')

  const [companyId, setCompanyId] = useState('')

  const [accounts, setAccounts] =
    useState<AccountingAccountClassification[]>([])

  const [classifications, setClassifications] =
    useState<DreClassification[]>([])

  const [selectedClassifications, setSelectedClassifications] =
    useState<Record<string, string>>({})

  const [startDate, setStartDate] = useState('2026-07-01')
  const [endDate, setEndDate] = useState('2026-07-31')

  const [dre, setDre] = useState<DreResult | null>(null)

  const [loading, setLoading] = useState(false)
  const [message, setMessage] = useState('')

  function validateCompanyId() {
    if (!companyId.trim()) {
      setMessage('Informe o ID da empresa.')
      return false
    }

    return true
  }

  async function loadClassificationData() {
    if (!validateCompanyId()) {
      return
    }

    setLoading(true)
    setMessage('')

    try {
      const [accountsResponse, classificationsResponse] =
        await Promise.all([
          fetch(
            `/api/companies/${companyId}/accounting-accounts/classifications`,
          ),
          fetch('/api/dre-classifications'),
        ])

      if (!accountsResponse.ok) {
        throw new Error('Não foi possível carregar as contas.')
      }

      if (!classificationsResponse.ok) {
        throw new Error(
          'Não foi possível carregar o catálogo de classificações.',
        )
      }

      const accountsData: AccountingAccountClassification[] =
        await accountsResponse.json()

      const classificationsData: DreClassification[] =
        await classificationsResponse.json()

      setAccounts(accountsData)
      setClassifications(classificationsData)

      const currentSelections: Record<string, string> = {}

      accountsData.forEach((account) => {
        if (account.classificationId) {
          currentSelections[account.accountId] =
            account.classificationId
        }
      })

      setSelectedClassifications(currentSelections)
    } catch (error) {
      setMessage(
        error instanceof Error
          ? error.message
          : 'Erro inesperado.',
      )
    } finally {
      setLoading(false)
    }
  }

  async function saveClassification(accountId: string) {
    const classificationId =
      selectedClassifications[accountId]

    if (!classificationId) {
      setMessage('Selecione uma classificação.')
      return
    }

    setLoading(true)
    setMessage('')

    try {
      const response = await fetch(
        `/api/companies/${companyId}/accounting-accounts/${accountId}/classification`,
        {
          method: 'PUT',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({
            classificationId,
          }),
        },
      )

      if (!response.ok) {
        throw new Error(
          'Não foi possível salvar a classificação.',
        )
      }

      setMessage('Classificação salva com sucesso.')

      await loadClassificationData()
    } catch (error) {
      setMessage(
        error instanceof Error
          ? error.message
          : 'Erro inesperado.',
      )
    } finally {
      setLoading(false)
    }
  }

  async function loadDre() {
    if (!validateCompanyId()) {
      return
    }

    setLoading(true)
    setMessage('')

    try {
      const params = new URLSearchParams({
        startDate,
        endDate,
      })

      const response = await fetch(
        `/api/companies/${companyId}/dre?${params}`,
      )

      if (!response.ok) {
        throw new Error('Não foi possível calcular a DRE.')
      }

      const data: DreResult = await response.json()

      setDre(data)
    } catch (error) {
      setMessage(
        error instanceof Error
          ? error.message
          : 'Erro inesperado.',
      )
    } finally {
      setLoading(false)
    }
  }

  function formatCurrency(value: number) {
    return new Intl.NumberFormat('pt-BR', {
      style: 'currency',
      currency: 'BRL',
    }).format(value)
  }

  return (
    <main className="app">
      <header className="header">
        <div>
          <h1>FINEMP</h1>
          <p>Gestão e análise contábil</p>
        </div>
      </header>

      <nav className="tabs">
        <button
          className={tab === 'classification' ? 'active' : ''}
          onClick={() => setTab('classification')}
        >
          Classificação de contas
        </button>

        <button
          className={tab === 'dre' ? 'active' : ''}
          onClick={() => setTab('dre')}
        >
          DRE
        </button>
      </nav>

      <section className="company-filter">
        <label>
          Empresa
          <input
            value={companyId}
            onChange={(event) =>
              setCompanyId(event.target.value)
            }
            placeholder="UUID da empresa"
          />
        </label>
      </section>

      {message && (
        <div className="message">
          {message}
        </div>
      )}

      {tab === 'classification' && (
        <section>
          <div className="section-header">
            <div>
              <h2>Classificação de contas</h2>
              <p>
                Vincule as contas contábeis às classificações
                gerenciais da FINEMP.
              </p>
            </div>

            <button
              onClick={loadClassificationData}
              disabled={loading}
            >
              {loading ? 'Carregando...' : 'Carregar contas'}
            </button>
          </div>

          <div className="summary">
            <span>
              Total: <strong>{accounts.length}</strong>
            </span>

            <span>
              Classificadas:{' '}
              <strong>
                {
                  accounts.filter(
                    (account) =>
                      account.status === 'CLASSIFIED',
                  ).length
                }
              </strong>
            </span>

            <span>
              Pendentes:{' '}
              <strong>
                {
                  accounts.filter(
                    (account) =>
                      account.status === 'PENDING',
                  ).length
                }
              </strong>
            </span>
          </div>

          <div className="table-wrapper">
            <table>
              <thead>
                <tr>
                  <th>Conta</th>
                  <th>Descrição</th>
                  <th>Status</th>
                  <th>DRE</th>
                  <th>DRE Aberta</th>
                  <th>EBITDA</th>
                  <th>Classificação</th>
                  <th></th>
                </tr>
              </thead>

              <tbody>
                {accounts.map((account) => (
                  <tr key={account.accountId}>
                    <td>{account.accountKey}</td>

                    <td>{account.description}</td>

                    <td>
                      <span
                        className={`status ${account.status.toLowerCase()}`}
                      >
                        {account.status}
                      </span>
                    </td>

                    <td>
                      {account.dreClassification ?? '—'}
                    </td>

                    <td>
                      {account.dreOpenClassification ?? '—'}
                    </td>

                    <td>
                      {account.ebitdaClassification ?? '—'}
                    </td>

                    <td>
                      <select
                        value={
                          selectedClassifications[
                          account.accountId
                          ] ?? ''
                        }
                        onChange={(event) =>
                          setSelectedClassifications(
                            (current) => ({
                              ...current,
                              [account.accountId]:
                                event.target.value,
                            }),
                          )
                        }
                      >
                        <option value="">
                          Selecione...
                        </option>

                        {classifications.map(
                          (classification) => (
                            <option
                              key={classification.id}
                              value={classification.id}
                            >
                              {
                                classification.dreClassification
                              }
                              {' / '}
                              {
                                classification.dreOpenClassification
                              }
                              {' / '}
                              {
                                classification.ebitdaClassification
                              }
                            </option>
                          ),
                        )}
                      </select>
                    </td>

                    <td>
                      <button
                        onClick={() =>
                          saveClassification(
                            account.accountId,
                          )
                        }
                        disabled={loading}
                      >
                        Salvar
                      </button>
                    </td>
                  </tr>
                ))}

                {accounts.length === 0 && (
                  <tr>
                    <td colSpan={8} className="empty">
                      Nenhuma conta carregada.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </section>
      )}

      {tab === 'dre' && (
        <section>
          <div className="section-header">
            <div>
              <h2>DRE</h2>
              <p>
                Resultado gerencial calculado a partir dos
                movimentos sincronizados.
              </p>
            </div>
          </div>

          <div className="period-filter">
            <label>
              Data inicial
              <input
                type="date"
                value={startDate}
                onChange={(event) =>
                  setStartDate(event.target.value)
                }
              />
            </label>

            <label>
              Data final
              <input
                type="date"
                value={endDate}
                onChange={(event) =>
                  setEndDate(event.target.value)
                }
              />
            </label>

            <button
              onClick={loadDre}
              disabled={loading}
            >
              {loading ? 'Calculando...' : 'Consultar DRE'}
            </button>
          </div>

          {dre && (
            <>
              <div className="summary">
                <span>
                  Linhas:{' '}
                  <strong>{dre.lines.length}</strong>
                </span>

                <span>
                  Contas pendentes:{' '}
                  <strong>
                    {dre.unclassifiedAccountCount}
                  </strong>
                </span>
              </div>

              <div className="table-wrapper">
                <table>
                  <thead>
                    <tr>
                      <th>DRE</th>
                      <th>DRE Aberta</th>
                      <th>EBITDA / Grupo</th>
                      <th className="numeric">Total</th>
                    </tr>
                  </thead>

                  <tbody>
                    {dre.lines.map((line, index) => (
                      <tr
                        key={`${line.dreClassification}-${line.dreOpenClassification}-${line.ebitdaClassification}-${index}`}
                      >
                        <td>
                          {line.dreClassification}
                        </td>

                        <td>
                          {line.dreOpenClassification}
                        </td>

                        <td>
                          {line.ebitdaClassification}
                        </td>

                        <td className="numeric">
                          {formatCurrency(line.total)}
                        </td>
                      </tr>
                    ))}

                    {dre.lines.length === 0 && (
                      <tr>
                        <td
                          colSpan={4}
                          className="empty"
                        >
                          Nenhuma linha encontrada para o
                          período.
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            </>
          )}
        </section>
      )}
    </main>
  )
}

export default App