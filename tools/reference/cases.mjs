// Synthetic data only. Expected results are captured against the pinned source.
export const cases = [
  {
    id: 'contract-withholdings', function: 'calcNF', bindings: ['r2', 'calcNF'],
    args: [{nf: {aliq: 5, issRet: true, ret: {ir: 1.5, pis: 0.65, cofins: 3, csll: 1}}}, 1000],
    expected: {bruto: 1000, iss: 50, ret: {iss: 50, ir: 15, pis: 6.5, cofins: 30, csll: 10, inss: 0}, totRet: 111.5, liquido: 888.5}
  },
  {
    id: 'contract-iss-not-withheld', function: 'calcNF', bindings: ['r2', 'calcNF'],
    args: [{nf: {aliq: 5, issRet: false}}, 1000],
    expected: {bruto: 1000, iss: 50, ret: {iss: 0, ir: 0, pis: 0, cofins: 0, csll: 0, inss: 0}, totRet: 0, liquido: 1000}
  },
  {
    id: 'inventory-weighted-average', function: 'estProcessar', bindings: ['r3', 'estProcessar'],
    args: [[
      {prod: 'synthetic-product', data: '2026-01-01', q: 10, vu: 10},
      {prod: 'synthetic-product', data: '2026-01-02', q: 10, vu: 20},
      {prod: 'synthetic-product', data: '2026-01-03', q: -5, vu: 25}
    ]], select: ['synthetic-product', 'valor'], expected: 225
  },
  {
    id: 'inventory-same-day-input-first', function: 'estProcessar', bindings: ['r3', 'estProcessar'],
    args: [[
      {prod: 'synthetic-product', data: '2026-01-01', q: -5, vu: 20},
      {prod: 'synthetic-product', data: '2026-01-01', q: 10, vu: 10}
    ]], select: ['synthetic-product', 'min'], expected: 0
  },
  {
    id: 'inventory-negative-history', function: 'estProcessar', bindings: ['r3', 'estProcessar'],
    args: [[
      {prod: 'synthetic-product', data: '2026-01-01', q: -5, vu: 20},
      {prod: 'synthetic-product', data: '2026-01-02', q: 10, vu: 10}
    ]], select: ['synthetic-product', 'min'], expected: -5
  },
  {
    id: 'asset-disposal-month-depreciates', function: 'depBem', bindings: ['r2', 'depMensal', 'mesesEntreYM', 'depBem'],
    args: [{valor: 1200, vida: 12, depreciar: true, data: '2026-01-10', baixa: {data: '2026-03-15'}}, '2026-03'],
    expected: 100
  },
  {
    id: 'asset-after-disposal-stops', function: 'depBem', bindings: ['r2', 'depMensal', 'mesesEntreYM', 'depBem'],
    args: [{valor: 1200, vida: 12, depreciar: true, data: '2026-01-10', baixa: {data: '2026-03-15'}}, '2026-04'],
    expected: 0
  },
  {
    id: 'asset-accumulation-capped-at-disposal', function: 'depAcum', bindings: ['r2', 'depMensal', 'mesesEntreYM', 'depAcum'],
    args: [{valor: 1200, vida: 12, depreciar: true, data: '2026-01-10', baixa: {data: '2026-03-15'}}, '2026-12-31'],
    expected: 300
  },
  {
    id: 'debt-sac-residual', function: 'tabelaDiv', bindings: ['r2', 'pad', 'isoD', 'addMeses', 'taxaMes', 'taxaImplicita', 'custoMes', 'tabelaDiv'],
    args: [{sistema: 'SAC', valorOrig: 1000, n: 3, taxa: 3, taxaPer: 'm', venc1: '2026-01-31'}],
    expected: [
      {k: 1, amort: 333.33, juros: 30, valor: 363.33, saldo: 666.67, venc: '2026-01-31'},
      {k: 2, amort: 333.33, juros: 20, valor: 353.33, saldo: 333.34, venc: '2026-02-28'},
      {k: 3, amort: 333.34, juros: 10, valor: 343.34, saldo: 0, venc: '2026-03-31'}
    ]
  },
  {
    id: 'debt-no-positive-implied-rate', function: 'taxaImplicita', bindings: ['taxaImplicita'],
    args: [1000, 100, 10], expected: 0
  },
  {
    id: 'workforce-clt-provision', function: 'custoPessoa', bindings: ['r2', 'z12', 'custoPessoa'],
    args: [{tipo: 'CLT', sal: 3600, qtd: 1}, {cfg: {modo: 'provisao', inss: 20, fgts: 8}, benef: []}, 2026],
    select: ['fgts', 0], expected: 344
  },
  {
    id: 'workforce-apprentice-fgts', function: 'custoPessoa', bindings: ['r2', 'z12', 'custoPessoa'],
    args: [{tipo: 'APR', sal: 3600, qtd: 1}, {cfg: {modo: 'provisao', inss: 20, fgts: 8}, benef: []}, 2026],
    select: ['fgts', 0], expected: 86
  },
  {
    id: 'fixed-cost-periodic-adjustment', function: 'valorItemFixo', bindings: ['r2', 'z12', 'valorItemFixo'],
    args: [{valor: 100, ini: 0, fim: 11, per: 3, mesRef: 0, reajPct: 10, reajMes: 6}],
    expected: [100, 0, 0, 100, 0, 0, 110, 0, 0, 110, 0, 0]
  },
  {
    id: 'weighted-period', function: 'mediaPond', bindings: ['mediaPond'],
    args: [[{d: 10, p: 100}, {d: 20, p: 300}]], expected: 17.5
  },
  {
    id: 'no-weighted-base-is-null', function: 'mediaPond', bindings: ['mediaPond'],
    args: [[]], expected: null
  },
  {
    id: 'operational-dre-settlement-difference', function: 'dreEfeitos',
    bindings: ['r2', 'subMap', 'catInfo', 'dreDe', 'valorEf', 'dreEfeitos'],
    company: {grupos: [{id: 'synthetic-group', dre: 'REC', subs: [{id: 'synthetic-category', nome: 'Synthetic'}]}]},
    args: [{tipo: 'E', cat: 'synthetic-category', comp: '2026-01', valor: 100, pgto: '2026-02-01', vpago: 110}],
    expected: [{ym: '2026-01', linha: 'REC', cat: 'synthetic-category', v: 100}, {ym: '2026-02', linha: 'RFIN', cat: '_dif', v: 10}]
  },
  {
    id: 'legacy-binary-rounding-1005', function: 'r2', bindings: ['r2'],
    args: [1.005], expected: 1,
    note: 'Legacy binary rounding evidence; a decimal stabilization must be documented by the Java port.'
  },
  {
    id: 'legacy-negative-tie', function: 'r2', bindings: ['r2'],
    args: [-1.125], expected: -1.12,
    note: 'Math.round resolves a negative tie toward positive infinity, unlike HALF_UP.'
  }
];
