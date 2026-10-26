import {createHash} from 'node:crypto';
import vm from 'node:vm';
import {createRequire} from 'node:module';

const require = createRequire(import.meta.url);
const {parse} = require('acorn');

export const REFERENCE_SHA256 = '7f8342a08bcc77fbdc76f6160dd7fcec766f83f2c89f4d5b2dd919040bfc7d51';

function walk(node, visit) {
  if (!node || typeof node !== 'object') return;
  if (typeof node.type === 'string') visit(node);
  for (const [key, value] of Object.entries(node)) {
    if (key === 'loc') continue;
    if (Array.isArray(value)) value.forEach(child => walk(child, visit));
    else if (value && typeof value === 'object') walk(value, visit);
  }
}

function literal(node) {
  if (node?.type === 'Literal') return node.value;
  if (node?.type === 'ArrayExpression') return node.elements.map(literal);
  if (node?.type === 'ObjectExpression') {
    return Object.fromEntries(node.properties.map(property => [
      property.key.name ?? property.key.value,
      literal(property.value)
    ]));
  }
  throw new Error(`Nonliteral metadata is not evaluated: ${node?.type}`);
}

export function parseReference(input, {verifyHash = true} = {}) {
  const bytes = Buffer.isBuffer(input) ? input : Buffer.from(input, 'utf8');
  const hash = createHash('sha256').update(bytes).digest('hex');
  if (verifyHash && hash !== REFERENCE_SHA256) {
    throw new Error('Reference hash mismatch. Review the new source before changing the pinned hash.');
  }
  const html = bytes.toString('utf8');
  const scripts = [...html.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/gi)];
  const main = scripts.find(script => /\bconst\s+TABS\s*=/.test(script[1]));
  if (!main) throw new Error('Business script with TABS was not found.');
  const source = main[1];
  const offset = html.slice(0, main.index + main[0].indexOf('>') + 1).split('\n').length - 1;
  const ast = parse(source, {ecmaVersion: 'latest', locations: true});
  const bindings = new Map();
  for (const node of ast.body) {
    if (node.type === 'FunctionDeclaration' && node.id) bindings.set(node.id.name, node);
    if (node.type === 'VariableDeclaration') {
      for (const declaration of node.declarations) {
        if (declaration.id.type === 'Identifier') bindings.set(declaration.id.name, declaration);
      }
    }
  }
  const baseTabs = literal(bindings.get('TABS')?.init).map(([id]) => id);
  const menu = literal(bindings.get('MENU')?.init);
  const labels = new Map(menu.flatMap(group => group.itens.map(([id, label]) => [id, {label, group: group.id}])));
  const adminTabs = [];
  let admin;
  walk(ast, node => {
    if (node.type === 'VariableDeclarator' && node.id.name === 'ADM_ITENS') admin = node;
  });
  if (admin) walk(admin.init, node => {
    if (node.type === 'ArrayExpression' && node.elements[0]?.type === 'Literal'
        && typeof node.elements[0].value === 'string' && node.elements[0].value.startsWith('adm')) {
      adminTabs.push({id: node.elements[0].value, label: node.elements[1]?.value, group: 'admin'});
    }
  });
  const functions = [];
  walk(ast, node => {
    if (node.type === 'FunctionDeclaration' && node.id) functions.push({
      name: node.id.name,
      startLine: offset + node.loc.start.line,
      endLine: offset + node.loc.end.line
    });
  });
  return {
    hash, source, bindings,
    inventory: {
      hash,
      screens: [...baseTabs.map(id => ({id, ...labels.get(id)})), ...adminTabs],
      namedFunctionDeclarations: functions.length,
      functions
    }
  };
}

// Only these reviewed calculation bindings may be compiled. Application startup,
// demo records, storage, DOM handlers and external adapters are never evaluated.
const CALCULATION_BINDINGS = new Set([
  'r2', 'r3', 'pad', 'isoD', 'addDias', 'addMeses', 'mesesEntreYM',
  'valorEf', 'sinal', 'saldoConta', 'saldoTotal', 'agregarAno', 'totaisMes',
  'subMap', 'catInfo', 'dreDe', 'natDe', 'cvCat', 'dreEfeitos',
  'calcNF', 'taxaMes', 'taxaImplicita', 'custoMes', 'tabelaDiv',
  'estProcessar', 'depMensal', 'depBem', 'depAcum', 'valorContabil',
  'z12', 'custoPessoa', 'valorItemFixo', 'valorItemVar', 'mediaPond',
  'competencias', 'vencDe'
]);

export function capture(reference, testCase) {
  const names = [...new Set(testCase.bindings)];
  const fragments = names.map(name => {
    if (!CALCULATION_BINDINGS.has(name)) throw new Error(`Unreviewed calculation binding: ${name}`);
    const node = reference.bindings.get(name);
    if (!node) throw new Error(`Calculation binding was not found: ${name}`);
    return node.type === 'FunctionDeclaration'
      ? reference.source.slice(node.start, node.end)
      : `const ${reference.source.slice(node.start, node.end)};`;
  });
  if (!names.includes(testCase.function)) throw new Error('The invoked function must be selected explicitly.');
  const context = vm.createContext({
    E: structuredClone(testCase.company ?? {}),
    __args: structuredClone(testCase.args),
    __result: null
  }, {codeGeneration: {strings: false, wasm: false}});
  const script = new vm.Script(`${fragments.join('\n')}\n__result = ${testCase.function}(...__args);`);
  script.runInContext(context, {timeout: 1000});
  // JSON transfer removes VM prototypes; it does not normalize floating point.
  return JSON.parse(JSON.stringify(context.__result));
}

export function selectPath(value, segments) {
  return segments.reduce((current, segment) => current?.[segment], value);
}
