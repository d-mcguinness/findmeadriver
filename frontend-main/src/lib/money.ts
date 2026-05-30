// Centralised money formatting. Backend amounts are already 2dp BigDecimals;
// this renders them with the right currency symbol.

/** Format an amount in the given ISO-4217 currency (default EUR). */
export function formatMoney(amount: number | null | undefined, currency = 'EUR'): string {
	if (amount == null || Number.isNaN(Number(amount))) return '—';
	try {
		return new Intl.NumberFormat('en-IE', {
			style: 'currency',
			currency: currency || 'EUR',
			maximumFractionDigits: 2
		}).format(Number(amount));
	} catch {
		// Unknown currency code — fall back to a plain number.
		return `${Number(amount).toFixed(2)} ${currency ?? ''}`.trim();
	}
}
