<script lang="ts">
	import { Grid, Row, Column, Button, Tile } from 'carbon-components-svelte';
	import { DeliveryTruck, Enterprise, ArrowRight, Train, Plane, Anchor } from 'carbon-icons-svelte';
	import { auth } from '$lib/stores/auth.svelte';
	import { onMount } from 'svelte';
	import { loadModePricing, FALLBACK_MODE_PRICING, type ModePricing } from '$lib/pricing';

	const MODE_ICON: Record<string, typeof DeliveryTruck> = {
		ROAD: DeliveryTruck,
		RAIL: Train,
		OCEAN: Anchor,
		AIR: Plane
	};

	// Per-mode accent colours (Carbon palette) so the four modes read as distinct.
	const MODE_ACCENT: Record<string, string> = {
		ROAD: '#24a148',
		RAIL: '#007d79',
		OCEAN: '#0072c3',
		AIR: '#8a3ffc'
	};

	// Renders instantly from the fallback, then swaps in live rates from the API.
	let modes = $state<ModePricing[]>(FALLBACK_MODE_PRICING);
	onMount(async () => {
		modes = await loadModePricing();
	});
</script>

<div class="landing">
	<Grid>
		<!-- Hero -->
		<Row>
			<Column>
				<div class="hero">
					<span class="eyebrow">Multimodal freight marketplace</span>
					<h1>Spare Capacity, Every Mode, <span class="accent">Put to Work</span></h1>
					<p class="hero-subtitle">
						Connect directly with self-employed carriers across
						<strong>road, rail, sea and air</strong> — and pay one transparent platform fee per
						mode. No agencies, no markup.
					</p>
					<div class="hero-actions">
						{#if auth.isAuthenticated}
							<Button href={auth.homePath} icon={ArrowRight}>Go to your account</Button>
						{:else}
							<Button href="/register" icon={DeliveryTruck}>I'm a Carrier</Button>
							<Button href="/register" kind="secondary" icon={Enterprise}>I'm a Shipper</Button>
						{/if}
					</div>
				</div>
			</Column>
		</Row>

		<!-- The differentiator: transparent, per-mode pricing -->
		<Row>
			<Column>
				<h2 class="section-heading">One platform. Every mode.</h2>
				<p class="section-sub">
					You pay the carrier's cost plus one transparent platform fee that depends only on the
					mode — shown before you post, with no agency markup.
				</p>
			</Column>
		</Row>
		<Row class="modes-row">
			{#each modes as m}
				{@const Icon = MODE_ICON[m.mode] ?? DeliveryTruck}
				<Column lg={4} md={4} sm={4}>
					<div class="card mode-card" style="--accent: {MODE_ACCENT[m.mode] ?? '#0f62fe'}">
						<Tile>
							<div class="mode-tile">
								<span class="mode-icon"><Icon size={32} /></span>
								<h3>{m.label}</h3>
								<div class="mode-fee">
									<span class="fee-num">{m.commissionPercent}%</span>
									<span class="fee-label">platform fee</span>
								</div>
								<p class="mode-basis">{m.basis}</p>
								<p class="mode-tagline">{m.tagline}</p>
							</div>
						</Tile>
					</div>
				</Column>
			{/each}
		</Row>
		<Row>
			<Column>
				<div class="pricing-link">
					<Button kind="ghost" size="small" href="/pricing" icon={ArrowRight}>See full pricing</Button>
				</div>
			</Column>
		</Row>

		<!-- Final CTA -->
		<Row>
			<Column>
				<Tile class="cta-tile">
					<h2>Ready to get started?</h2>
					<p>Whether you have spare capacity to fill or deliveries that need doing, sign up in under a minute.</p>
					<div class="cta-actions">
						{#if auth.isAuthenticated}
							<Button href={auth.homePath} icon={ArrowRight}>Go to your account</Button>
						{:else}
							<Button href="/register" icon={DeliveryTruck}>Register as Carrier</Button>
							<Button href="/register" kind="secondary" icon={Enterprise}>Register as Shipper</Button>
						{/if}
					</div>
				</Tile>
			</Column>
		</Row>
	</Grid>
</div>

<style>
	.landing {
		padding-bottom: 3rem;
	}

	/* ---- Hero ---- */
	.hero {
		text-align: center;
		padding: 4rem 1.5rem 3.5rem;
		margin: 0.5rem 0 1rem;
		border-radius: 16px;
		background:
			radial-gradient(60rem 30rem at 50% -10%, #d0e2ff 0%, rgba(208, 226, 255, 0) 60%),
			linear-gradient(160deg, #edf5ff 0%, #ffffff 55%);
		border: 1px solid var(--cds-border-subtle, #e0e0e0);
	}
	.eyebrow {
		display: inline-block;
		background: #d0e2ff;
		color: #0043ce;
		padding: 0.3rem 0.85rem;
		border-radius: 999px;
		font-size: 0.75rem;
		font-weight: 600;
		letter-spacing: 0.06em;
		text-transform: uppercase;
		margin-bottom: 1.25rem;
	}
	.hero h1 {
		font-size: 3rem;
		font-weight: 600;
		margin-bottom: 1rem;
		line-height: 1.15;
		letter-spacing: -0.01em;
	}
	.hero h1 .accent {
		background: linear-gradient(90deg, #0f62fe 0%, #8a3ffc 100%);
		-webkit-background-clip: text;
		background-clip: text;
		color: transparent;
	}
	.hero-subtitle {
		font-size: 1.125rem;
		color: var(--cds-text-secondary);
		margin: 0 auto 2rem;
		max-width: 620px;
		line-height: 1.6;
	}
	.hero-actions {
		display: flex;
		justify-content: center;
		gap: 1rem;
		flex-wrap: wrap;
	}

	/* ---- Section heading ---- */
	.section-heading {
		font-size: 1.625rem;
		font-weight: 600;
		margin: 3rem 0 1rem;
		letter-spacing: -0.01em;
	}
	.section-heading::after {
		content: '';
		display: block;
		width: 2.5rem;
		height: 3px;
		border-radius: 2px;
		background: linear-gradient(90deg, #0f62fe, #8a3ffc);
		margin-top: 0.6rem;
	}
	.section-sub {
		color: var(--cds-text-secondary);
		font-size: 1rem;
		line-height: 1.6;
		max-width: 680px;
		margin: 0 0 1.5rem;
	}

	/* ---- Mode pricing cards ---- */
	:global(.modes-row) {
		margin-bottom: 0.5rem;
	}
	.card {
		height: 100%;
		transition: transform 160ms ease, box-shadow 160ms ease;
	}
	.card :global(.bx--tile) {
		height: 100%;
	}
	.card:hover {
		transform: translateY(-4px);
		box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
	}
	.mode-card {
		border-top: 3px solid var(--accent);
	}
	.mode-tile {
		padding: 0.5rem 0;
		text-align: center;
	}
	.mode-icon {
		display: inline-flex;
		color: var(--accent);
	}
	.mode-icon :global(svg) {
		fill: currentColor;
	}
	.mode-tile h3 {
		margin: 0.5rem 0;
	}
	.mode-fee {
		display: flex;
		align-items: baseline;
		justify-content: center;
		gap: 0.35rem;
		margin-bottom: 0.5rem;
	}
	.fee-num {
		font-size: 1.875rem;
		font-weight: 700;
		color: var(--accent);
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
	}
	.mode-tagline {
		color: var(--cds-text-secondary);
		font-size: 0.8125rem;
		line-height: 1.5;
		margin: 0;
	}
	.pricing-link {
		text-align: center;
		margin-bottom: 1rem;
	}

	/* ---- Final CTA ---- */
	:global(.cta-tile) {
		text-align: center;
		padding: 3.25rem 1.5rem;
		border-radius: 16px;
		background: linear-gradient(135deg, #0f62fe 0%, #6929c4 100%);
		color: #fff;
	}
	:global(.cta-tile h2) {
		font-size: 1.875rem;
		font-weight: 600;
		margin-bottom: 0.5rem;
		color: #fff;
	}
	:global(.cta-tile p) {
		color: rgba(255, 255, 255, 0.9);
		font-size: 1.0625rem;
		max-width: 560px;
		margin: 0 auto;
	}
	.cta-actions {
		display: flex;
		gap: 1rem;
		margin-top: 1.75rem;
		justify-content: center;
		flex-wrap: wrap;
	}

	@media (max-width: 672px) {
		.hero {
			padding: 2.5rem 1rem;
		}
		.hero h1 {
			font-size: 2rem;
		}
	}
</style>
