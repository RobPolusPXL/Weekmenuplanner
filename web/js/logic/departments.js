// Winkelafdelingen via een vaste woordenlijst (geen AI), gelijk aan GroceryDepartments in de Android-app.

/** Weergavevolgorde: zoals je meestal door de winkel loopt. */
export const DEPARTMENTS = [
  ['GROENTEN_FRUIT', 'Groenten & fruit'],
  ['BROOD', 'Brood & bakkerij'],
  ['VLEES_VIS', 'Vlees, vis & vega'],
  ['ZUIVEL', 'Zuivel & eieren'],
  ['DROGE_WAREN', 'Pasta, rijst & droge waren'],
  ['KRUIDEN_SAUZEN', 'Kruiden, sauzen & conserven'],
  ['DIEPVRIES', 'Diepvries'],
  ['DRANKEN', 'Dranken'],
  ['OVERIG', 'Overig'],
];

export const departmentLabel = key => DEPARTMENTS.find(d => d[0] === key)?.[1] ?? 'Overig';

// Volgorde = matchprioriteit (specifiek eerst), niet weergavevolgorde.
const RULES = [
  ['ZUIVEL', ['zoute boter', 'zoute roomboter']],
  ['GROENTEN_FRUIT', ['watermeloen', 'zoete aardappel', 'pepertjes']],
  ['DIEPVRIES', ['diepvries', 'ijs', 'bevroren']],
  ['KRUIDEN_SAUZEN', [
    'bouillon', 'saus', 'ketchup', 'mayonaise', 'mosterd', 'azijn', 'sojasaus', 'ketjap',
    'sambal', 'pesto', 'tomatenpuree', 'tomatenblokjes', 'gepelde tomaten', 'passata',
    'kokosmelk', 'olie', 'olijfolie', 'zout', 'peper', 'poeder', 'kruiden', 'kruid', 'kerrie',
    'kaneel', 'komijn', 'kurkuma', 'oregano', 'tijm', 'rozemarijn', 'laurier', 'nootmuskaat',
    'gember', 'koriander', 'kardemom', 'paprikapoeder', 'chilivlokken', 'currypasta', 'currypoeder',
    'maïs', 'mais', 'kikkererwten', 'kidneybonen', 'bonen in blik', 'linzen', 'tonijn', 'ansjovis',
    'olijven', 'kappertjes', 'honing', 'siroop', 'jam', 'pindakaas', 'hagelslag', 'suiker',
  ]],
  ['DRANKEN', ['water', 'cola', 'frisdrank', 'sap', 'wijn', 'bier', 'koffie', 'thee', 'limonade']],
  ['BROOD', [
    'brood', 'boterham', 'wrap', 'tortilla', 'pita', 'bagel', 'croissant', 'stokbrood',
    'ciabatta', 'focaccia', 'broodje', 'hamburgerbroodje', 'naan', 'toast', 'beschuit', 'paneermeel',
  ]],
  ['ZUIVEL', [
    'melk', 'boter', 'room', 'kaas', 'yoghurt', 'yogurt', 'kwark', 'skyr', 'ei', 'eieren',
    'eigeel', 'eiwit', 'mozzarella', 'feta', 'parmezaan', 'mascarpone', 'ricotta', 'crème fraîche',
    'creme fraiche', 'slagroom', 'margarine', 'halfvolle', 'karnemelk', 'vla', 'cottage',
  ]],
  ['VLEES_VIS', [
    'gehakt', 'kip', 'kipfilet', 'kippendij', 'kalkoen', 'rund', 'varken', 'spek', 'spekjes',
    'bacon', 'ham', 'worst', 'chorizo', 'salami', 'filet', 'biefstuk', 'karbonade', 'schnitzel',
    'braadworst', 'hamburger', 'burger', 'vlees', 'lam', 'zalm', 'vis', 'garnaal', 'garnalen',
    'kabeljauw', 'pangasius', 'makreel', 'haring', 'mosselen', 'scampi', 'tofu', 'tempeh',
    'vegetarisch', 'quorn', 'seitan', 'rookvlees', 'kipstuckjes', 'kipstukjes',
  ]],
  ['GROENTEN_FRUIT', [
    'ui', 'uien', 'knoflook', 'sjalot', 'prei', 'wortel', 'wortelen', 'aardappel', 'aardappelen',
    'tomaat', 'tomaten', 'komkommer', 'paprika', 'courgette', 'aubergine', 'broccoli', 'bloemkool',
    'spinazie', 'sla', 'ijsbergsla', 'rucola', 'spruitjes', 'kool', 'witlof', 'venkel', 'champignon',
    'champignons', 'paddenstoel', 'pompoen', 'zoete aardappel', 'bataat', 'radijs', 'rode biet', 'biet',
    'selderij', 'bleekselderij', 'asperge', 'doperwten', 'sperziebonen', 'snijbonen', 'erwten',
    'peterselie', 'basilicum', 'bieslook', 'dille', 'munt', 'lente-ui', 'lenteui', 'bosui',
    'appel', 'peer', 'banaan', 'citroen', 'limoen', 'sinaasappel', 'mandarijn', 'druiven',
    'aardbei', 'aardbeien', 'framboos', 'frambozen', 'blauwe bessen', 'bessen', 'mango', 'ananas',
    'avocado', 'meloen', 'kiwi', 'perzik', 'nectarine', 'pruim', 'fruit', 'groente', 'groenten',
    'salade', 'mais kolf', 'chilipeper', 'rode peper', 'spitskool', 'rodekool', 'witte kool', 'snoeptomaat',
  ]],
  ['DROGE_WAREN', [
    'pasta', 'spaghetti', 'penne', 'macaroni', 'tagliatelle', 'lasagne', 'fusilli', 'rigatoni',
    'rijst', 'couscous', 'bulgur', 'quinoa', 'noedels', 'mie', 'bami', 'nasi', 'meel', 'bloem',
    'havermout', 'muesli', 'cornflakes', 'granola', 'cracker', 'chips', 'noten', 'amandelen',
    'walnoten', 'cashew', 'pinda', 'pitten', 'zaden', 'rozijnen', 'bakpoeder', 'gist', 'chocolade',
    'koekje', 'koek', 'snoep', 'tortillachips', 'linzen droog',
  ]],
];

const TOKEN_SPLIT = /[^\p{L}\p{N}-]+/u;

export function departmentFor(rawName) {
  const name = String(rawName ?? '').trim().toLowerCase();
  if (!name) return 'OVERIG';
  const tokens = name.split(TOKEN_SPLIT).filter(Boolean);
  for (const [dept, keywords] of RULES) {
    for (const kw of keywords) {
      const hit = kw.includes(' ')
        ? name.includes(kw)
        : tokens.some(t => t === kw || (kw.length >= 4 && t.includes(kw)));
      if (hit) return dept;
    }
  }
  return 'OVERIG';
}

/** Groepeert items per afdeling in winkelvolgorde: [[key, label, items], …]; lege afdelingen vervallen. */
export function groupByDepartment(items, nameOf) {
  const by = new Map();
  for (const item of items) {
    const d = departmentFor(nameOf(item));
    if (!by.has(d)) by.set(d, []);
    by.get(d).push(item);
  }
  return DEPARTMENTS.filter(([k]) => by.has(k)).map(([k, label]) => [k, label, by.get(k)]);
}
