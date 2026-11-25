// Unit tests voor de logica (zelfde gevallen als de Kotlin-tests). Draaien met: node --test web/tests
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { parseIngredient } from '../js/logic/parser.js';
import { departmentFor, groupByDepartment } from '../js/logic/departments.js';
import { weekStart, weekDates, weekHeader, SUNDAY, MONDAY } from '../js/logic/dates.js';
import { reconcile, hasChanges, splitGroceryList, parsePantryInput, isLow, daysByWeek } from '../js/logic/pantry.js';
import { computeStats } from '../js/logic/stats.js';
import { ratingWeight, pickDishForDay, generateWholeWeek, aggregateGroceryList } from '../js/logic/planner.js';
import { formatPrepTime, formatQuantity, parseAmount } from '../js/logic/units.js';
import { parseRecipeHtml, parseIsoDurationMinutes, normalizeUrl } from '../js/logic/recipe.js';

const near = (a, b) => assert.ok(Math.abs(a - b) < 0.001, `${a} ≠ ${b}`);

test('parser: 150 gram gehakt', () => {
  const i = parseIngredient('150 gram gehakt');
  assert.equal(i.name, 'gehakt'); near(i.quantity, 150); assert.equal(i.unit, 'g'); assert.equal(i.needsNormalization, false);
  const j = parseIngredient('150g gehakt');
  assert.equal(j.name, 'gehakt'); near(j.quantity, 150); assert.equal(j.unit, 'g');
});

test('parser: voorbeeldlijst', () => {
  const cases = [
    ['2 uien', 'ui', 2, 'stuk'], ['500 g gehakt', 'gehakt', 500, 'g'], ['1,5 kg patatten', 'aardappel', 1500, 'g'],
    ['1/2 bloemkool', 'bloemkool', 0.5, 'stuk'], ['2 el olijfolie', 'olijfolie', 2, 'el'],
    ['3 teentjes look', 'knoflook', 3, 'teen'], ['1 blik tomatenblokjes', 'tomatenblokjes', 1, 'blik'], ['250 ml room', 'room', 250, 'ml'],
  ];
  for (const [raw, name, q, u] of cases) {
    const i = parseIngredient(raw);
    assert.equal(i.name, name, raw); near(i.quantity, q); assert.equal(i.unit, u, raw);
  }
});

test('parser: zonder hoeveelheid, meervoud, omrekenen, enkel getal', () => {
  const s = parseIngredient('snuf zout'); assert.equal(s.name, 'zout'); assert.equal(s.quantity, null); assert.equal(s.unit, null);
  assert.equal(parseIngredient('peper').name, 'peper');
  assert.equal(parseIngredient('2 uien').name, 'ui'); assert.equal(parseIngredient('1 ui').name, 'ui');
  assert.equal(parseIngredient('2 rode uien').name, 'rode ui');
  const k = parseIngredient('1 kilo aardappelen'); assert.equal(k.name, 'aardappel'); near(k.quantity, 1000); assert.equal(k.unit, 'g');
  const l = parseIngredient('0,5 l melk'); assert.equal(l.name, 'melk'); near(l.quantity, 500); assert.equal(l.unit, 'ml');
  const g = parseIngredient('150 gram'); assert.equal(g.name, '150 gram'); assert.equal(g.quantity, null);
});

test('afdelingen', () => {
  assert.equal(departmentFor('ui'), 'GROENTEN_FRUIT');
  assert.equal(departmentFor('Rode paprika'), 'GROENTEN_FRUIT');
  assert.equal(departmentFor('gehakt'), 'VLEES_VIS');
  assert.equal(departmentFor('kipfilet'), 'VLEES_VIS');
  assert.equal(departmentFor('eieren'), 'ZUIVEL');
  assert.equal(departmentFor('geraspte kaas'), 'ZUIVEL');
  for (const n of ['kippenbouillon', 'kokosmelk', 'pindakaas', 'tomatenpuree', 'paprikapoeder']) assert.equal(departmentFor(n), 'KRUIDEN_SAUZEN', n);
  assert.equal(departmentFor('zoute boter'), 'ZUIVEL');
  assert.equal(departmentFor('watermeloen'), 'GROENTEN_FRUIT');
  assert.equal(departmentFor('zoete aardappel'), 'GROENTEN_FRUIT');
  assert.equal(departmentFor('spaghetti'), 'DROGE_WAREN');
  assert.equal(departmentFor('keukenrol'), 'OVERIG');
  assert.equal(departmentFor(''), 'OVERIG');
  assert.deepEqual(groupByDepartment(['rijst', 'gehakt', 'ui', 'kaas'], x => x).map(g => g[0]),
    ['GROENTEN_FRUIT', 'VLEES_VIS', 'ZUIVEL', 'DROGE_WAREN']);
});

