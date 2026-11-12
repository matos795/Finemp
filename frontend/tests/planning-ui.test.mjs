import { test } from "node:test"
import assert from "node:assert/strict"
import {
  canManagePlanning,
  canReadPlanning,
  describePlanningMutationError,
} from "../src/features/planning/lib/planning-ui.ts"
import { ApiError } from "../src/lib/api/api-client.ts"

test("planning view and manage permissions stay distinct", () => {
  assert.equal(canReadPlanning((permission) => permission === "PLANNING_VIEW"), true)
  assert.equal(canManagePlanning((permission) => permission === "PLANNING_VIEW"), false)

  assert.equal(canReadPlanning((permission) => permission === "PLANNING_MANAGE"), true)
  assert.equal(canManagePlanning((permission) => permission === "PLANNING_MANAGE"), true)
})

test("stale planning writes ask for refresh instead of overwrite", () => {
  assert.match(
    describePlanningMutationError(new ApiError(409, "conflict", "STALE_VERSION")),
    /atualizado por outra operação/i,
  )

  assert.match(
    describePlanningMutationError(new ApiError(403, "forbidden")),
    /permissão/i,
  )
})
