<script lang="ts">
	import {
		Grid, Row, Column,
		Button, TextInput, TextArea, NumberInput, Select, SelectItem,
		InlineNotification, Tag
	} from 'carbon-components-svelte';
	import { ArrowLeft, Add, TrashCan, ArrowUp, ArrowDown } from 'carbon-icons-svelte';
	import { api } from '$lib/api';
	import { auth } from '$lib/stores/auth.svelte';
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import { onMount } from 'svelte';
	import { TRANSPORT_MODE_OPTIONS, transportModeLabel, modeTagColor, estimatedCommissionPct } from '$lib/transport-modes';
	import { estimateLegCarrierCost, chargeableQuantity, chargeUnitForMode } from '$lib/pricing';
	import { formatMoney } from '$lib/money';

	const BASIS_LABELS: Record<string, string> = {
		PER_KM: 'per km',
		PER_CONTAINER: 'per container',
		PER_CHARGEABLE_KG: 'per chargeable-kg',
		PER_PIECE: 'per piece'
	};
	import { HAULAGE_COUNTRIES } from '$lib/countries';
	import { licenceCategoriesFor } from '$lib/licence-categories';

	type ShipperOption = { id: number; companyName: string; email: string; country?: string };
	type LegDraft = {
		clientId: string;
		transportMode: string;
		pickupLocation: string;
		deliveryLocation: string;
		pickupCountry: string;
		deliveryCountry: string;
		requiredLicenceCategory: string;
		distanceKm: number;
		weightKg: number;
		volumeM3: number;
		containerCount: number;
	};

	let shipperCountry = $state('IE');
	let licenceOptions = $derived(licenceCategoriesFor(shipperCountry));

	let order = $state({ title: '', description: '', dateNeeded: '' });

	function newLeg(mode: string): LegDraft {
		return {
			clientId: `leg-${Math.random().toString(36).slice(2, 9)}`,
			transportMode: mode,
			pickupLocation: '',
			deliveryLocation: '',
			pickupCountry: shipperCountry,
			deliveryCountry: shipperCountry,
			requiredLicenceCategory: 'C',
			distanceKm: 100,
			weightKg: 100,
			volumeM3: 1,
			containerCount: 1
		};
	}

	// Sensible starter: a road feeder into an ocean main leg.
	let legs = $state<LegDraft[]>([newLeg('ROAD'), newLeg('OCEAN')]);

	// Live estimate, mirroring the server: each leg priced on its mode's rate
	// card (per-km / per-container / per chargeable-kg), then the per-mode fee
	// on top, summed. The backend recomputes the authoritative figure on submit.
	function legQuantities(l: LegDraft) {
		return {
			distanceKm: Number(l.distanceKm) || undefined,
			weightKg: Number(l.weightKg) || undefined,
			volumeM3: Number(l.volumeM3) || undefined,
			containerCount: Number(l.containerCount) || undefined
		};
	}
	let preview = $derived.by(() => {
		let carrier = 0;
		let fee = 0;
		const rows = legs.map((l) => {
			const q = legQuantities(l);
			const c = estimateLegCarrierCost(l.transportMode, q) ?? 0;
			const pct = estimatedCommissionPct(l.transportMode);
			const f = (c * pct) / 100;
			carrier += c;
			fee += f;
			return {
				carrier: c,
				pct,
				fee: f,
				total: c + f,
				unit: chargeUnitForMode(l.transportMode),
				qty: chargeableQuantity(l.transportMode, q)
			};
		});
		return { rows, carrier, fee, total: carrier + fee };
	});

	let shippers = $state<ShipperOption[]>([]);
	let selectedShipperId = $state<string>('');

	onMount(async () => {
		if (auth.isAdmin) {
			try {
				shippers = await api.get<ShipperOption[]>('/api/admin/shippers');
				const hinted = page.url.searchParams.get('shipperId');
				if (hinted && shippers.some((e) => String(e.id) === hinted)) {
					selectedShipperId = hinted;
				} else if (shippers.length > 0) {
					selectedShipperId = String(shippers[0].id);
				}
			} catch {
				shippers = [];
			}
		}
	});

	let postError = $state('');
	let postSuccess = $state('');
	let postLoading = $state(false);

	function addLeg() {
		legs = [...legs, newLeg('ROAD')];
	}
	function removeLeg(i: number) {
		if (legs.length <= 1) return;
		legs = legs.filter((_, j) => j !== i);
	}
	function moveLeg(i: number, delta: -1 | 1) {
		const t = i + delta;
		if (t < 0 || t >= legs.length) return;
		const copy = [...legs];
		[copy[i], copy[t]] = [copy[t], copy[i]];
		legs = copy;
	}

	function validate(): string | null {
		if (!order.title.trim()) return 'Title is required.';
		if (legs.length < 1) return 'Add at least one leg.';
		for (let i = 0; i < legs.length; i++) {
			const l = legs[i];
			if (!l.pickupLocation.trim() || !l.deliveryLocation.trim()) {
				return `Leg ${i + 1} needs a pickup and delivery location.`;
			}
		}
		return null;
	}

	async function postIntermodal() {
		postError = '';
		postSuccess = '';
		const v = validate();
		if (v) {
			postError = v;
			return;
		}
		postLoading = true;
		try {
			const payload = {
				...order,
				legs: legs.map((l) => ({
					transportMode: l.transportMode,
					pickupLocation: l.pickupLocation,
					deliveryLocation: l.deliveryLocation,
					pickupCountry: l.pickupCountry,
					deliveryCountry: l.deliveryCountry,
					requiredLicenceCategory: l.requiredLicenceCategory,
					// Mode-specific quantity drives the rate-card carrier cost.
					distanceKm: l.transportMode === 'ROAD' ? l.distanceKm : undefined,
					weightKg: l.transportMode === 'AIR' ? l.weightKg : undefined,
					volumeM3: l.transportMode === 'AIR' ? l.volumeM3 : undefined,
					containerCount:
						l.transportMode === 'OCEAN' || l.transportMode === 'RAIL'
							? l.containerCount
							: undefined
				}))
			};
			if (auth.isAdmin) {
				if (!selectedShipperId) {
					postError = 'Please choose an shipper to create the load under.';
					postLoading = false;
					return;
				}
				await api.post(
					`/api/admin/itineraries?shipperId=${encodeURIComponent(selectedShipperId)}`,
					payload
				);
			} else {
				await api.post('/api/shipper/itineraries', payload);
			}
			postSuccess = 'Intermodal load created! Redirecting...';
			setTimeout(() => goto('/dashboard/itineraries'), 1200);
		} catch (e: any) {
			postError = e.message || 'Failed to create intermodal load';
		} finally {
			postLoading = false;
		}
	}