test('weekstart', () => {
  const sat = '2026-10-31';
  assert.equal(weekStart(sat, SUNDAY), '2026-10-25');
  assert.equal(weekStart(sat, MONDAY), '2026-10-26');
  assert.equal(weekStart(sat, 7), sat);
  assert.equal(weekStart(sat, 6), '2026-10-30');
  const dates = weekDates('2026-10-26');
  assert.equal(dates.length, 7); assert.equal(dates[6], '2026-11-01');
  assert.equal(weekHeader('2026-10-26'), '26/10/2026 – 01/11/2026');
  const days = { '2026-10-25': { dateId: '2026-10-25' }, '2026-10-26': { dateId: '2026-10-26' } };
  assert.deepEqual(Object.keys(daysByWeek(days, MONDAY)).sort(), ['2026-10-19', '2026-10-26']);
  assert.deepEqual(Object.keys(daysByWeek(days, SUNDAY)), ['2026-10-25']);
});

const w1 = '2026-10-25', w2 = '2026-11-01';
test('voorraad: vroegste week eerst, idempotent, teruggeven, vervallen', () => {
  const r = reconcile({ 'rijst|g': 500 }, {}, { [w1]: { 'rijst|g': 300 }, [w2]: { 'rijst|g': 300 } }, [w2, w1]);
  near(r.reservations[w1]['rijst|g'], 300); near(r.reservations[w2]['rijst|g'], 200); near(r.freeStock['rijst|g'], 0);

  const first = reconcile({ 'rijst|g': 500 }, {}, { [w1]: { 'rijst|g': 300 } }, [w1]);
  const second = reconcile(first.freeStock, first.reservations, { [w1]: { 'rijst|g': 300 } }, [w1]);
  assert.equal(hasChanges(first.freeStock, first.reservations, [w1], second), false);

  const back = reconcile({ 'rijst|g': 200 }, { [w1]: { 'rijst|g': 300 } }, {}, [w1]);
  near(back.freeStock['rijst|g'], 500); assert.deepEqual(back.reservations[w1], {});

  const cur = { [w1]: { 'pasta|g': 250 } };
  const gone = reconcile({}, cur, { [w1]: { 'pasta|g': 250 } }, [w1]);
  assert.deepEqual(gone.reservations[w1], {});
  assert.equal(hasChanges({}, cur, [w1], gone), true);
});

const agg = (name, q, u) => ({ itemKey: `${name}|${u ?? ''}`, name, totalQuantity: q, canonicalUnit: u, displayAmount: '', checked: false, dishNames: [], fromStockText: '' });
test('voorraad: lijst splitsen, invoer, bijna op', () => {
  const items = [agg('rijst', 300, 'g'), agg('gehakt', 500, 'g'), agg('ui', 3, 'stuk'), agg('zout', null, null)];
  const pantry = [{ id: '1', name: 'rijst', unit: 'g', quantity: 0 }, { id: '2', name: 'ui', unit: 'stuk', quantity: 0 }, { id: '3', name: 'zout', unit: null, quantity: null }];
  const s = splitGroceryList(items, { 'rijst|g': 300, 'ui|stuk': 1 }, pantry);
  assert.deepEqual(s.toBuy.map(i => i.name), ['gehakt', 'ui']);
  near(s.toBuy.find(i => i.name === 'ui').totalQuantity, 2);
  assert.deepEqual(s.fromStock.map(i => i.name), ['rijst', 'zout']);

  const p = parsePantryInput('1 kg rijst'); assert.equal(p.name, 'rijst'); assert.equal(p.unit, 'g'); near(p.quantity, 1000);
  assert.equal(parsePantryInput('zout').quantity, null);
  near(parseAmount('1,5'), 1.5);

  assert.equal(isLow({ quantity: 150, minQuantity: 200 }), true);
  assert.equal(isLow({ quantity: 500, minQuantity: 200 }), false);
  assert.equal(isLow({ quantity: 0 }), true);
  assert.equal(isLow({ quantity: null }), false);
});

