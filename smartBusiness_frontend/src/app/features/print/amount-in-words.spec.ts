import { amountInWords, integerInWords } from './amount-in-words';

describe('integerInWords', () => {

  const cases: [number, string][] = [
    [0, 'zéro'], [1, 'un'], [11, 'onze'], [16, 'seize'], [17, 'dix-sept'],
    [20, 'vingt'], [21, 'vingt et un'], [22, 'vingt-deux'], [31, 'trente et un'], [45, 'quarante-cinq'],
    [61, 'soixante et un'], [70, 'soixante-dix'], [71, 'soixante et onze'], [72, 'soixante-douze'], [79, 'soixante-dix-neuf'],
    [80, 'quatre-vingts'], [81, 'quatre-vingt-un'], [85, 'quatre-vingt-cinq'], [90, 'quatre-vingt-dix'], [91, 'quatre-vingt-onze'], [99, 'quatre-vingt-dix-neuf'],
    [100, 'cent'], [101, 'cent un'], [180, 'cent quatre-vingts'], [200, 'deux cents'], [201, 'deux cent un'], [999, 'neuf cent quatre-vingt-dix-neuf'],
    [1000, 'mille'], [1001, 'mille un'], [1100, 'mille cent'], [2000, 'deux mille'], [21000, 'vingt et un mille'],
    [80000, 'quatre-vingt mille'], [200000, 'deux cent mille'], [123456, 'cent vingt-trois mille quatre cent cinquante-six'],
    [1000000, 'un million'], [2000000, 'deux millions'], [200000000, 'deux cents millions'],
    [1234567, 'un million deux cent trente-quatre mille cinq cent soixante-sept'],
    [3000000000, 'trois milliards']
  ];

  for (const [value, words] of cases) {
    it(`spells ${value} as "${words}"`, () => {
      expect(integerInWords(value)).toBe(words);
    });
  }

  it('refuses what it cannot spell', () => {
    expect(() => integerInWords(-1)).toThrowError(RangeError);
    expect(() => integerInWords(1.5)).toThrowError(RangeError);
    expect(() => integerInWords(1e12)).toThrowError(RangeError);
  });
});

describe('amountInWords', () => {

  it('spells the Finco reference case: 37,057 DT', () => {
    expect(amountInWords(37.057)).toBe('trente-sept dinars et cinquante-sept millimes');
  });

  it('uses the singular for one dinar and one millime', () => {
    expect(amountInWords(1)).toBe('un dinar');
    expect(amountInWords(1.001)).toBe('un dinar et un millime');
  });

  it('leaves the millimes out when there are none', () => {
    expect(amountInWords(120)).toBe('cent vingt dinars');
  });

  it('does not open with "zéro dinar" for an amount under one dinar', () => {
    expect(amountInWords(0.5)).toBe('cinq cents millimes');
    expect(amountInWords(0.001)).toBe('un millime');
  });

  it('spells zero', () => {
    expect(amountInWords(0)).toBe('zéro dinar');
  });

  it('rounds to the millime instead of printing a stray fraction', () => {
    expect(amountInWords(37.0569999)).toBe('trente-sept dinars et cinquante-sept millimes');
    expect(amountInWords(0.1 + 0.2)).toBe('trois cents millimes');
  });

  it('spells a large amount', () => {
    expect(amountInWords(1234.5)).toBe('mille deux cent trente-quatre dinars et cinq cents millimes');
  });

  it('handles the other currencies it knows, with two decimals', () => {
    expect(amountInWords(12.5, 'EUR')).toBe('douze euros et cinquante centimes');
    expect(amountInWords(1, 'USD')).toBe('un dollar');
  });

  it('falls back on the currency code for an unknown one', () => {
    expect(amountInWords(5, 'XOF')).toBe('cinq XOF');
  });
});
