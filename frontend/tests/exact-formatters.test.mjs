import { test } from 'node:test'
import { readFileSync } from 'node:fs'
import assert from 'node:assert/strict'
import {
  formatExactCurrency,
  formatExactDecimal,
  formatExactDecimalCell,
  formatIsoDate,
  isNegativeExactDecimal,
} from '../src/lib/formatters/decimal.ts'
import { parseOpeningBalance } from '../src/features/bank-accounts/lib/parse-opening-balance.ts'
import { formatCompetence } from '../src/features/reports/lib/calendar-labels.ts'
import { agingBands, isOverdueBand } from '../src/features/reports/lib/aging-bands.ts'

test('exact decimals are formatted without Number conversion or rounding', () => {
  assert.equal(formatExactDecimal('99999999999999999.98'), '99.999.999.999.999.999,98')
  assert.equal(formatExactDecimal('-1234.5'), '-1.234,50')
  assert.equal(formatExactDecimal('-0.00'), '0,00')
  assert.equal(formatExactDecimal('1.234'), '1.234')
  assert.equal(formatExactCurrency('-1234.56'), '-R$ 1.234,56')
  assert.equal(formatExactCurrency('0.10'), 'R$ 0,10')
})

test('table cells show zero as a dash, like the prototype', () => {
  for (const zero of ['0', '0.00', '-0.00', '.0']) assert.equal(formatExactDecimalCell(zero), '–')
  assert.equal(formatExactDecimalCell('0.01'), '0,01')
  assert.equal(isNegativeExactDecimal('-0.00'), false)
  assert.equal(isNegativeExactDecimal('-0.01'), true)
})

test('dates and competences are relabelled without time zone shifts', () => {
  assert.equal(formatIsoDate('2026-01-01'), '01/01/2026')
  assert.equal(formatCompetence('2026-07'), 'jul/26')
  assert.equal(formatCompetence('2026-13'), '2026-13')
})

test('opening balance keeps exact text and refuses fractions of a cent', () => {
  assert.deepEqual(parseOpeningBalance('R$ 1.234,56'), { ok: true, value: '1234.56' })
  assert.deepEqual(parseOpeningBalance('-10'), { ok: true, value: '-10' })
  assert.equal(parseOpeningBalance('1,234').ok, false)
  assert.equal(parseOpeningBalance('abc').ok, false)
})

test('aging bands follow the contract codes in prototype order', () => {
  assert.equal(agingBands.length, 8)
  assert.equal(new Set(agingBands.map(band => band.code)).size, 8)
  for (const band of agingBands) assert.equal(isOverdueBand(band.code), band.overdue)
})

test("money inputs accept Brazilian thousands separators without Number conversion", async () => {
  const { parseMoneyInput } = await import("../src/lib/formatters/money-input.ts")
  assert.equal(parseMoneyInput("1.500,00"), "1500.00")
  assert.equal(parseMoneyInput("R$ 12.000,5"), "12000.5")
  assert.equal(parseMoneyInput("1500"), "1500")
  assert.equal(parseMoneyInput("1500,75"), "1500.75")
  assert.equal(parseMoneyInput("1500.75"), "1500.75")
  assert.equal(parseMoneyInput("1.234"), "1234", "dot groups of three are thousands")
  assert.equal(parseMoneyInput("99999999999999999,98"), "99999999999999999.98")
  for (const invalid of ["", "abc", "1,234", "1.2.3", "-10", "10,", ",5"]) {
    assert.equal(parseMoneyInput(invalid), null, invalid)
  }
})

test("published money fields use the shared parser instead of a digits-only pattern", () => {
  for (const path of [
    "../src/features/contracts/pages/contracts-page.tsx",
    "../src/features/sales/pages/sales-pipeline-page.tsx",
    "../src/features/assets/pages/assets-page.tsx",
    "../src/features/debt/pages/debt-origin-page.tsx",
    "../src/features/billing/pages/service-invoices-page.tsx",
  ]) {
    const source = readFileSync(new URL(path, import.meta.url), "utf8")
    assert.match(source, /parseMoneyInput/, path)
    assert.doesNotMatch(source, /\/\^\\d\+\(\?:\[\.,\]\\d\{1,2\}\)\?\$\//, path)
  }
})