test('statistieken', () => {
  const cook = (dateId, dishId, name, type) => ({ dateId, kind: 'koken', dishId, dishSnapshot: { name, type, ingredients: [] } });
  const mark = (dateId, kind) => ({ dateId, kind });
  const days = [
    cook('2026-10-01', 'a', 'Lasagne', 'pasta'), cook('2026-10-03', 'a', 'Lasagne', 'pasta'), cook('2026-10-04', 'b', 'Stoofvlees', 'vlees'),
    mark('2026-10-02', 'afhaal'), mark('2026-10-05', 'opwarm'), mark('2026-10-06', 'niet_koken'), mark('2026-10-07', 'leeg'),
    cook('2026-09-30', 'c', 'Soep', 'soep'), cook('2025-12-24', 'a', 'Lasagne', 'pasta'), cook('2026-10-20', 'b', 'Stoofvlees', 'vlees'),
  ];
  const s = computeStats(days, '2026-10', '2026-10-09');
  assert.equal(s.cooked, 3); assert.equal(s.takeaway, 1); assert.equal(s.reheated, 1); assert.equal(s.noCook, 1); assert.equal(s.total, 6);
  assert.deepEqual(s.topDishes.map(t => [t.label, t.count]), [['Lasagne', 2], ['Stoofvlees', 1]]);
  assert.deepEqual(s.types.map(t => [t.label, t.count]), [['pasta', 2], ['vlees', 1]]);
  assert.equal(computeStats(days, '2026', '2026-10-09').cooked, 4);
  assert.equal(computeStats(days, '2024', '2026-10-09').total, 0);
});

test('planner: gewicht, kiezen, week genereren, samentellen', () => {
  assert.deepEqual([0, 1, 2, 3, 4, 5].map(r => ratingWeight({ rating: r })), [3, 1, 2, 3, 5, 8]);
  const dishes = [{ id: 'a', name: 'Lasagne', type: 'pasta', rating: 0, ingredients: [] }, { id: 'b', name: 'Curry', type: 'rijst', rating: 0, ingredients: [] }];
  const week = { '2026-11-02': { dateId: '2026-11-02', kind: 'koken', dishId: 'a', dishSnapshot: { name: 'Lasagne', type: 'pasta', ingredients: [] } } };
  for (let i = 0; i < 20; i++) {
    const r = pickDishForDay('2026-11-03', week, dishes);
    assert.equal(r.dish.id, 'b'); assert.equal(r.ruleIgnored, false);
  }
  assert.equal(pickDishForDay('x', {}, []), null);
  const gen = generateWholeWeek(['2026-11-02', '2026-11-03', '2026-11-04'], week, dishes);
  assert.deepEqual(Object.keys(gen.assignments).sort(), ['2026-11-03', '2026-11-04']);
  assert.equal(gen.anyRuleIgnored, true);

  const days = [
    { kind: 'koken', dishSnapshot: { name: 'A', ingredients: [{ name: 'ui', quantity: 2, unit: 'stuk' }, { name: 'gehakt', quantity: 0.5, unit: 'kg' }] } },
    { kind: 'koken', dishSnapshot: { name: 'B', ingredients: [{ name: 'ui', quantity: 1, unit: 'stuk' }, { name: 'gehakt', quantity: 700, unit: 'g' }, { raw: 'Peper', name: '' }] } },
    { kind: 'afhaal' },
  ];
  const list = aggregateGroceryList(days, { 'ui|stuk': true });
  assert.deepEqual(list.map(i => [i.itemKey, i.displayAmount, i.checked]),
    [['gehakt|g', '1,2 kg', false], ['peper|', '', false], ['ui|stuk', '3 stuk', true]]);
  assert.deepEqual(list[0].dishNames, ['A', 'B']);
});

