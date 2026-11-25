// Voorbeeldgegevens voor de demo-modus (?demo). Komen nooit in Firestore terecht.
import { parseIngredient } from '../logic/parser.js';
import { DEFAULT_DISH_TYPES, dishSnapshot } from '../logic/planner.js';
import { today, weekStart, addDays } from '../logic/dates.js';

const UID = 'demo-rob';
const HID = 'DEMO42';

const STEPS = {
  d1: [
    'Snipper de ui en pers de look. Breng een grote pot water met zout aan de kook.',
    'Bak het gehakt rul in een hete pan, 5 minuten. Voeg ui en look toe en bak 2 minuten mee.',
    'Voeg de tomatenblokjes toe, kruid met peper en zout en laat 15 minuten zachtjes pruttelen.',
    'Kook ondertussen de spaghetti 9 minuten beetgaar en giet af.',
    'Meng de spaghetti met de saus en serveer.',
  ],
  d3: [
    'Snijd de kipfilet in blokjes, de ui in ringen en de paprika in reepjes.',
    'Kook de rijst volgens de verpakking, ongeveer 12 minuten.',
    'Bak de kip 5 minuten goudbruin. Voeg ui, paprika en currypasta toe en bak 3 minuten.',
    'Giet de kokosmelk erbij en laat 10 minuten zacht koken. Serveer met de rijst.',
  ],
};

const DISHES = [
  ['d1', 'Spaghetti bolognese', 'pasta', 4, 45, ['500 g gehakt', '1 ui', '2 teentjes look', '1 blik tomatenblokjes', '400 g spaghetti', 'snuf zout']],
  ['d2', 'Stoofvlees met frietjes', 'vlees', 5, 180, ['1 kg rundsvlees', '2 uien', '1 fles bruin bier', '2 sneetjes peperkoek', '1 el mosterd', '1 kg aardappelen']],
  ['d3', 'Kip curry met rijst', 'rijst', 4, 35, ['400 g kipfilet', '1 ui', '400 ml kokosmelk', '2 el currypasta', '300 g rijst', '1 paprika']],
  ['d4', 'Zalm met spinazie en puree', 'vis', 3, 40, ['2 zalmfilets', '300 g spinazie', '800 g aardappelen', '100 ml melk', '20 g boter']],
  ['d5', 'Groentesoep', 'soep', 0, 50, ['2 wortelen', '1 prei', '1 ui', '1 blokje bouillon', '2 aardappelen']],
  ['d6', 'Wok met noedels', 'wok', 3, 25, ['250 g mie', '400 g wokgroenten', '2 el sojasaus', '200 g kipfilet']],
  ['d7', 'Witloof in de oven', 'ovenschotel', 5, 60, ['4 stronken witloof', '8 plakjes ham', '50 g geraspte kaas', '500 ml melk', '800 g aardappelen']],
  ['d8', 'Croque monsieur', 'brood/snel', 2, 15, ['8 sneetjes brood', '4 plakjes ham', '4 plakjes kaas', '20 g boter']],
  ['d9', 'Vegetarische lasagne', 'vegetarisch', 0, 70, ['250 g lasagnebladen', '1 courgette', '1 aubergine', '500 ml passata', '125 g mozzarella']],
];

export function seedDemo(write) {
  const now = new Date();
  const ws = weekStart(today(), 2);
  write(`households/${HID}`, {
    members: [UID, 'demo-joke'], inviteCode: HID, types: [...DEFAULT_DISH_TYPES], weekStartDay: 2,
    createdBy: UID, createdAt: now, updatedAt: now,
  });

  const dishes = {};
  for (const [id, name, type, rating, prepMinutes, lines] of DISHES) {
    const dish = {
      name, type, rating, prepMinutes,
      ingredients: lines.map(parseIngredient),
      servings: 2,
      ...(STEPS[id] ? { steps: STEPS[id] } : {}),
      needsNormalization: false, pendingPhotoUpload: false,
      createdBy: UID, createdAt: now, updatedAt: now,
    };
    dishes[id] = { id, ...dish };
    write(`households/${HID}/dishes/${id}`, dish);
  }

  const day = (offset, data) => write(`households/${HID}/days/${addDays(ws, offset)}`, { ...data, updatedBy: UID, updatedAt: now });
  const cook = id => ({ kind: 'koken', dishId: id, dishSnapshot: dishSnapshot(dishes[id]) });
  day(0, cook('d1'));
  day(1, cook('d3'));
  day(2, { kind: 'afhaal', note: 'Frituur' });
  day(3, cook('d7'));
  // vorige week, voor statistieken en "kopieer naar huidige week"
  day(-7, cook('d2'));
  day(-6, cook('d5'));
  day(-5, cook('d6'));
  day(-4, { kind: 'opwarm', note: 'Restje soep' });
  day(-3, cook('d4'));
  day(-2, { kind: 'niet_koken', note: 'Uit eten' });
  day(-1, cook('d8'));

  write(`households/${HID}/weeks/${ws}`, { checked: { 'ui|stuk': true }, updatedBy: UID, updatedAt: now });

  const loose = (id, name, recurring, checked = false) => write(`households/${HID}/looseItems/${id}`,
    { name, recurring, checked, createdBy: UID, createdAt: now, updatedAt: now });
  loose('l1', 'Keukenrol', false);
  loose('l2', 'Melk', true);
  loose('l3', 'Koffie', true, true);

  const pantry = (id, name, unit, quantity, minQuantity) => {
    const data = { name, createdBy: UID, createdAt: now, updatedAt: now };
    if (unit) data.unit = unit;
    if (quantity != null) data.quantity = quantity;
    if (minQuantity != null) data.minQuantity = minQuantity;
    write(`households/${HID}/pantry/${id}`, data);
  };
  pantry('p1', 'rijst', 'g', 1000, 300);
  pantry('p2', 'spaghetti', 'g', 250, 500);
  pantry('p3', 'zout', null, null, null);
  pantry('p4', 'ui', 'stuk', 4, 2);
  pantry('p5', 'passata', 'ml', 0, null);
}
