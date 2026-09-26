/**
 * An amount spelled out in French, as the legal line under a Tunisian invoice or quote asks
 * for ("Arrêté le présent devis à la somme de … dinars et … millimes" — Finco prints it on
 * every document, and it is a fiscal mention, so it lives in one tested place).
 */

const UNITS = [
  'zéro', 'un', 'deux', 'trois', 'quatre', 'cinq', 'six', 'sept', 'huit', 'neuf', 'dix',
  'onze', 'douze', 'treize', 'quatorze', 'quinze', 'seize', 'dix-sept', 'dix-huit', 'dix-neuf'
];

const TENS = ['', '', 'vingt', 'trente', 'quarante', 'cinquante', 'soixante'];

/**
 * 1–99. `final` says whether the number ends the phrase: "quatre-vingts" only takes its s then
 * ("quatre-vingts dinars" but "quatre-vingt mille").
 */
function below100(n: number, final: boolean): string {
  if (n < 20) {
    return UNITS[n];
  }
  const ten = Math.floor(n / 10);
  const unit = n % 10;

  if (ten === 7 || ten === 9) {
    // soixante-dix… / quatre-vingt-dix… : the tens are 60 + 10..19 and 80 + 10..19
    const base = ten === 7 ? 'soixante' : 'quatre-vingt';
    const rest = 10 + unit;
    const joiner = ten === 7 && rest === 11 ? ' et ' : '-';
    return base + joiner + UNITS[rest];
  }
  if (ten === 8) {
    return unit === 0 ? (final ? 'quatre-vingts' : 'quatre-vingt') : 'quatre-vingt-' + UNITS[unit];
  }
  if (unit === 0) {
    return TENS[ten];
  }
  return TENS[ten] + (unit === 1 ? ' et un' : '-' + UNITS[unit]);
}

/** 1–999. */
function below1000(n: number, final: boolean): string {
  const hundreds = Math.floor(n / 100);
  const rest = n % 100;
  const parts: string[] = [];

  if (hundreds > 0) {
    if (hundreds === 1) {
      parts.push('cent');
    } else {
      // "deux cents" only when nothing follows
      parts.push(UNITS[hundreds] + (rest === 0 && final ? ' cents' : ' cent'));
    }
  }
  if (rest > 0) {
    parts.push(below100(rest, final));
  }
  return parts.join(' ');
}

/** Any whole number from 0 to 999 999 999 999. */
export function integerInWords(value: number): string {
  if (!Number.isInteger(value) || value < 0 || value > 999_999_999_999) {
    throw new RangeError('The amount is outside what can be spelled out');
  }
  if (value === 0) {
    return UNITS[0];
  }

  const billions = Math.floor(value / 1_000_000_000);
  const millions = Math.floor((value % 1_000_000_000) / 1_000_000);
  const thousands = Math.floor((value % 1_000_000) / 1000);
  const rest = value % 1000;

  const parts: string[] = [];
  if (billions > 0) {
    // "milliard(s)" and "million(s)" are nouns: the number before them agrees
    parts.push(below1000(billions, true) + (billions > 1 ? ' milliards' : ' milliard'));
  }
  if (millions > 0) {
    parts.push(below1000(millions, true) + (millions > 1 ? ' millions' : ' million'));
  }
  if (thousands > 0) {
    // "mille" is invariable and never preceded by "un"; what comes before it takes no plural s
    parts.push(thousands === 1 ? 'mille' : below1000(thousands, false) + ' mille');
  }
  if (rest > 0) {
    parts.push(below1000(rest, true));
  }
  return parts.join(' ');
}

interface CurrencyWords {
  unit: string;
  subunit: string;
  /** Decimals the currency is written with: 3 for the dinar (millimes), 2 for most others. */
  digits: number;
  /** A bare currency code ("XOF") is not pluralised. */
  invariable?: boolean;
}

const CURRENCIES: Record<string, CurrencyWords> = {
  TND: { unit: 'dinar', subunit: 'millime', digits: 3 },
  EUR: { unit: 'euro', subunit: 'centime', digits: 2 },
  USD: { unit: 'dollar', subunit: 'cent', digits: 2 }
};

function plural(count: number, word: string, invariable = false): string {
  return count > 1 && !invariable ? word + 's' : word;
}

/**
 * "trente-sept dinars et cinquante-sept millimes". The amount is rounded to the currency's
 * decimals first, so 37.0569999 never prints a stray millime.
 */
export function amountInWords(amount: number, currency = 'TND'): string {
  const words = CURRENCIES[currency] ?? { unit: currency, subunit: 'centième', digits: 2, invariable: true };
  const factor = Math.pow(10, words.digits);

  const total = Math.round(Math.abs(amount) * factor);
  const whole = Math.floor(total / factor);
  const fraction = total % factor;

  const parts: string[] = [];
  // A bare fraction ("cinq cents millimes") does not start with "zéro dinar"
  if (whole > 0 || fraction === 0) {
    parts.push(`${integerInWords(whole)} ${plural(whole, words.unit, words.invariable)}`);
  }
  if (fraction > 0) {
    parts.push(`${integerInWords(fraction)} ${plural(fraction, words.subunit, words.invariable)}`);
  }
  return parts.join(' et ');
}
