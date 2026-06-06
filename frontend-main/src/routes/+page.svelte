<script lang="ts">
	import { Grid, Row, Column, Button, Tile } from 'carbon-components-svelte';
	import {
		DeliveryTruck, Enterprise, ArrowRight, Time, Checkmark, Star, CertificateCheck,
		Train, Plane, Anchor, ChartLineData
	} from 'carbon-icons-svelte';
	import { auth } from '$lib/stores/auth.svelte';
	import { onMount } from 'svelte';
	import { loadModePricing, FALLBACK_MODE_PRICING, type ModePricing } from '$lib/pricing';

	const MODE_ICON: Record<string, typeof DeliveryTruck> = {
		ROAD: DeliveryTruck,
		RAIL: Train,
		OCEAN: Anchor,
		AIR: Plane
	};

	// Per-mode accent colours (Carbon palette) — used for the pricing-card top rule
	// and the hero mode chips so the four modes read as distinct at a glance.
	const MODE_ACCENT: Record<string, string> = {
		ROAD: '#24a148',
		RAIL: '#007d79',
		OCEAN: '#0072c3',
		AIR: '#8a3ffc'
	};

	const HERO_MODES = [
		{ key: 'ROAD', label: 'Road' },
		{ key: 'RAIL', label: 'Rail' },
		{ key: 'OCEAN', label: 'Sea' },
		{ key: 'AIR', label: 'Air' }
	];

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
						FindMeADriver is the spare-hours freight marketplace &mdash; now multimodal. Connect with
						self-employed carriers across <strong>road, rail, sea and air</strong>, and pay one
						transparent platform fee per mode. No agencies, no long-term commitments &mdash; just
						capacity matched to loads.
					</p>
					<div class="hero-actions">
						{#if auth.isAuthenticated}
							<Button href="/dashboard" icon={ArrowRight}>Go to Dashboard</Button>
						{:else}
							<Button href="/register" icon={DeliveryTruck}>I'm a Carrier</Button>
							<Button href="/register" kind="secondary" icon={Enterprise}>I'm a Shipper</Button>
						{/if}
					</div>
					<div class="hero-modes">
						{#each HERO_MODES as hm}
							{@const Icon = MODE_ICON[hm.key] ?? DeliveryTruck}
							<span class="hero-mode" style="--accent: {MODE_ACCENT[hm.key]}">
								<span class="hero-mode-icon"><Icon size={20} /></span>
								{hm.label}
							</span>
						{/each}
					</div>
				</div>
			</Column>
		</Row>

		<!-- Multimodal + transparent pricing -->
		<Row>
			<Column>
				<h2 class="section-heading">One platform. Every mode.</h2>
				<p class="section-sub">
					Post a load by road, rail, sea or air and we match it to the right carrier. You pay the
					carrier's cost plus a single, transparent platform fee that depends only on the mode &mdash;
					shown before you post, with no agency markup.
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
				<div class="pricing-cta">
					<ChartLineData size={20} />
					<span>Transparent, mode-based pricing &mdash; you always see the platform fee before you post.</span>
					<Button kind="ghost" size="small" href="/pricing" icon={ArrowRight}>See full pricing</Button>
				</div>
			</Column>
		</Row>

		<!-- How It Works: Carriers -->
		<Row>
			<Column>
				<h2 class="section-heading">How It Works for Carriers</h2>
			</Column>
		</Row>
		<Row class="steps-row">
			<Column lg={5} md={4} sm={4}>
				<div class="card">
					<Tile>
						<div class="step-tile">
							<div class="step-number">1</div>
							<span class="icon-badge"><Time size={24} /></span>
							<h3>Set Your Capacity</h3>
							<p>Declare the modes you cover and your weekly capacity. For road, EU tachograph limits (9h/day, 56h/week, 90h/fortnight) are enforced automatically.</p>
						</div>
					</Tile>
				</div>
			</Column>
			<Column lg={5} md={4} sm={4}>
				<div class="card">
					<Tile>
						<div class="step-tile">
							<div class="step-number">2</div>
							<span class="icon-badge"><DeliveryTruck size={24} /></span>
							<h3>Get Matched</h3>
							<p>See loads that fit your modes, credentials and available time slots. Apply with one click.</p>
						</div>
					</Tile>
				</div>
			</Column>
			<Column lg={5} md={4} sm={4}>
				<div class="card">
					<Tile>
						<div class="step-tile">
							<div class="step-number">3</div>
							<span class="icon-badge"><Star size={24} /></span>
							<h3>Carry &amp; Get Paid</h3>
							<p>Complete deliveries, earn on your terms, and build your reputation with ratings.</p>
						</div>
					</Tile>
				</div>
			</Column>
		</Row>

		<!-- How It Works: Shippers -->
		<Row>
			<Column>
				<h2 class="section-heading">How It Works for Shippers</h2>
			</Column>
		</Row>
		<Row class="steps-row">
			<Column lg={5} md={4} sm={4}>
				<div class="card">
					<Tile>
						<div class="step-tile">
							<div class="step-number">1</div>
							<span class="icon-badge"><Enterprise size={24} /></span>
							<h3>Post a Load</h3>
							<p>Describe your delivery, set the date, duration, and rate. Takes 2 minutes.</p>
						</div>
					</Tile>
				</div>
			</Column>
			<Column lg={5} md={4} sm={4}>
				<div class="card">
					<Tile>
						<div class="step-tile">
							<div class="step-number">2</div>
							<span class="icon-badge"><CertificateCheck size={24} /></span>
							<h3>Review Carriers</h3>
							<p>See verified, rated carriers who match your requirements and are available on your date.</p>
						</div>
					</Tile>
				</div>
			</Column>
			<Column lg={5} md={4} sm={4}>
				<div class="card">
					<Tile>
						<div class="step-tile">
							<div class="step-number">3</div>
							<span class="icon-badge"><Checkmark size={24} /></span>
							<h3>Load Done</h3>
							<p>Assign a carrier, track progress, mark complete. Pay per delivery, not per day.</p>
						</div>
					</Tile>
				</div>
			</Column>
		</Row>

		<!-- Platform Highlights -->
		<Row>
			<Column>
				<h2 class="section-heading">Why FindMeADriver</h2>
			</Column>
		</Row>
		<Row class="highlights-row">
			<Column lg={4} md={4} sm={4}>
				<div class="card">
					<Tile>
						<div class="highlight-tile">
							<span class="icon-badge sm"><Time size={20} /></span>
							<h4>Compliance Built In</h4>
							<p>Road availability is validated against EU driving time regulations, and credentials are checked per mode. No risk of overbooking.</p>
						</div>
					</Tile>
				</div>
			</Column>
			<Column lg={4} md={4} sm={4}>
				<div class="card">
					<Tile>
						<div class="highlight-tile">
							<span class="icon-badge sm"><CertificateCheck size={20} /></span>
							<h4>Verified Carriers</h4>
							<p>Licence, insurance, and mode credential verification for every carrier on the platform.</p>
						</div>
					</Tile>
				</div>
			</Column>
			<Column lg={4} md={4} sm={4}>
				<div class="card">
					<Tile>
						<div class="highlight-tile">
							<span class="icon-badge sm"><Star size={20} /></span>
							<h4>Ratings &amp; Reviews</h4>
							<p>Both parties rate each other after every load. Trust is earned, not assumed.</p>
						</div>
					</Tile>
				</div>
			</Column>
			<Column lg={4} md={4} sm={4}>
				<div class="card">
					<Tile>
						<div class="highlight-tile">
							<span class="icon-badge sm"><Enterprise size={20} /></span>
							<h4>No Middlemen</h4>
							<p>Direct connection between carrier and shipper. Transparent rates, no agency markup.</p>
						</div>
					</Tile>
				</div>
			</Column>
		</Row>

		<!-- Final CTA -->
		<Row>
			<Column>
				<Tile class="cta-tile">
					<h2>Ready to get started?</h2>
					<p>Whether you have spare hours to fill or deliveries that need doing, sign up in under a minute.</p>
					<div class="cta-actions">
						{#if auth.isAuthenticated}
							<Button href="/dashboard" icon={ArrowRight}>Go to Dashboard</Button>
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
	.hero-modes {
		display: flex;
		justify-content: center;
		gap: 0.5rem;
		flex-wrap: wrap;
		margin-top: 2.25rem;
	}
	.hero-mode {
		display: inline-flex;
		align-items: center;
		gap: 0.4rem;
		background: #fff;
		border: 1px solid var(--cds-border-subtle, #e0e0e0);
		border-radius: 999px;
		padding: 0.4rem 0.9rem;
		font-size: 0.8125rem;
		font-weight: 600;
		box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
	}
	.hero-mode-icon {
		display: inline-flex;
		color: var(--accent);
	}
	.hero-mode-icon :global(svg) {
		fill: currentColor;
	}

	/* ---- Section headings ---- */
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
		max-width: 760px;
		margin: 0 0 1.5rem;
	}

	/* ---- Card lift (shared by mode / step / highlight tiles) ---- */
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

	/* ---- Mode pricing cards ---- */
	:global(.modes-row) {
		margin-bottom: 1rem;
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
	.pricing-cta {
		display: flex;
		align-items: center;
		gap: 0.75rem;
		flex-wrap: wrap;
		background: var(--cds-layer, #f4f4f4);
		border-left: 3px solid var(--cds-support-success, #24a148);
		border-radius: 0 8px 8px 0;
		padding: 0.85rem 1.1rem;
		font-size: 0.9375rem;
		margin-bottom: 1rem;
	}
	.pricing-cta span {
		flex: 1 1 auto;
		min-width: 12rem;
	}

	/* ---- Icon badge (steps + highlights) ---- */
	.icon-badge {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		width: 3.25rem;
		height: 3.25rem;
		border-radius: 50%;
		background: linear-gradient(135deg, #edf5ff 0%, #e8defc 100%);
		color: #0f62fe;
	}
	.icon-badge.sm {
		width: 2.75rem;
		height: 2.75rem;
	}
	.icon-badge :global(svg) {
		fill: currentColor;
	}

	/* ---- Step tiles ---- */
	:global(.steps-row) {
		margin-bottom: 1rem;
	}
	.step-tile {
		padding: 1.25rem 0.5rem;
		text-align: center;
		position: relative;
	}
	.step-tile h3 {
		margin: 0.85rem 0 0.5rem;
	}
	.step-tile p {
		color: var(--cds-text-secondary);
		font-size: 0.875rem;
		line-height: 1.5;
	}
	.step-number {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		width: 1.75rem;
		height: 1.75rem;
		border-radius: 50%;
		background: linear-gradient(135deg, #0f62fe, #8a3ffc);
		color: white;
		font-weight: 700;
		font-size: 0.8125rem;
		margin-bottom: 0.85rem;
	}

	/* ---- Highlight tiles ---- */
	:global(.highlights-row) {
		margin-bottom: 2.5rem;
	}
	.highlight-tile {
		padding: 0.75rem 0.25rem;
	}
	.highlight-tile h4 {
		margin: 0.75rem 0 0.35rem;
	}
	.highlight-tile p {
		color: var(--cds-text-secondary);
		font-size: 0.875rem;
		line-height: 1.5;
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
