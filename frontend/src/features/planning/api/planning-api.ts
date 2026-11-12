import { apiRequest } from "../../../lib/api/api-client.ts"

export interface PagedResponse<T> {
    items: T[]
    page: number
    size: number
    totalItems: number
    totalPages: number
}

export interface PlanningMeeting {
    id: string
    title: string
    stage: string
    scheduledOn: string
    notes: string | null
    diagnosticText: string | null
    version: number
}

export interface GoalMeasurement {
    id: string
    measuredOn: string
    measuredValue: string
    notes: string | null
    createdAt: string
}

export interface Goal {
    id: string
    name: string
    direction: "UP" | "DOWN"
    baselineValue: string
    targetValue: string
    dueOn: string
    notes: string | null
    active: boolean
    version: number
    currentValue: string | null
    currentMeasuredOn: string | null
    progress: string | null
    achieved: boolean | null
}

export interface ActionPlanItem {
    id: string
    goalId: string | null
    what: string
    why: string | null
    responsible: string
    dueOn: string | null
    where: string | null
    how: string | null
    cost: string | null
    workflowStatus: string
    completedAt: string | null
    version: number
}

function json(method: string, body: unknown): RequestInit {
    return {
        method,
        body: JSON.stringify(body),
    }
}

export function listPlanningMeetings(
    companyId: string,
    stage = "",
    page = 0,
    size = 50,
) {
    const params = new URLSearchParams({
        stage: stage.trim(),
        page: String(page),
        size: String(size),
    })

    return apiRequest<PagedResponse<PlanningMeeting>>(
        `/api/companies/${companyId}/planning/meetings?${params.toString()}`,
    )
}

export function createPlanningMeeting(
    companyId: string,
    input: Omit<PlanningMeeting, "id" | "version">,
) {
    return apiRequest<PlanningMeeting>(
        `/api/companies/${companyId}/planning/meetings`,
        json("POST", input),
    )
}

export function updatePlanningMeeting(
    companyId: string,
    meetingId: string,
    input: Omit<PlanningMeeting, "id">,
) {
    return apiRequest<PlanningMeeting>(
        `/api/companies/${companyId}/planning/meetings/${meetingId}`,
        json("PUT", {
            title: input.title,
            stage: input.stage,
            scheduledOn: input.scheduledOn,
            notes: input.notes,
            diagnosticText: input.diagnosticText,
            expectedVersion: input.version,
        }),
    )
}

export function deletePlanningMeeting(
    companyId: string,
    meetingId: string,
    expectedVersion: number,
) {
    return apiRequest<void>(
        `/api/companies/${companyId}/planning/meetings/${meetingId}?expectedVersion=${expectedVersion}`,
        { method: "DELETE" },
    )
}

export function listGoals(
    companyId: string,
    active: boolean | null = null,
    page = 0,
    size = 50,
) {
    const params = new URLSearchParams({
        page: String(page),
        size: String(size),
    })

    if (active !== null) params.set("active", String(active))

    return apiRequest<PagedResponse<Goal>>(
        `/api/companies/${companyId}/goals?${params.toString()}`,
    )
}

export async function listAllActiveGoals(
    companyId: string,
) {
    const items: Goal[] = []
    let page = 0
    let totalPages = 1

    while (page < totalPages) {
        const response = await listGoals(
            companyId,
            true,
            page,
            100,
        )

        items.push(...response.items)
        totalPages = response.totalPages
        page += 1
    }

    return items
}

export function createGoal(
    companyId: string,
    input: {
        name: string
        direction: "UP" | "DOWN"
        baselineValue: string
        targetValue: string
        dueOn: string
        notes: string | null
    },
) {
    return apiRequest<Goal>(
        `/api/companies/${companyId}/goals`,
        json("POST", input),
    )
}

export function updateGoal(
    companyId: string,
    goal: Goal,
    input: {
        name: string
        direction: "UP" | "DOWN"
        baselineValue: string
        targetValue: string
        dueOn: string
        notes: string | null
    },
) {
    return apiRequest<Goal>(
        `/api/companies/${companyId}/goals/${goal.id}`,
        json("PUT", {
            ...input,
            expectedVersion: goal.version,
        }),
    )
}

export function deactivateGoal(
    companyId: string,
    goalId: string,
    expectedVersion: number,
) {
    return apiRequest<Goal>(
        `/api/companies/${companyId}/goals/${goalId}?expectedVersion=${expectedVersion}`,
        { method: "DELETE" },
    )
}

export function listGoalMeasurements(
    companyId: string,
    goalId: string,
    page = 0,
    size = 50,
) {
    return apiRequest<PagedResponse<GoalMeasurement>>(
        `/api/companies/${companyId}/goals/${goalId}/measurements?page=${page}&size=${size}`,
    )
}

export function measureGoal(
    companyId: string,
    goalId: string,
    input: {
        measuredOn: string
        measuredValue: string
        notes: string | null
    },
) {
    return apiRequest<GoalMeasurement>(
        `/api/companies/${companyId}/goals/${goalId}/measurements`,
        json("POST", input),
    )
}

export function listActionPlans(
    companyId: string,
    status = "",
    page = 0,
    size = 50,
) {
    const params = new URLSearchParams({
        status: status.trim(),
        page: String(page),
        size: String(size),
    })

    return apiRequest<PagedResponse<ActionPlanItem>>(
        `/api/companies/${companyId}/action-plans?${params.toString()}`,
    )
}

export function createActionPlan(
    companyId: string,
    input: {
        goalId: string | null
        what: string
        why: string | null
        responsible: string
        dueOn: string | null
        where: string | null
        how: string | null
        cost: string | null
        workflowStatus: string
    },
) {
    return apiRequest<ActionPlanItem>(
        `/api/companies/${companyId}/action-plans`,
        json("POST", input),
    )
}

export function updateActionPlan(
    companyId: string,
    item: ActionPlanItem,
    input: {
        goalId: string | null
        what: string
        why: string | null
        responsible: string
        dueOn: string | null
        where: string | null
        how: string | null
        cost: string | null
        workflowStatus: string
    },
) {
    return apiRequest<ActionPlanItem>(
        `/api/companies/${companyId}/action-plans/${item.id}`,
        json("PUT", {
            ...input,
            expectedVersion: item.version,
        }),
    )
}

export function setActionCompleted(
    companyId: string,
    item: ActionPlanItem,
    completed: boolean,
) {
    return apiRequest<ActionPlanItem>(
        `/api/companies/${companyId}/action-plans/${item.id}/completion`,
        json("POST", {
            completed,
            expectedVersion: item.version,
        }),
    )
}

export function deleteActionPlan(
    companyId: string,
    itemId: string,
    expectedVersion: number,
) {
    return apiRequest<void>(
        `/api/companies/${companyId}/action-plans/${itemId}?expectedVersion=${expectedVersion}`,
        { method: "DELETE" },
    )
}
