import { useEffect, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Modal } from "../../../components/finemp/modal"
import { Pager } from "../../../components/finemp/pager"
import { QueryError } from "../../../components/finemp/query-error"
import { useToast } from "../../../components/finemp/toast-context"
import { useCompanyPage } from "../../../lib/api/use-company-page"
import { formatExactCurrency, formatIsoDate } from "../../../lib/formatters/decimal"
import { ApiError } from "../../../lib/api/api-client"
import { listBusinessPartnersPage } from "../../business-partners/api/business-partners-api"
import { useCompanyAccess } from "../../companies/company-access-context"
import { listAllResultCenters } from "../../result-centers/api/result-center-api"
import {
    createAsset,
    getAssetSummary,
    listAssets,
    updateAsset,
} from "../api/assets-api"
import {
    assetCategoryOptions,
    assetStatusLabel,
    type Asset,
    type AssetCategory,
    type AssetFilter,
    type AssetStatus,
    type AssetValues,
} from "../types/asset.types"

const categoryLabel = Object.fromEntries(
    assetCategoryOptions.map((option) => [option.value, option.label]),
) as Record<AssetCategory, string>

type DialogState = { asset?: Asset } | null

export function AssetsPage() {
    const { selectedCompany } = useCompanyAccess()
    return <AssetsCompanyPage key={selectedCompany?.id ?? "no-company"} />
}