</script>

<Grid>
	<Row>
		<Column>
			<div class="page-header">
				<Button kind="ghost" size="small" href="/dashboard/loads/post" icon={ArrowLeft}>
					Single-leg load instead
				</Button>
				<h1><Add size={24} /> Post an Intermodal Load</h1>
				<p class="sub">
					Build a door-to-door movement from multiple legs — each with its own mode, route, and
					rate. We price every leg with its mode's platform fee and roll them up to one total.
				</p>
			</div>
		</Column>
	</Row>

	<Row>
		<Column lg={11} md={8} sm={4}>
			{#if postError}
				<InlineNotification kind="error" title="Error" subtitle={postError}
					on:close={() => (postError = '')} />
			{/if}
			{#if postSuccess}
				<InlineNotification kind="success" title="Success" subtitle={postSuccess}
					on:close={() => (postSuccess = '')} />
			{/if}

			<div class="form-grid">
				{#if auth.isAdmin}
					<Select bind:selected={selectedShipperId} labelText="Create Load On Behalf Of (Shipper)">
						{#each shippers as emp}
							<SelectItem value={String(emp.id)} text="{emp.companyName} ({emp.email})" />
						{/each}
					</Select>
				{/if}

				<TextInput bind:value={order.title}
					labelText="Load Title" placeholder="e.g. Dublin to Amsterdam door-to-door" />
				<TextArea bind:value={order.description}
					labelText="Description" placeholder="Describe the shipment..." rows={2} />
				<TextInput bind:value={order.dateNeeded}
					labelText="Date Needed (YYYY-MM-DD)" placeholder="2026-06-10" type="date" />

				<div class="legs-section">
					<div class="legs-header">
						<h3>Legs</h3>
						<Button kind="tertiary" size="small" icon={Add} on:click={addLeg}>Add leg</Button>
					</div>
					<p class="legs-hint">Legs run in order, top to bottom. Each leg is priced on its own mode.</p>

					{#each legs as leg, i (leg.clientId)}
						<div class="leg-card">
							<div class="leg-top">
								<span class="leg-seq">Leg {i + 1}</span>
								<Tag type={modeTagColor(leg.transportMode)}>{transportModeLabel(leg.transportMode)}</Tag>
								<span class="leg-spacer"></span>
								<Button kind="ghost" size="small" icon={ArrowUp} iconDescription="Move up"
									disabled={i === 0} on:click={() => moveLeg(i, -1)} />
								<Button kind="ghost" size="small" icon={ArrowDown} iconDescription="Move down"
									disabled={i === legs.length - 1} on:click={() => moveLeg(i, 1)} />
								<Button kind="danger-ghost" size="small" icon={TrashCan} iconDescription="Remove leg"
									disabled={legs.length <= 1} on:click={() => removeLeg(i)} />
							</div>

							<div class="leg-row">
								<Select bind:selected={leg.transportMode} labelText="Mode">
									{#each TRANSPORT_MODE_OPTIONS as opt}
										<SelectItem value={opt.value} text={opt.label} />
									{/each}
								</Select>
								<Select bind:selected={leg.requiredLicenceCategory} labelText="Required Licence">
									{#each licenceOptions as opt}
										<SelectItem value={opt.code} text={opt.label} />
									{/each}
								</Select>
							</div>

							<div class="leg-row">
								<Select bind:selected={leg.pickupCountry} labelText="From country">
									{#each HAULAGE_COUNTRIES as c}
										<SelectItem value={c.code} text="{c.code} — {c.name}" />
									{/each}
								</Select>
								<TextInput bind:value={leg.pickupLocation} labelText="From" placeholder="Pickup location" />
							</div>
							<div class="leg-row">
								<Select bind:selected={leg.deliveryCountry} labelText="To country">
									{#each HAULAGE_COUNTRIES as c}
										<SelectItem value={c.code} text="{c.code} — {c.name}" />
									{/each}
								</Select>
								<TextInput bind:value={leg.deliveryLocation} labelText="To" placeholder="Delivery location" />
							</div>

							<div class="leg-row">
								{#if leg.transportMode === 'AIR'}
									<NumberInput bind:value={leg.weightKg} label="Weight (kg)" min={1} step={1} />
									<NumberInput bind:value={leg.volumeM3} label="Volume (m³)" min={0.1} step={0.1} />
								{:else if leg.transportMode === 'ROAD'}
									<NumberInput bind:value={leg.distanceKm} label="Distance (km)" min={1} step={1} />
								{:else}
									<NumberInput bind:value={leg.containerCount} label="Containers" min={1} step={1} />
								{/if}
							</div>

							<div class="leg-price">
								<span class="leg-basis">{BASIS_LABELS[preview.rows[i]?.unit] ?? ''} × {preview.rows[i]?.qty ?? '—'}</span>
								<span>Carrier {formatMoney(preview.rows[i]?.carrier)}</span>
								<span>+ {transportModeLabel(leg.transportMode)} fee {preview.rows[i]?.pct}% ({formatMoney(preview.rows[i]?.fee)})</span>
								<strong>= {formatMoney(preview.rows[i]?.total)}</strong>
							</div>
						</div>
					{/each}
				</div>

				<div class="totals">
					<div class="totals-line"><span>Carrier cost (all legs)</span><strong>{formatMoney(preview.carrier)}</strong></div>
					<div class="totals-line"><span>Platform fee (all legs)</span><strong>{formatMoney(preview.fee)}</strong></div>
					<div class="totals-line grand"><span>Estimated total</span><strong>{formatMoney(preview.total)}</strong></div>
					<p class="totals-hint">Estimate — the server confirms exact per-mode fees on submit.</p>
				</div>

				<Button on:click={postIntermodal} disabled={postLoading || !order.title}>
					{postLoading ? 'Creating...' : 'Create Intermodal Load'}
				</Button>
			</div>
		</Column>
	</Row>
</Grid>

<style>
	.page-header { margin-bottom: 1.5rem; }
	.page-header h1 { display: flex; align-items: center; gap: 0.5rem; margin-top: 0.5rem; }
	.sub { color: var(--cds-text-secondary); max-width: 720px; line-height: 1.5; }
	.form-grid { display: flex; flex-direction: column; gap: 1rem; max-width: 820px; }
	.legs-section {
		display: flex; flex-direction: column; gap: 0.75rem; padding: 1rem;
		background: var(--cds-layer, #f4f4f4);
		border-left: 3px solid var(--cds-interactive, #0f62fe);
	}
	.legs-header { display: flex; justify-content: space-between; align-items: center; }
	.legs-header h3 { margin: 0; font-size: 1rem; }
	.legs-hint { font-size: 0.8125rem; color: var(--cds-text-secondary); margin: 0; }
	.leg-card {
		display: flex; flex-direction: column; gap: 0.5rem;
		padding: 0.75rem; background: var(--cds-background, #fff);
		border: 1px solid var(--cds-border-subtle, #e0e0e0);
	}
	.leg-top { display: flex; align-items: center; gap: 0.5rem; }
	.leg-seq { font-weight: 600; font-size: 0.875rem; }
	.leg-spacer { flex: 1 1 auto; }
	.leg-row { display: grid; grid-template-columns: 1fr 2fr; gap: 0.5rem; }
	.leg-price {
		display: flex; gap: 0.75rem; flex-wrap: wrap; align-items: baseline;
		font-size: 0.8125rem; color: var(--cds-text-secondary);
		border-top: 1px dashed var(--cds-border-subtle, #e0e0e0); padding-top: 0.5rem;
	}
	.leg-price strong { color: var(--cds-text-primary); font-size: 0.9375rem; }
	.leg-basis {
		padding: 0.05rem 0.4rem;
		border-radius: 3px;
		background: var(--cds-layer-accent, #e0e0e0);
		font-size: 0.6875rem;
	}
	.totals {
		background: var(--cds-layer, #f4f4f4);
		border-left: 3px solid var(--cds-support-success, #24a148);
		padding: 0.75rem 1rem;
	}
	.totals-line { display: flex; justify-content: space-between; gap: 1rem; font-size: 0.875rem; }
	.totals-line.grand {
		border-top: 1px solid var(--cds-border-subtle, #e0e0e0);
		margin-top: 0.35rem; padding-top: 0.45rem; font-size: 1.0625rem;
	}
	.totals-hint { font-size: 0.75rem; color: var(--cds-text-secondary); margin: 0.5rem 0 0; }
	@media (max-width: 672px) {
		.leg-row { grid-template-columns: 1fr; }
	}
</style>
