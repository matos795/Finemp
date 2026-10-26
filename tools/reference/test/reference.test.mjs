import assert from 'node:assert/strict';
import test from 'node:test';
import {readFileSync} from 'node:fs';
import {capture, parseReference, selectPath} from '../reference.mjs';
import {cases} from '../cases.mjs';

const syntheticSource = `<html><script>
throw new Error('Application startup must never run');
const TABS = [['first'], ['second']];
const MENU = [{id: 'group', nome: 'Synthetic', itens: [['first','First'],['second','Second']]}];
const r2 = value => Math.round(value * 100) / 100;
(function () { const ADM_ITENS = () => [['admUsers','Users'], ...(true ? [['admClients','Clients']] : [])]; })();
</script></html>`;

test('extracts every base and conditional admin screen without application startup', () => {
  const reference = parseReference(syntheticSource, {verifyHash: false});
  assert.deepEqual(reference.inventory.screens.map(screen => screen.id), ['first', 'second', 'admUsers', 'admClients']);
});

test('rejects an unreviewed source hash by default', () => {
  assert.throws(() => parseReference(syntheticSource), /hash mismatch/);
});

test('compiles only an explicitly reviewed calculation binding', () => {
  const reference = parseReference(syntheticSource, {verifyHash: false});
  assert.equal(capture(reference, {bindings: ['r2'], function: 'r2', args: [12.346]}), 12.35);
  assert.throws(() => capture(reference, {bindings: ['renderLanc'], function: 'renderLanc', args: []}), /Unreviewed/);
});

test('selects a nested result without executing arbitrary selectors', () => {
  assert.equal(selectPath({components: {tax: [12.5]}}, ['components', 'tax', 0]), 12.5);
});

test('synthetic parity cases have unique identifiers and expected results', () => {
  assert.equal(new Set(cases.map(testCase => testCase.id)).size, cases.length);
  for (const testCase of cases) {
    assert.ok(Object.hasOwn(testCase, 'expected'), testCase.id);
    assert.ok(testCase.bindings.includes(testCase.function), testCase.id);
  }
});

test('documents all 61 known navigation entries and keeps current accounting API distinct', () => {
  const matrix = readFileSync(new URL('../../../docs/reference/frontend-backend-parity.md', import.meta.url), 'utf8');
  const ids = [...matrix.matchAll(/^\| `([A-Za-z][A-Za-z0-9]*)` —/gm)].map(match => match[1]);
  assert.equal(ids.length, 61);
  assert.equal(new Set(ids).size, 61);
  const navigation = JSON.parse(readFileSync(new URL('../../../docs/reference/reference-navigation.json', import.meta.url), 'utf8'));
  assert.deepEqual([...ids].sort(), navigation.screens.map(screen => screen.id).sort());
  assert.ok(matrix.includes('AccountingMovement'));
  assert.ok(matrix.includes('FinancialEntry'));
});