function AssetsCompanyPage() {
    const { selectedCompany, hasPermission } = useCompanyAccess()
    const companyId = selectedCompany?.id
    const canManage = Boolean(companyId) && hasPermission("ASSET_MANAGE")
    const queryClient = useQueryClient()
    const showToast = useToast()

    const [filter, setFilter] = useState<AssetFilter>({
        search: "",
        status: "",
        category: "",
    })
    const [searchText, setSearchText] = useState("")
    const [dialog, setDialog] = useState<DialogState>(null)
    const [page, setPage] = useCompanyPage(companyId, JSON.stringify(filter))

    const assets = useQuery({
        queryKey: ["assets", companyId, filter, page] as const,
        queryFn: ({ signal }) => listAssets(companyId!, filter, page, signal),
        enabled: Boolean(companyId),
        placeholderData: (previous) => previous,
        retry: false,
    })

    const summary = useQuery({
        queryKey: ["assets-summary", companyId] as const,
        queryFn: ({ signal }) => getAssetSummary(companyId!, signal),
        enabled: Boolean(companyId),
        retry: false,
    })

    useEffect(() => {
        if (searchText === filter.search) return
        const timer = window.setTimeout(
            () => setFilter((current) => ({ ...current, search: searchText })),
            300,
        )
        return () => window.clearTimeout(timer)
    }, [searchText, filter.search])

    useEffect(() => {
        const totalPages = assets.data?.totalPages
        if (totalPages !== undefined && page > 0 && page >= totalPages) {
            setPage(Math.max(0, totalPages - 1))
        }
    }, [assets.data?.totalPages, page, setPage])

    const items = assets.data?.items ?? []

    return (
        <>
            <div className="filters">
                <div className="f">
                    <label htmlFor="assetStatus">Situação</label>
                    <select
                        id="assetStatus"
                        value={filter.status}
                        onChange={(event) => {
                            setPage(0)
                            setFilter({
                                ...filter,
                                status: event.target.value as AssetStatus | "",
                            })
                        }}
                    >
                        <option value="">Todos</option>
                        <option value="IN_USE">Em uso</option>
                        <option value="MAINTENANCE">Em manutenção</option>
                        <option value="IDLE">Parado</option>
                        <option value="DISPOSED">Baixado</option>
                    </select>
                </div>

                <div className="f">
                    <label htmlFor="assetCategory">Tipo de bem</label>
                    <select
                        id="assetCategory"
                        value={filter.category}
                        onChange={(event) => {
                            setPage(0)
                            setFilter({
                                ...filter,
                                category: event.target.value as AssetCategory | "",
                            })
                        }}
                    >
                        <option value="">Todos</option>
                        {assetCategoryOptions.map((option) => (
                            <option key={option.value} value={option.value}>
                                {option.label}
                            </option>
                        ))}
                    </select>
                </div>

                <div className="f">
                    <label htmlFor="assetSearch">Buscar</label>
                    <input
                        id="assetSearch"
                        type="search"
                        maxLength={120}
                        placeholder="Plaqueta, bem, marca, local…"
                        value={searchText}
                        onChange={(event) => setSearchText(event.target.value)}
                    />
                </div>

                {canManage ? (
                    <div className="f">
                        <label>&nbsp;</label>
                        <button
                            type="button"
                            className="btn"
                            onClick={() => setDialog({})}
                        >
                            + Bem
                        </button>
                    </div>
                ) : null}
            </div>

            {summary.isError ? (
                <QueryError
                    error={summary.error}
                    messages={{
                        403: "Você não tem acesso aos bens desta empresa.",
                    }}
                    fallback="Não foi possível carregar o resumo patrimonial."
                    onRetry={() => summary.refetch()}
                />
            ) : summary.data ? (
                <div className="cards">
                    <div className="card">
                        <div className="lb">Bens ativos</div>
                        <div className="vl">{summary.data.activeCount}</div>
                    </div>
                    <div className="card">
                        <div className="lb">Valor de aquisição</div>
                        <div className="vl">
                            {formatExactCurrency(summary.data.acquisitionValue)}
                        </div>
                    </div>
                    <div className="card">
                        <div className="lb">Valor contábil hoje</div>
                        <div className="vl">
                            {formatExactCurrency(summary.data.bookValue)}
                        </div>
                    </div>
                    <div className="card">
                        <div className="lb">Depreciação do mês</div>
                        <div className="vl">
                            {formatExactCurrency(summary.data.monthlyDepreciation)}
                        </div>
                    </div>
                </div>
            ) : null}

            {assets.isError ? (
                <QueryError
                    error={assets.error}
                    messages={{
                        400: "Confira os filtros informados.",
                        403: "Você não tem acesso aos bens desta empresa.",
                    }}
                    fallback="Não foi possível carregar os bens."
                    onRetry={() => assets.refetch()}
                />
            ) : (
                <div className="panel" style={{ padding: 0 }}>
                    <div
                        className="tblwrap"
                        style={assets.isPlaceholderData ? { opacity: 0.6 } : undefined}
                    >
                        <table>
                            <thead>
                                <tr>
                                    <th>Plaqueta</th>
                                    <th>Bem</th>
                                    <th>Tipo</th>
                                    <th>Aquisição</th>
                                    <th className="n">Valor pago</th>
                                    <th className="n">Depreciado</th>
                                    <th className="n">Valor contábil</th>
                                    <th>Local / responsável</th>
                                    <th>Situação</th>
                                    {canManage ? <th>Ações</th> : null}
                                </tr>
                            </thead>
                            <tbody>
                                {items.map((asset) => (
                                    <tr key={asset.id}>
                                        <td>{asset.assetTag || "—"}</td>
                                        <td>
                                            <b>{asset.description}</b>
                                            <div className="sub">
                                                {asset.quantity > 1 ? String(asset.quantity) + " un. · " : ""}
                                                {[asset.brandModel, asset.serialNumber]
                                                    .filter(Boolean)
                                                    .join(" · ") || "—"}
                                            </div>
                                        </td>
                                        <td>{categoryLabel[asset.category]}</td>
                                        <td>
                                            {formatIsoDate(asset.acquisitionDate)}
                                            <div className="sub">
                                                {asset.businessPartnerName || asset.documentReference || "Manual"}
                                            </div>
                                        </td>
                                        <td className="n">
                                            {formatExactCurrency(asset.acquisitionValue)}
                                        </td>
                                        <td className="n">
                                            {asset.depreciable
                                                ? formatExactCurrency(asset.accumulatedDepreciation)
                                                : "Não deprecia"}
                                        </td>
                                        <td className="n">
                                            {formatExactCurrency(asset.bookValue)}
                                        </td>
                                        <td>
                                            {asset.location || "—"}
                                            <div className="sub">{asset.responsible || "—"}</div>
                                        </td>
                                        <td>
                                            <span
                                                className={
                                                    asset.status === "IN_USE"
                                                        ? "badge b-ok"
                                                        : asset.status === "DISPOSED"
                                                            ? "badge b-neu"
                                                            : "badge b-gold"
                                                }
                                            >
                                                {assetStatusLabel[asset.status]}
                                            </span>
                                        </td>
                                        {canManage ? (
                                            <td>
                                                <button
                                                    type="button"
                                                    className="btn sec sm"
                                                    disabled={asset.status === "DISPOSED"}
                                                    onClick={() => setDialog({ asset })}
                                                >
                                                    Editar
                                                </button>
                                            </td>
                                        ) : null}
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>

                    {assets.isPending ? (
                        <div className="empty">Carregando…</div>
                    ) : items.length === 0 ? (
                        <div className="empty">
                            Nenhum bem encontrado.
                        </div>
                    ) : null}

                    <Pager
                        page={page}
                        totalPages={assets.data?.totalPages ?? 0}
                        disabled={assets.isFetching}
                        onChange={setPage}
                    />
                </div>
            )}

            {dialog && companyId ? (
                <AssetDialog
                    key={dialog.asset ? dialog.asset.id + "-" + dialog.asset.version : "new"}
                    companyId={companyId}
                    asset={dialog.asset}
                    onSaved={async () => {
                        await Promise.all([
                            queryClient.invalidateQueries({
                                queryKey: ["assets", companyId],
                            }),
                            queryClient.invalidateQueries({
                                queryKey: ["assets-summary", companyId],
                            }),
                        ])
                        showToast(dialog.asset ? "Bem atualizado." : "Bem cadastrado.")
                        setDialog(null)
                    }}
                    onClose={() => setDialog(null)}
                />
            ) : null}
        </>
    )
}

function normalizeMoney(value: string) {
    return value.trim().replace(",", ".")
}

function validMoney(value: string) {
    return /^\d+(?:[.,]\d{1,2})?$/.test(value.trim())
}

function AssetDialog({
    companyId,
    asset,
    onSaved,
    onClose,
}: {
    companyId: string
    asset?: Asset
    onSaved: () => Promise<void>
    onClose: () => void
}) {
    const queryClient = useQueryClient()
    const [assetTag, setAssetTag] = useState(asset?.assetTag ?? "")
    const [description, setDescription] = useState(asset?.description ?? "")
    const [category, setCategory] = useState<AssetCategory>(asset?.category ?? "OTHER")
    const [brandModel, setBrandModel] = useState(asset?.brandModel ?? "")
    const [serialNumber, setSerialNumber] = useState(asset?.serialNumber ?? "")
    const [acquisitionDate, setAcquisitionDate] = useState(asset?.acquisitionDate ?? "")
    const [acquisitionValue, setAcquisitionValue] = useState(asset?.acquisitionValue ?? "")
    const [quantity, setQuantity] = useState(String(asset?.quantity ?? 1))
    const [businessPartnerId, setBusinessPartnerId] =
        useState(asset?.businessPartnerId ?? "")
    const [partnerSearch, setPartnerSearch] = useState("")
    const [documentReference, setDocumentReference] =
        useState(asset?.documentReference ?? "")
    const [resultCenterId, setResultCenterId] =
        useState(asset?.resultCenterId ?? "")
    const [location, setLocation] = useState(asset?.location ?? "")
    const [responsible, setResponsible] = useState(asset?.responsible ?? "")
    const initialCategory = assetCategoryOptions.find(
        (option) => option.value === (asset?.category ?? "OTHER"),
    )!
    const [usefulLifeMonths, setUsefulLifeMonths] =
        useState(String(asset?.usefulLifeMonths ?? initialCategory.usefulLifeMonths))
    const [residualPercent, setResidualPercent] =
        useState(asset?.residualPercent ?? "0.00")
    const [depreciable, setDepreciable] =
        useState(asset?.depreciable ?? initialCategory.depreciable)
    const [depreciationStartDate, setDepreciationStartDate] =
        useState(asset?.depreciationStartDate ?? "")
    const [status, setStatus] =
        useState<Exclude<AssetStatus, "DISPOSED">>(
            asset?.status === "DISPOSED" ? "IN_USE" : (asset?.status ?? "IN_USE"),
        )
    const [warrantyUntil, setWarrantyUntil] =
        useState(asset?.warrantyUntil ?? "")
    const [notes, setNotes] = useState(asset?.notes ?? "")
    const [error, setError] = useState("")

    const partners = useQuery({
        queryKey: ["asset-partners", companyId, partnerSearch] as const,
        queryFn: ({ signal }) =>
            listBusinessPartnersPage(
                companyId,
                {
                    role: "",
                    active: "true",
                    search: partnerSearch,
                },
                0,
                signal,
            ),
        retry: false,
    })

    const centers = useQuery({
        queryKey: ["asset-centers", companyId] as const,
        queryFn: () => listAllResultCenters(companyId),
        retry: false,
    })

    const save = useMutation({
        mutationFn: async () => {
            setError("")

            const quantityNumber = Number(quantity)
            const lifeNumber = Number(usefulLifeMonths)
            const residual = Number(normalizeMoney(residualPercent))

            if (
                description.trim().length < 2 ||
                !acquisitionDate ||
                !validMoney(acquisitionValue) ||
                Number(normalizeMoney(acquisitionValue)) <= 0 ||
                !Number.isInteger(quantityNumber) ||
                quantityNumber < 1 ||
                !Number.isInteger(lifeNumber) ||
                lifeNumber < 0 ||
                depreciable && lifeNumber < 1 ||
                !validMoney(residualPercent) ||
                residual < 0 ||
                residual > 90 ||
                depreciationStartDate && depreciationStartDate < acquisitionDate
            ) {
                throw new Error("VALIDATION")
            }

            const values: AssetValues = {
                assetTag: assetTag.trim() || null,
                description: description.trim(),
                category,
                brandModel: brandModel.trim() || null,
                serialNumber: serialNumber.trim() || null,
                acquisitionDate,
                acquisitionValue: normalizeMoney(acquisitionValue),
                quantity: quantityNumber,
                businessPartnerId: businessPartnerId || null,
                documentReference: documentReference.trim() || null,
                resultCenterId: resultCenterId || null,
                location: location.trim() || null,
                responsible: responsible.trim() || null,
                usefulLifeMonths: lifeNumber,
                residualPercent: normalizeMoney(residualPercent),
                depreciable,
                depreciationStartDate: depreciationStartDate || null,
                status,
                warrantyUntil: warrantyUntil || null,
                notes: notes.trim() || null,
            }

            return asset
                ? updateAsset(companyId, asset, values)
                : createAsset(companyId, values)
        },
        onSuccess: () => onSaved(),
        onError: async (caught) => {
            if (caught instanceof Error && caught.message === "VALIDATION") {
                setError("Confira descrição, aquisição, valor, quantidade e parâmetros de depreciação.")
                return
            }

            if (caught instanceof ApiError && caught.code === "ASSET_DUPLICATE_TAG") {
                setError("Esta plaqueta já está cadastrada nesta empresa.")
                return
            }

            const statusCode =
                caught instanceof ApiError ? caught.status : undefined

            if (statusCode === 409) {
                await queryClient.invalidateQueries({
                    queryKey: ["assets", companyId],
                })
                setError("O bem foi alterado por outro usuário. Feche e abra novamente.")
            } else if (statusCode === 403) {
                setError("Seu acesso não permite alterar bens.")
            } else {
                setError("Não foi possível salvar o bem.")
            }
        },
    })

    function changeCategory(value: AssetCategory) {
        const option = assetCategoryOptions.find((item) => item.value === value)!
        setCategory(value)
        setUsefulLifeMonths(String(option.usefulLifeMonths))
        setDepreciable(option.depreciable)
    }

    const partnerItems = partners.data?.items ?? []
    const centerItems = centers.data?.filter((center) => center.active) ?? []

    return (
        <Modal
            onClose={save.isPending ? undefined : onClose}
            labelledBy="assetDialogTitle"
        >
            <h2 id="assetDialogTitle">
                {asset ? "Editar bem" : "Novo bem"}
            </h2>

            <div className="fg">
                <div className="fl">
                    <label htmlFor="assetTag">Plaqueta</label>
                    <input
                        id="assetTag"
                        maxLength={30}
                        value={assetTag}
                        onChange={(event) => setAssetTag(event.target.value)}
                        placeholder="PAT-0001"
                    />
                </div>

                <div className="fl">
                    <label htmlFor="assetCategoryInput">Tipo</label>
                    <select
                        id="assetCategoryInput"
                        value={category}
                        onChange={(event) =>
                            changeCategory(event.target.value as AssetCategory)
                        }
                    >
                        {assetCategoryOptions.map((option) => (
                            <option key={option.value} value={option.value}>
                                {option.label}
                            </option>
                        ))}
                    </select>
                </div>

                <div className="fl full">
                    <label htmlFor="assetDescription">Descrição</label>
                    <input
                        id="assetDescription"
                        maxLength={150}
                        value={description}
                        onChange={(event) => setDescription(event.target.value)}
                    />
                </div>

                <div className="fl">
                    <label htmlFor="assetBrand">Marca / modelo</label>
                    <input
                        id="assetBrand"
                        maxLength={80}
                        value={brandModel}
                        onChange={(event) => setBrandModel(event.target.value)}
                    />
                </div>

                <div className="fl">
                    <label htmlFor="assetSerial">Série / identificação</label>
                    <input
                        id="assetSerial"
                        maxLength={60}
                        value={serialNumber}
                        onChange={(event) => setSerialNumber(event.target.value)}
                    />
                </div>

                <div className="fl">
                    <label htmlFor="assetDate">Aquisição</label>
                    <input
                        id="assetDate"
                        type="date"
                        value={acquisitionDate}
                        onChange={(event) => setAcquisitionDate(event.target.value)}
                    />
                </div>

                <div className="fl">
                    <label htmlFor="assetValue">Valor total pago</label>
                    <input
                        id="assetValue"
                        inputMode="decimal"
                        value={acquisitionValue}
                        onChange={(event) => setAcquisitionValue(event.target.value)}
                        placeholder="0,00"
                    />
                </div>

                <div className="fl">
                    <label htmlFor="assetQuantity">Quantidade</label>
                    <input
                        id="assetQuantity"
                        type="number"
                        min="1"
                        step="1"
                        value={quantity}
                        onChange={(event) => setQuantity(event.target.value)}
                    />
                </div>

                <div className="fl">
                    <label htmlFor="assetDocument">NF / documento</label>
                    <input
                        id="assetDocument"
                        maxLength={60}
                        value={documentReference}
                        onChange={(event) => setDocumentReference(event.target.value)}
                    />
                </div>

                <div className="fl full">
                    <label htmlFor="assetPartnerSearch">Buscar fornecedor</label>
                    <input
                        id="assetPartnerSearch"
                        type="search"
                        value={partnerSearch}
                        onChange={(event) => setPartnerSearch(event.target.value)}
                        placeholder="Nome do parceiro…"
                    />
                </div>

                <div className="fl full">
                    <label htmlFor="assetPartner">Fornecedor (opcional)</label>
                    <select
                        id="assetPartner"
                        value={businessPartnerId}
                        onChange={(event) => setBusinessPartnerId(event.target.value)}
                    >
                        <option value="">Sem vínculo</option>
                        {asset?.businessPartnerId &&
                            !partnerItems.some((partner) => partner.id === asset.businessPartnerId) ? (
                            <option value={asset.businessPartnerId}>
                                {asset.businessPartnerName ?? "Fornecedor atual"}
                            </option>
                        ) : null}
                        {partnerItems.map((partner) => (
                            <option key={partner.id} value={partner.id}>
                                {partner.profile.name}
                            </option>
                        ))}
                    </select>
                </div>

                <div className="fl">
                    <label htmlFor="assetCenter">Centro de resultado</label>
                    <select
                        id="assetCenter"
                        value={resultCenterId}
                        onChange={(event) => setResultCenterId(event.target.value)}
                    >
                        <option value="">Sem vínculo</option>
                        {asset?.resultCenterId &&
                            !centerItems.some((center) => center.id === asset.resultCenterId) ? (
                            <option value={asset.resultCenterId}>
                                {asset.resultCenterCode
                                    ? asset.resultCenterCode + " — " + (asset.resultCenterName ?? "")
                                    : "Centro atual"}
                            </option>
                        ) : null}
                        {centerItems.map((center) => (
                            <option key={center.id} value={center.id}>
                                {center.code} — {center.name}
                            </option>
                        ))}
                    </select>
                </div>

                <div className="fl">
                    <label htmlFor="assetLocation">Local</label>
                    <input
                        id="assetLocation"
                        maxLength={80}
                        value={location}
                        onChange={(event) => setLocation(event.target.value)}
                    />
                </div>

                <div className="fl">
                    <label htmlFor="assetResponsible">Responsável</label>
                    <input
                        id="assetResponsible"
                        maxLength={120}
                        value={responsible}
                        onChange={(event) => setResponsible(event.target.value)}
                    />
                </div>

                <div className="fl">
                    <label htmlFor="assetStatusInput">Situação</label>
                    <select
                        id="assetStatusInput"
                        value={status}
                        onChange={(event) =>
                            setStatus(event.target.value as Exclude<AssetStatus, "DISPOSED">)
                        }
                    >
                        <option value="IN_USE">Em uso</option>
                        <option value="MAINTENANCE">Em manutenção</option>
                        <option value="IDLE">Parado</option>
                    </select>
                </div>

                <div className="fl">
                    <label htmlFor="assetLife">Vida útil (meses)</label>
                    <input
                        id="assetLife"
                        type="number"
                        min={depreciable ? 1 : 0}
                        step="1"
                        value={usefulLifeMonths}
                        onChange={(event) => setUsefulLifeMonths(event.target.value)}
                    />
                </div>

                <div className="fl">
                    <label htmlFor="assetResidual">Residual (%)</label>
                    <input
                        id="assetResidual"
                        inputMode="decimal"
                        value={residualPercent}
                        onChange={(event) => setResidualPercent(event.target.value)}
                    />
                </div>

                <div className="fl">
                    <label htmlFor="assetDepStart">Início da depreciação</label>
                    <input
                        id="assetDepStart"
                        type="date"
                        value={depreciationStartDate}
                        onChange={(event) => setDepreciationStartDate(event.target.value)}
                    />
                </div>

                <div className="fl">
                    <label className="chk">
                        <input
                            type="checkbox"
                            checked={depreciable}
                            onChange={(event) => setDepreciable(event.target.checked)}
                        />
                        Depreciar este bem
                    </label>
                </div>

                <div className="fl">
                    <label htmlFor="assetWarranty">Garantia até</label>
                    <input
                        id="assetWarranty"
                        type="date"
                        value={warrantyUntil}
                        onChange={(event) => setWarrantyUntil(event.target.value)}
                    />
                </div>

                <div className="fl full">
                    <label htmlFor="assetNotes">Observações</label>
                    <textarea
                        id="assetNotes"
                        maxLength={800}
                        value={notes}
                        onChange={(event) => setNotes(event.target.value)}
                    />
                </div>
            </div>

            {partners.isError ? (
                <div className="alert w">
                    Não foi possível consultar fornecedores; o bem pode ser salvo sem vínculo.
                </div>
            ) : null}

            {centers.isError ? (
                <div className="alert w">
                    Não foi possível consultar centros de resultado; o bem pode ser salvo sem vínculo.
                </div>
            ) : null}

            {error ? <div className="alert e">{error}</div> : null}

            <div className="mact">
                <button
                    type="button"
                    className="btn sec"
                    disabled={save.isPending}
                    onClick={onClose}
                >
                    Cancelar
                </button>
                <button
                    type="button"
                    className="btn"
                    disabled={save.isPending}
                    onClick={() => save.mutate()}
                >
                    {save.isPending ? "Salvando…" : "Salvar"}
                </button>
            </div>
        </Modal>
    )
}
