// Sets the author's private phrase for Kinhold: `node scripts/set-phrase.mjs` generates a random one (8 unrelated words) and
// prints it ONCE; or `node scripts/set-phrase.mjs "your own phrase of at least four words"` hashes a phrase you choose.
// Only a salted slow hash (PBKDF2-SHA256, 200000 rounds) is written, into model/Authorship.kt. The phrase is never saved:
// keep it in your password manager. Same rule as Tidypage: capitals and extra spaces do not matter.
import { pbkdf2Sync, randomBytes, randomInt } from "node:crypto";
import { readFileSync, writeFileSync } from "node:fs";

const FILE = new URL("../android/app/src/main/java/com/inamibrahim/kinhold/model/Authorship.kt", import.meta.url);
const ITERATIONS = 200000;
const WORDS = ("amber anchor apple arrow aspen badge baker basil beach birch bison blade bloom boat brass bridge brook cabin camel candle canyon cedar chalk cherry cliff clover cobalt comet copper coral cotton crane crimson dagger daisy delta desert dune eagle ember fabric falcon fern field flint forest fossil galaxy garden ginger glacier granite harbor hazel heron hollow honey island ivory jacket jasper jungle kettle lagoon lantern lemon lilac linen maple marble meadow mirror monsoon mosaic nectar nickel oasis olive onyx orchid otter paddle pebble pepper pigeon plum quartz quiver raven ribbon river saddle saffron sapphire shadow silver slate spruce summit swallow tangle thistle thunder timber tulip velvet violet walnut willow winter zephyr yonder").split(" ");
const normalize = (p) => p.normalize("NFC").toLowerCase().split(" ").filter(Boolean).join(" ");

let phrase = process.argv[2];
let generated = false;
if (!phrase) { phrase = Array.from({ length: 8 }, () => WORDS[randomInt(WORDS.length)]).join(" "); generated = true; }
phrase = normalize(phrase);
if (phrase.split(" ").length < 4 || phrase.length < 20) { console.error("Too short: use at least four words and 20 characters."); process.exit(1); }

const salt = randomBytes(16).toString("hex");
const hash = pbkdf2Sync(phrase, Buffer.from(salt, "hex"), ITERATIONS, 32, "sha256").toString("hex");
const setOn = new Date().toISOString().slice(0, 10);
const text = readFileSync(FILE, "utf8");
const b = text.indexOf("// BEGIN PHRASE"), e = text.indexOf("// END PHRASE");
if (b < 0 || e < 0) { console.error("Phrase block not found."); process.exit(1); }
const block = `// BEGIN PHRASE (written by scripts/set-phrase.mjs; do not edit by hand)\n    const val SALT = "${salt}"\n    const val HASH = "${hash}"\n    const val ITERATIONS = ${ITERATIONS}\n    const val SET_ON = "${setOn}"\n    `;
writeFileSync(FILE, text.slice(0, b) + block + text.slice(e));
console.log(`Phrase set on ${setOn}. Fingerprint: ${hash}`);
if (generated) console.log(`PHRASE: ${phrase}`);
