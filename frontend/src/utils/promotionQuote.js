export const promotionQuote = (dailyRate, days) => {
  const cents = Math.round(Number(dailyRate) * 100);
  if (!Number.isSafeInteger(cents) || cents <= 0 || cents > 100000 || ![3, 7, 14].includes(Number(days))) return null;
  return cents * Number(days) / 100;
};
