<script lang="ts">
	import { Grid, Row, Column, Button, Tile, Tag, InlineNotification } from 'carbon-components-svelte';
	import { ArrowLeft, Add, ArrowRight } from 'carbon-icons-svelte';
	import { api } from '$lib/api';
	import { auth } from '$lib/stores/auth.svelte';
	import { onMount } from 'svelte';
	import type { Itinerary } from '$lib/types';
	import { transportModeLabel, modeTagColor } from '$lib/transport-modes';
	import { formatMoney } from '$lib/money';

	let itineraries = $state<Itinerary[]>([]);
	let loading = $state(true);
	let error = $state('');

	const BASIS_LABELS: Record<string, string> = {
		PER_KM: 'per km',
		PER_CONTAINER: 'per container',
		PER_CHARGEABLE_KG: 'per chargeable-kg',
		PER_PIECE: 'per piece',
		PER_HOUR: 'per hour',
		FLAT: 'flat'
	};
	function basisLabel(unit: string | undefined, qty: number | undefined): string {
		if (!unit) return '';
		const l = BASIS_LABELS[unit] ?? unit;
		return qty != null ? `${l} × ${qty}` : l;
	}
	function nodeName(name: string | undefined, code: string | undefined): string {
		if (!name) return '?';
		return code ? `${name} (${code})` : name;
	}

	function statusKind(status: string | undefined): 'blue' | 'green' | 'cyan' | 'gray' | 'red' {
		switch (status) {
			case 'PLANNED': return 'blue';
			case 'IN_TRANSIT': return 'cyan';
			case 'DELIVERED': return 'green';
			case 'CANCELLED': return 'red';
			default: return 'gray';
		}
	}

	onMount(async () => {
		try {
			itineraries = await api.get<Itinerary[]>(
				auth.isAdmin ? '/api/admin/itineraries' : '/api/shipper/itineraries'
			);
		} catch (e: any) {
			error = e.message || 'Failed to load itineraries';
		} finally {
			loading = false;
		}
	});
</script>

<Grid>
	<Row>
		<Column>
			<div class="page-header">
				<Button kind="ghost" size="small" href="/dashboard" icon={ArrowLeft}>Back to Dashboard</Button>
				<div class="head-row">
					<h1 class="section-heading">Intermodal Itineraries</h1>
					<Button size="small" icon={Add} href="/dashboard/loads/post-intermodal">Post intermodal load</Button>
				</div>
			</div>
		</Column>
	</Row>

	<Row>
		<Column lg={12} md={8} sm={4}>
			{#if error}
				<InlineNotification kind="error" title="Error" subtitle={error} on:close={() => (error = '')} />
			{/if}

			{#if loading}
				<p>Loading itineraries...</p>
			{:else if itineraries.length === 0}
				<InlineNotification kind="info" title="No itineraries yet"
					subtitle="Post a multi-leg intermodal load to see it here." hideCloseButton />
			{:else}
				<div class="it-list">
					{#each itineraries as it}
						<Tile>
							<div class="it-head">
								<div class="it-title">
									<h3>{it.orderTitle ?? `Itinerary #${it.id}`}</h3>
									<div class="it-tags">
										<Tag type={modeTagColor(it.mode)}>{transportModeLabel(it.mode)}</Tag>
										<Tag type={statusKind(it.status)}>{it.status ?? '—'}</Tag>
										{#if auth.isAdmin && it.shipperName}<Tag type="outline">{it.shipperName}</Tag>{/if}
									</div>
								</div>
								<div class="it-route">
									<span class="cc">{it.originCountry ?? '?'}</span>
									<ArrowRight size={16} />
									<span class="cc">{it.destinationCountry ?? '?'}</span>
									<span class="legcount">· {it.legCount ?? it.legs?.length ?? 0} legs</span>
								</div>
							</div>

							<div class="legs">
								{#each it.legs ?? [] as leg}
									<div class="leg">
										<span class="leg-seq">{leg.legSequence}</span>
										<Tag type={modeTagColor(leg.mode)} size="sm">{transportModeLabel(leg.mode)}</Tag>
										<span class="leg-route">
											{nodeName(leg.pickupLocation, leg.pickupCode)} → {nodeName(leg.deliveryLocation, leg.deliveryCode)}
											{#if leg.chargeUnit}<span class="leg-basis">{basisLabel(leg.chargeUnit, leg.chargeableQuantity)}</span>{/if}
										</span>
										<span class="leg-money">
											{formatMoney(leg.carrierCost, leg.currency)}
											<span class="leg-fee">+{leg.commissionPercent}% fee</span>
											= <strong>{formatMoney(leg.shipperTotal, leg.currency)}</strong>
										</span>
									</div>
								{/each}
							</div>

							<div class="it-totals">
								<span>Carrier <strong>{formatMoney(it.carrierCostTotal, it.currency)}</strong></span>
								<span>Platform fee <strong>{formatMoney(it.commissionTotal, it.currency)}</strong></span>
								<span class="grand">Total <strong>{formatMoney(it.grandTotal, it.currency)}</strong></span>
							</div>
						</Tile>
					{/each}
				</div>
			{/if}
		</Column>
	</Row>
</Grid>

<style>
	.page-header { margin-bottom: 1.5rem; }
	.head-row { display: flex; align-items: center; justify-content: space-between; gap: 1rem; flex-wrap: wrap; margin-top: 0.5rem; }
	.head-row h1 { margin: 0; }
	.it-list { display: flex; flex-direction: column; gap: 1rem; }
	.it-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 1rem; flex-wrap: wrap; }
	.it-title h3 { margin: 0 0 0.35rem; }
	.it-tags { display: flex; gap: 0.35rem; flex-wrap: wrap; }
	.it-route { display: flex; align-items: center; gap: 0.4rem; font-size: 0.875rem; color: var(--cds-text-secondary); }
	.it-route .cc { font-weight: 600; color: var(--cds-text-primary); }
	.legcount { margin-left: 0.25rem; }
	.legs { display: flex; flex-direction: column; gap: 0.35rem; margin: 0.75rem 0; }
	.leg {
		display: flex; align-items: center; gap: 0.6rem; flex-wrap: wrap;
		padding: 0.4rem 0.5rem; background: var(--cds-layer, #f4f4f4); font-size: 0.8125rem;
	}
	.leg-seq {
		display: inline-flex; align-items: center; justify-content: center;
		width: 1.4rem; height: 1.4rem; border-radius: 50%;
		background: var(--cds-interactive, #0f62fe); color: #fff; font-weight: 700; font-size: 0.75rem;
	}
	.leg-route { flex: 1 1 auto; min-width: 10rem; }
	.leg-basis {
		display: inline-block;
		margin-left: 0.4rem;
		padding: 0.05rem 0.4rem;
		border-radius: 3px;
		background: var(--cds-layer-accent, #e0e0e0);
		font-size: 0.6875rem;
		color: var(--cds-text-secondary);
	}
	.leg-money { color: var(--cds-text-secondary); }
	.leg-fee { font-size: 0.75rem; }
	.it-totals {
		display: flex; gap: 1.5rem; flex-wrap: wrap;
		border-top: 1px solid var(--cds-border-subtle, #e0e0e0); padding-top: 0.6rem; font-size: 0.875rem;
	}
	.it-totals .grand { margin-left: auto; font-size: 1rem; }
</style>
