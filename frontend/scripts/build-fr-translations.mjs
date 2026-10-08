// Builds src/locale/fr.json (bundled by src/translations.ts) from the messages extracted by
// `ng extract-i18n` and the English→French table in src/locale/fr-translations.json.
// Fails when a message has no French text or when a translation changes its placeholders.
import { readFileSync, writeFileSync } from 'node:fs';

const normalize = (text) => text.replace(/\s+/g, ' ').trim();
const placeholders = (text) =>
  [...text.matchAll(/\{\$[A-Za-z0-9_]+\}|\{[A-Z][A-Z0-9_]+\}/g)].map((m) => m[0]).sort();

const { translations: source } = JSON.parse(readFileSync('src/locale/messages.json', 'utf8'));
const table = JSON.parse(readFileSync('src/locale/fr-translations.json', 'utf8'));
const lookup = new Map(Object.entries(table).map(([english, french]) => [normalize(english), french]));

const output = {};
const problems = [];
for (const [id, english] of Object.entries(source)) {
  const french = lookup.get(normalize(english));
  if (french === undefined) {
    problems.push(`missing: ${JSON.stringify(normalize(english))}`);
    continue;
  }
  if (placeholders(english).join() !== placeholders(french).join()) {
    problems.push(`placeholders differ: ${JSON.stringify(normalize(english))}`);
    continue;
  }
  output[id] = french;
}

if (problems.length > 0) {
  console.error(problems.join('\n'));
  console.error(`${problems.length} French translation problem(s).`);
  process.exit(1);
}
writeFileSync('src/locale/fr.json', `${JSON.stringify({ locale: 'fr', translations: output }, null, 2)}\n`);
console.log(`src/locale/fr.json: ${Object.keys(output).length} messages.`);