test('tijd en hoeveelheden tonen', () => {
  assert.equal(formatPrepTime(25), '25 min'); assert.equal(formatPrepTime(60), '1 u');
  assert.equal(formatPrepTime(75), '1 u 15 min'); assert.equal(formatPrepTime(120), '2 u');
  assert.equal(formatQuantity(1500, 'g'), '1,5 kg'); assert.equal(formatQuantity(250, 'ml'), '250 ml'); assert.equal(formatQuantity(2, null), '2');
});

const page = json => `<html><head><script type="application/ld+json">${json}</script></head><body></body></html>`;
test('recept uit JSON-LD', () => {
  const r = parseRecipeHtml(page('{"@context":"https://schema.org","@type":"Recipe","name":"Spaghetti bolognese","totalTime":"PT1H15M","recipeIngredient":["400 g gehakt","1 ui","2 el olijfolie"]}'));
  assert.equal(r.name, 'Spaghetti bolognese'); assert.equal(r.totalMinutes, 75);
  assert.deepEqual(r.ingredientLines, ['400 g gehakt', '1 ui', '2 el olijfolie']);
  const g = parseRecipeHtml(page('{"@graph":[{"@type":"WebSite","name":"Site"},{"@type":["Recipe","Thing"],"name":"Stoofvlees","prepTime":"PT20M","cookTime":"PT2H","recipeIngredient":["1 kg rundsvlees"]}]}'));
  assert.equal(g.name, 'Stoofvlees'); assert.equal(g.totalMinutes, 140);
  const e = parseRecipeHtml(page('[{"@type":"Recipe","name":"Mac &amp; cheese","recipeIngredient":["250 g macaroni","1&#8201;dl melk","1 ui"]}]'));
  assert.equal(e.name, 'Mac & cheese'); assert.equal(e.ingredientLines[0], '250 g macaroni');
  assert.equal(parseRecipeHtml(page('{"@type":"Article","name":"Nieuws"}')), null);
  assert.equal(parseRecipeHtml('<html><body>geen json-ld</body></html>'), null);
  assert.equal(parseRecipeHtml(page('{kapotte json')), null);
  assert.equal(parseIsoDurationMinutes('PT45M'), 45); assert.equal(parseIsoDurationMinutes('PT1H'), 60);
  assert.equal(parseIsoDurationMinutes('P0DT0H30M'), 30); assert.equal(parseIsoDurationMinutes('PT0M'), null); assert.equal(parseIsoDurationMinutes('onzin'), null);
  assert.equal(normalizeUrl('www.libelle-lekker.be/recept'), 'https://www.libelle-lekker.be/recept');
  assert.equal(normalizeUrl(' http://voorbeeld.be/r '), 'https://voorbeeld.be/r');
  assert.equal(normalizeUrl('geen url'), null); assert.equal(normalizeUrl(''), null);
});

test('parseRecipeHtml leest stappen (HowToStep met HTML-lijst) en porties', () => {
  const html = `<script type="application/ld+json">${JSON.stringify({
    '@type': 'Recipe', name: 'Pasta', recipeYield: 2, totalTime: 'PT25M', recipeIngredient: ['200 gram Kip'],
    recipeInstructions: [
      { '@type': 'HowToStep', text: '<ul><li>Stap een.</li><li>Stap <span>twee</span>. </li></ul>' },
      { '@type': 'HowToSection', itemListElement: [{ '@type': 'HowToStep', text: 'Stap drie.' }] },
      'Stap vier.\nStap vijf.',
    ],
  })}</script>`;
  const r = parseRecipeHtml(html);
  assert.deepEqual(r.steps, ['Stap een.', 'Stap twee.', 'Stap drie.', 'Stap vier.', 'Stap vijf.']);
  assert.equal(r.servings, 2);
  assert.equal(parseRecipeHtml(html.replace('"recipeYield":2', '"recipeYield":["4 personen"]')).servings, 4);
});

