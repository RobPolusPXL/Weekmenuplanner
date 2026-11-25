import { test } from 'node:test';
import assert from 'node:assert/strict';
import { stepTimers, formatTimer, ingredientsForStep, portionFactor, scaledAmount, parseAiRecipe, aiPrompt, groceryText } from '../js/logic/cooking.js';
import { aggregateGroceryList, dishSnapshot } from '../js/logic/planner.js';
import { parseIngredient } from '../js/logic/parser.js';

test('timers uit stappen', () => {
  assert.deepEqual(stepTimers('Bak 8 minuten en laat dan 10–12 min rusten').map(t => t.seconds), [480, 720]);
  assert.deepEqual(stepTimers('Stoof 1,5 uur op laag vuur').map(t => t.seconds), [5400]);
  assert.deepEqual(stepTimers('Gaar in 2 u').map(t => t.seconds), [7200]);
  assert.deepEqual(stepTimers('Roer 30 sec'), [{ label: '30 sec', seconds: 30, index: 5 }]);
  assert.deepEqual(stepTimers('Snijd 2 uien en 3 tomaten'), []);
  assert.equal(formatTimer(605), '10:05'); assert.equal(formatTimer(3725), '1:02:05'); assert.equal(formatTimer(9), '0:09');
});

test('ingrediënten per stap', () => {
  const ings = ['500 g gehakt', '2 uien', '400 g spaghetti', '1 blik tomatenblokjes'].map(parseIngredient);
  assert.deepEqual(ingredientsForStep('Bak het gehakt rul en voeg de ui toe', ings).map(i => i.name), ['gehakt', 'ui']);
  assert.deepEqual(ingredientsForStep('Kook de spaghetti', ings).map(i => i.name), ['spaghetti']);
});

test('porties schalen, ook in de boodschappenlijst', () => {
  assert.equal(portionFactor(4, 2), 2); assert.equal(portionFactor(null, 2), 1); assert.equal(portionFactor(3, null), 1.5);
  assert.equal(scaledAmount({ quantity: 500, unit: 'g' }, 3), '1,5 kg');
  const dish = { id: 'a', name: 'A', type: 'pasta', servings: 2, ingredients: ['500 g gehakt', '1 ui', 'zout'].map(parseIngredient) };
  const snap4 = dishSnapshot(dish, 4);
  assert.equal(snap4.portions, 4); assert.equal(snap4.servings, 2);
  assert.equal(dishSnapshot(dish, 2).portions, undefined);
  const list = aggregateGroceryList([{ kind: 'koken', dishSnapshot: snap4 }, { kind: 'koken', dishSnapshot: dishSnapshot(dish) }]);
  assert.deepEqual(list.map(i => [i.itemKey, i.displayAmount]), [['gehakt|g', '1,5 kg'], ['ui|stuk', '3 stuk'], ['zout|', '']]);
});

test('recept uit AI-antwoord', () => {
  const answer = 'Hier is je recept:\n```json\n{"name":"Balletjes in tomatensaus","type":"Vlees","servings":4,"prepMinutes":"45",' +
    '"ingredients":["500 g gehakt","- 2 uien"],"steps":["1. Draai balletjes","2. Bak ze 10 minuten"]}\n```';
  const r = parseAiRecipe(answer, ['pasta', 'vlees']);
  assert.equal(r.name, 'Balletjes in tomatensaus'); assert.equal(r.type, 'vlees'); assert.equal(r.servings, 4); assert.equal(r.prepMinutes, 45);
  assert.deepEqual(r.ingredients, ['500 g gehakt', '2 uien']);
  assert.deepEqual(r.steps, ['Draai balletjes', 'Bak ze 10 minuten']);
  assert.throws(() => parseAiRecipe('geen json hier'), /Geen JSON/);
  assert.throws(() => parseAiRecipe('{"name":"x","ingredients":[]}'), /geen ingrediënten/);
  assert.throws(() => parseAiRecipe('{kapot'), /Geen JSON|geen geldige/);
  assert.match(aiPrompt('https://voorbeeld.be/recept', ['pasta', 'vis']), /één van: pasta, vis[\s\S]*https:\/\/voorbeeld.be\/recept/);
});

test('boodschappenlijst als tekst', () => {
  const toBuy = [
    { name: 'gehakt', displayAmount: '500 g', checked: false },
    { name: 'ui', displayAmount: '2 stuk', checked: true },
    { name: 'spaghetti', displayAmount: '400 g', checked: false },
  ];
  const text = groceryText(toBuy, [{ name: 'keukenrol', checked: false, quantity: null, unit: null }, { name: 'koffie', checked: true }], '23/11 – 29/11');
  assert.equal(text, 'Boodschappen 23/11 – 29/11\n\nVlees, vis & vega\n☐ Gehakt – 500 g\n\nPasta, rijst & droge waren\n☐ Spaghetti – 400 g\n\nExtra\n☐ Keukenrol');
  assert.match(groceryText([], [], 'x'), /Niets meer te kopen/);
});

test('timerLabel: werkwoord + ingrediënt uit de zin met de tijd', async () => {
  const { timerLabel, stepTimers } = await import('../js/logic/cooking.js');
  const { parseIngredient } = await import('../js/logic/parser.js');
  const ings = ['200 gram Kipfiletreepjes met grillkruiden', '1 stuk(s) Knoflookteen', '180 gram Penne', '360 gram Broccoli', '1 el Olijfolie', '150 ml Room'].map(parseIngredient);
  const label = (text, n) => timerLabel(text, stepTimers(text)[0].index, ings, n);
  assert.equal(label('Bak de kipfiletstukjes in 1 - 2 minuten rondom bruin.', 3), 'Kipfiletstukjes bakken');
  assert.equal(label('Verhit 1/4 el olijfolie in dezelfde pan en fruit de knoflook 1 - 2 minuten.', 4), 'Knoflook fruiten');
  assert.equal(label('Voeg de room en de penne toe. Kook, afgedekt, 15 - 17 minuten, of totdat de penne gaar is.', 5), 'Penne koken');
  assert.equal(label('Voeg in de laatste 8 - 10 minuten de broccoli toe en roer door.', 6), 'Broccoli toevoegen');
  assert.equal(label('Laat 10 minuten rusten.', 7), 'Stap 7 · laten rusten');
  assert.equal(label('Wacht 10 minuten.', 8), 'Stap 8');
});
