import { test, afterEach } from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"
import {
  createGoal,
  measureGoal,
  setActionCompleted,
  updateGoal,
  updatePlanningMeeting,
} from "../src/features/planning/api/planning-api.ts"

const originalFetch = globalThis.fetch

afterEach(() => {
  globalThis.fetch = originalFetch
})

function installFetch(calls) {
  globalThis.fetch = async (path, options = {}) => {
    calls.push({ path, options })

    if (path === "/api/auth/csrf") {
      return Response.json({
        headerName: "X-CSRF-TOKEN",
        token: "synthetic-token",
      })
    }

    return Response.json({
      id: "synthetic",
      version: 4,
    })
  }
}

test("planning updates carry the backend version instead of overwriting blindly", async () => {
  const calls = []
  installFetch(calls)

  await updatePlanningMeeting(
    "company-1",
    "meeting-1",
    {
      title: "Reunião",
      stage: "Diagnóstico",
      scheduledOn: "2026-12-01",
      notes: null,
      diagnosticText: null,
      version: 3,
    },
  )

  await updateGoal(
    "company-1",
    {
      id: "goal-1",
      name: "Meta",
      direction: "UP",
      baselineValue: "10",
      targetValue: "20",
      dueOn: "2027-01-01",
      notes: null,
      active: true,
      version: 7,
      currentValue: null,
      currentMeasuredOn: null,
      progress: null,
      achieved: null,
    },
    {
      name: "Meta",
      direction: "UP",
      baselineValue: "10",
      targetValue: "20",
      dueOn: "2027-01-01",
      notes: null,
    },
  )

  const mutations = calls.filter(({ path }) => path !== "/api/auth/csrf")
  assert.equal(JSON.parse(mutations[0].options.body).expectedVersion, 3)
  assert.equal(JSON.parse(mutations[1].options.body).expectedVersion, 7)
})

test("goal measurement is append-only and never replaces the goal", async () => {
  const calls = []
  installFetch(calls)

  await measureGoal(
    "company-1",
    "goal-1",
    {
      measuredOn: "2026-11-12",
      measuredValue: "15.5",
      notes: "medição sintética",
    },
  )

  const mutation = calls.find(
    ({ path }) => path !== "/api/auth/csrf",
  )

  assert.equal(
    mutation.path,
    "/api/companies/company-1/goals/goal-1/measurements",
  )
  assert.equal(mutation.options.method, "POST")
  assert.deepEqual(
    JSON.parse(mutation.options.body),
    {
      measuredOn: "2026-11-12",
      measuredValue: "15.5",
      notes: "medição sintética",
    },
  )
})

test("action completion carries optimistic version", async () => {
  const calls = []
  installFetch(calls)

  await setActionCompleted(
    "company-1",
    {
      id: "action-1",
      goalId: null,
      what: "Executar",
      why: null,
      responsible: "Pessoa sintética",
      dueOn: null,
      where: null,
      how: null,
      cost: null,
      workflowStatus: "PLANEJADA",
      completedAt: null,
      version: 5,
    },
    true,
  )

  const mutation = calls.find(
    ({ path }) => path !== "/api/auth/csrf",
  )

  assert.deepEqual(
    JSON.parse(mutation.options.body),
    {
      completed: true,
      expectedVersion: 5,
    },
  )
})

test("planning pages compose the homologated FINEMP visual language", () => {
  for (const path of [
    "../src/features/planning/pages/planning-meetings-page.tsx",
    "../src/features/planning/pages/planning-goals-page.tsx",
    "../src/features/planning/pages/planning-actions-page.tsx",
  ]) {
    const source = readFileSync(
      new URL(path, import.meta.url),
      "utf8",
    )

    assert.match(source, /<Panel/)
    assert.match(source, /className="(?:filters|cards|tblwrap|kb|modal)/)
    assert.doesNotMatch(source, /finemp-theme\.css/)
  }
})

test("frontend does not reimplement the goal progress business formula", () => {
  const page = readFileSync(
    new URL(
      "../src/features/planning/pages/planning-goals-page.tsx",
      import.meta.url,
    ),
    "utf8",
  )

  assert.doesNotMatch(page, /baselineValue\s*[-+*/]\s*targetValue/)
  assert.doesNotMatch(page, /targetValue\s*[-+*/]\s*baselineValue/)
})

test("goal create posts raw decimals and direction to the backend", async () => {
  const calls = []
  installFetch(calls)

  await createGoal("company-1", {
    name: "Meta sintética",
    direction: "DOWN",
    baselineValue: "100.0000",
    targetValue: "50.0000",
    dueOn: "2027-01-01",
    notes: null,
  })

  const mutation = calls.find(
    ({ path }) => path !== "/api/auth/csrf",
  )

  const body = JSON.parse(mutation.options.body)
  assert.equal(body.direction, "DOWN")
  assert.equal(body.baselineValue, "100.0000")
  assert.equal(body.targetValue, "50.0000")
})


test("all three planning routes are real and share the same permission boundary", () => {
  const router = readFileSync(
    new URL("../src/app/router/app-router.tsx", import.meta.url),
    "utf8",
  )
  const navigation = readFileSync(
    new URL("../src/components/layout/navigation.ts", import.meta.url),
    "utf8",
  )

  for (const [path, page, id] of [
    ["/planning/meetings", "PlanningMeetingsPage", "plnCron"],
    ["/planning/goals", "PlanningGoalsPage", "plnMetas"],
    ["/planning/actions", "PlanningActionsPage", "plnAcoes"],
  ]) {
    assert.match(router, new RegExp(`path="${path}"[\\s\\S]*?<${page} \\/>`))
    assert.match(
      navigation,
      new RegExp(`id: "${id}"[\\s\\S]*?path: "${path}"[\\s\\S]*?PLANNING_VIEW[\\s\\S]*?PLANNING_MANAGE`),
    )
  }
})

test("planning permissions are selectable without being silently assigned by role", () => {
  const admin = readFileSync(
    new URL(
      "../src/features/platform-admin/pages/platform-admin-page.tsx",
      import.meta.url,
    ),
    "utf8",
  )
  const onboarding = readFileSync(
    new URL(
      "../src/features/platform-admin/components/official-user-onboarding-section.tsx",
      import.meta.url,
    ),
    "utf8",
  )

  for (const source of [admin, onboarding]) {
    assert.match(source, /PLANNING_VIEW/)
    assert.match(source, /PLANNING_MANAGE/)
  }
})


test("goal history query keeps its page in state and query key", () => {
  const page = readFileSync(
    new URL(
      "../src/features/planning/pages/planning-goals-page.tsx",
      import.meta.url,
    ),
    "utf8",
  )

  assert.match(page, /const \[historyPage, setHistoryPage\] = useState\(0\)/)
  assert.match(
    page,
    /"planning-goal-measurements"[\s\S]*?historyTarget\?\.id[\s\S]*?historyPage/,
  )
  assert.match(
    page,
    /listGoalMeasurements\([\s\S]*?historyTarget!\.id,[\s\S]*?historyPage,[\s\S]*?50/,
  )
  assert.match(page, /setHistoryPage\(0\)[\s\S]*?setHistoryTarget\(goal\)/)
  assert.match(page, /historyQuery\.data\.totalPages/)
})