test('gekoppelde eenheden: noden en boodschappenlijst rekenen om naar de hoofdeenheid', async () => {
  const { pantryNeeds, pantryLinks } = await import('../js/logic/pantry.js');
  const pantry = [
    { id: '1', name: 'gepelde tomaat', unit: 'stuk', quantity: 8, altUnits: { blik: 1 } },
    { id: '2', name: 'rijst', unit: 'pak', quantity: 2, altUnits: { g: 500 } },
    { id: '3', name: 'ui', unit: 'stuk', quantity: 3, altUnits: {} },
  ];
  assert.deepEqual(pantryNeeds({ 'gepelde tomaat|blik': 1, 'rijst|g': 750, 'rijst|pak': 1, 'ui|g': 200, 'kaas|g': 50 }, pantry),
    { 'gepelde tomaat|stuk': 1, 'rijst|pak': 2.5 });
  assert.equal(pantryLinks(pantry)['ui|g'], undefined);
  const items = [
    { itemKey: 'gepelde tomaat|blik', name: 'gepelde tomaat', totalQuantity: 1, canonicalUnit: 'blik' },
    { itemKey: 'rijst|g', name: 'rijst', totalQuantity: 750, canonicalUnit: 'g' },
    { itemKey: 'rijst|pak', name: 'rijst', totalQuantity: 1, canonicalUnit: 'pak' },
  ];
  // Gereserveerd: 1 stuk tomaat; 2 pak rijst (1 pak voor de pak-regel, 1 pak = 500 g voor de g-regel).
  const s = splitGroceryList(items, { 'gepelde tomaat|stuk': 1, 'rijst|pak': 2 }, pantry);
  assert.deepEqual(s.fromStock.map(i => i.itemKey).sort(), ['gepelde tomaat|blik', 'rijst|pak']);
  assert.deepEqual(s.toBuy.map(i => [i.itemKey, i.totalQuantity]), [['rijst|g', 250]]);
});

test('reconcileDaily: voorbije dagen zijn opgegeten, enkel vandaag en later gepland', async () => {
  const { reconcileDaily, plannedByKey, weekReservedByKey } = await import('../js/logic/pantry.js');
  const weekOf = () => '2026-11-22';
  const K = 'tomatenpuree|stuk';
  const needs = { '2026-11-22': { [K]: 1 }, '2026-11-23': { [K]: 1 }, '2026-11-25': { [K]: 1 } };
  // Start: vrij 3, niets gereserveerd, vandaag woensdag 25/11. Enkel woensdag (en later) wordt gereserveerd.
  const r = reconcileDaily({ [K]: 3 }, {}, needs, '2026-11-25', weekOf);
  assert.deepEqual(r.reservations['2026-11-22'], { [`2026-11-25|${K}`]: 1 });
  assert.equal(r.freeStock[K], 2);
  assert.deepEqual(plannedByKey(r.reservations, '2026-11-25', '2026-11-22'), { [K]: 1 });
  assert.deepEqual(weekReservedByKey(r.reservations['2026-11-22']), { [K]: 1 });
  // Een oudere versie schrijft er een weeksleutel overheen (zoals op 25/11 gebeurde): die gaat volledig terug.
  const old = reconcileDaily({ [K]: 0 }, { '2026-11-22': { [K]: 2 } }, needs, '2026-11-25', weekOf);
  assert.equal(old.freeStock[K], 1);
  assert.deepEqual(old.reservations['2026-11-22'], { [`2026-11-25|${K}`]: 1 });
  // Een dag later: woensdag is opgegeten, niets meer gepland, vrij blijft 2.
  const r2 = reconcileDaily(r.freeStock, r.reservations, needs, '2026-11-26', weekOf);
  assert.equal(r2.freeStock[K], 2);
  assert.deepEqual(plannedByKey(r2.reservations, '2026-11-26', '2026-11-22'), {});
  // Stabiel: nog eens draaien verandert niets.
  const r3 = reconcileDaily(r2.freeStock, r2.reservations, needs, '2026-11-26', weekOf);
  assert.deepEqual(r3, r2);
  // Toekomstig gerecht schrappen: de reservering komt terug.
  const r4 = reconcileDaily(r.freeStock, r.reservations, { '2026-11-22': { [K]: 1 }, '2026-11-23': { [K]: 1 } }, '2026-11-25', weekOf);
  assert.equal(r4.freeStock[K], 3);
  // Voorbije dag die al gereserveerd stond, blijft opgegeten.
  const r5 = reconcileDaily({ [K]: 2 }, { '2026-11-22': { [`2026-11-23|${K}`]: 1 } }, needs, '2026-11-25', weekOf);
  assert.equal(r5.freeStock[K], 1);
  assert.equal(r5.reservations['2026-11-22'][`2026-11-23|${K}`], 1);
});
