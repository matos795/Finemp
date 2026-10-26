import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {parseReference, capture, selectPath} from './reference.mjs';
import {cases} from './cases.mjs';

const [command, sourcePath, matrixPath] = process.argv.slice(2);
if (!sourcePath || !['inventory', 'oracle'].includes(command)) {
  throw new Error('Usage: node cli.mjs inventory|oracle path/to/web/index.html [path/to/parity.md]');
}
const reference = parseReference(readFileSync(sourcePath));
if (matrixPath) {
  const matrix = readFileSync(matrixPath, 'utf8');
  for (const screen of reference.inventory.screens) {
    assert.ok(matrix.includes(`\`${screen.id}\``), `Screen missing from parity matrix: ${screen.id}`);
  }
}
if (command === 'inventory') {
  console.log(JSON.stringify(reference.inventory, null, 2));
} else {
  const results = cases.map(testCase => {
    const fullResult = capture(reference, testCase);
    const actual = testCase.select ? selectPath(fullResult, testCase.select) : fullResult;
    assert.deepEqual(actual, testCase.expected, testCase.id);
    return {id: testCase.id, actual, note: testCase.note};
  });
  console.log(JSON.stringify({referenceHash: reference.hash, passed: results.length, results}, null, 2));
}
