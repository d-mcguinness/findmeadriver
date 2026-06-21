<script lang="ts">
	import { Grid, Row, Column, Button, Tile } from 'carbon-components-svelte';
	import {
		DeliveryTruck, Enterprise, ArrowRight, ChartLineData, Money, Checkmark,
		Train, Plane, Anchor
	} from 'carbon-icons-svelte';
	import { auth } from '$lib/stores/auth.svelte';
	import { onMount } from 'svelte';
	import { loadModePricing, FALLBACK_MODE_PRICING, type ModePricing } from '$lib/pricing';
	import { formatMoney } from '$lib/money';

	const MODE_ICON: Record<string, typeof DeliveryTruck> = {
		ROAD: DeliveryTruck,
		RAIL: Train,
		OCEAN: Anchor,
		AIR: Plane
	};

	// Worked-example carrier cost used to make the per-mode fee tangible.
	const EXAMPLE_CARRIER_COST = 1000;

	let modes = $state<ModePricing[]>(FALLBACK_MODE_PRICING);
	onMount(async () => {
		modes = await loadModePricing();
	});
</script>

<div class="pricing-page">
	<Grid>
		<Row>
			<Column>
				<div class="hero">
					<span class="eyebrow">Transparent pricing</span>
					<h1>Pricing</h1>
					<p class="hero-subtitle">
						One simple model across every mode: you pay the <strong>carrier's cost</strong> plus a
						single <strong>platform fee</strong> that depends only on the transport mode. The fee is
						shown before you post a load, and it's the only thing we add — no agency markup, no
						per-day minimums, no hidden line items.
					</p>
				</div>
			</Column>
		</Row>

		<Row>
			<Column>
				<Tile class="formula-tile">
					<div class="formula">
						<span class="f-term">Carrier cost</span>
						<span class="f-op">+</span>
						<span class="f-term f-fee">Mode platform fee</span>
						<span class="f-op">=</span>
						<span class="f-term f-total">What you pay</span>
					</div>
					<p class="formula-note">
						The carrier sets their rate; we add the mode fee and compute your total on the server
						when you post — so the price you see is the price you pay.
					</p>
				</Tile>
			</Column>
		</Row>

		<Row>
			<Column>
				<h2 class="section-heading">Platform fee by mode</h2>
			</Column>
		</Row>
		<Row class="modes-row">
			{#each modes as m}
				{@const Icon = MODE_ICON[m.mode] ?? DeliveryTruck}
				{@const fee = (EXAMPLE_CARRIER_COST * m.commissionPercent) / 100}
				<Column lg={4} md={4} sm={4}>
					<div class="fmad-card">
						<Tile>
							<div class="mode-card">
								<div class="mode-head">
									<span class="icon-badge sm"><Icon size={20} /></span>
									<h3>{m.label}</h3>
								</div>
								<div class="mode-fee">
									<span class="fee-num">{m.commissionPercent}%</span>
									<span class="fee-label">platform fee</span>
								</div>
								<p class="mode-basis"><Money size={16} /> {m.basis}</p>
								<p class="mode-tagline">{m.tagline}</p>
								<div class="mode-example">
									<span>On a {formatMoney(EXAMPLE_CARRIER_COST)} carrier cost:</span>
									<span class="ex-line"><Checkmark size={14} /> fee {formatMoney(fee)}</span>
									<span class="ex-total">you pay {formatMoney(EXAMPLE_CARRIER_COST + fee)}</span>
								</div>
							</div>
						</Tile>
					</div>
				</Column>
			{/each}
		</Row>

		<Row>
			<Column>
				<div class="why-pricing">
					<div class="why-item"><span class="icon-badge sm"><ChartLineData size={20} /></span><div><strong>Transparent</strong><p>See the exact fee before you commit — the post-a-load form previews it live.</p></div></div>
					<div class="why-item"><span class="icon-badge sm"><Money size={20} /></span><div><strong>Mode-fair</strong><p>Air and sea cost more to broker than road, so the fee reflects the mode — nothing else.</p></div></div>
					<div class="why-item"><span class="icon-badge sm"><Checkmark size={20} /></span><div><strong>No markup</strong><p>We never inflate the carrier's rate. The platform fee is all we charge.</p></div></div>
				</div>
			</Column>
		</Row>

		<Row>
			<Column>
				<div class="gradient-cta">
					<h2>Ready to move freight?</h2>
					<p>Post a load in any mode and see your total before you commit.</p>
					<div class="cta-actions">
						{#if auth.isAuthenticated}
							<Button href={auth.homePath} icon={ArrowRight}>Go to your account</Button>
						{:else}
							<Button href="/register" icon={DeliveryTruck}>Register as Carrier</Button>
							<Button href="/register" kind="secondary" icon={Enterprise}>Register as Shipper</Button>
						{/if}
					</div>
				</div>
			</Column>
		</Row>
	</Grid>
</div>

<style>
	.pricing-page {
		padding-bottom: 3rem;
	}
	:global(.formula-tile) {
		margin-bottom: 1rem;
	}
	.formula {
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 0.75rem;
		flex-wrap: wrap;
		font-size: 1.125rem;
		font-weight: 600;
	}
	.f-term {
		padding: 0.4rem 0.85rem;
		border-radius: 4px;
		background: var(--cds-layer-accent, #e0e0e0);
	}
	.f-fee {
		background: #d0e2ff;
	}
	.f-total {
		background: #a7f0ba;
	}
	.f-op {
		color: var(--cds-text-secondary);
		font-size: 1.25rem;
	}
	.formula-note {
		text-align: center;
		color: var(--cds-text-secondary);
		font-size: 0.875rem;
		margin: 0.75rem auto 0;
		max-width: 640px;
	}
	:global(.modes-row) {
		margin-bottom: 1.5rem;
	}
	.mode-card {
		padding: 0.5rem 0;
	}
	.mode-head {
		display: flex;
		align-items: center;
		gap: 0.5rem;
	}
	.mode-head h3 {
		margin: 0;
	}
	.mode-fee {
		display: flex;
		align-items: baseline;
		gap: 0.35rem;
		margin: 0.5rem 0;
	}
	.fee-num {
		font-size: 1.75rem;
		font-weight: 700;
		color: var(--cds-interactive, #0f62fe);
		line-height: 1;
	}
	.fee-label {
		font-size: 0.8125rem;
		color: var(--cds-text-secondary);
	}
	.mode-basis {
		font-size: 0.8125rem;
		font-weight: 600;
		margin: 0 0 0.35rem;
		display: flex;
		align-items: center;
		gap: 0.3rem;
	}
	.mode-tagline {
		color: var(--cds-text-secondary);
		font-size: 0.8125rem;
		line-height: 1.5;
		margin: 0 0 0.75rem;
	}
	.mode-example {
		display: flex;
		flex-direction: column;
		gap: 0.2rem;
		border-top: 1px solid var(--cds-border-subtle, #e0e0e0);
		padding-top: 0.5rem;
		font-size: 0.8125rem;
		color: var(--cds-text-secondary);
	}
	.ex-line {
		display: flex;
		align-items: center;
		gap: 0.3rem;
	}
	.ex-total {
		font-weight: 700;
		color: var(--cds-text-primary);
	}
	.why-pricing {
		display: grid;
		grid-template-columns: repeat(3, 1fr);
		gap: 1rem;
		margin: 1rem 0 2rem;
	}
	.why-item {
		display: flex;
		gap: 0.6rem;
		align-items: flex-start;
	}
	.why-item strong {
		display: block;
		margin-bottom: 0.15rem;
	}
	.why-item p {
		margin: 0;
		font-size: 0.8125rem;
		color: var(--cds-text-secondary);
		line-height: 1.5;
	}
	.cta-actions {
		display: flex;
		gap: 1rem;
		margin-top: 1.5rem;
		flex-wrap: wrap;
		justify-content: center;
	}
	@media (max-width: 672px) {
		.why-pricing {
			grid-template-columns: 1fr;
		}
	}
</style>