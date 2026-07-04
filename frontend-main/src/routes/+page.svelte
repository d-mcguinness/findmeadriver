<script lang="ts">
	import { Grid, Row, Column, Button, Tile } from 'carbon-components-svelte';
	import {
		DeliveryTruck, Enterprise, ArrowRight, Train, Plane, Anchor,
		Money, Partnership, Security, FlowConnection
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
					<h1>Freight, direct.<br /><span class="accent">Every mode. No markup.</span></h1>
					<p class="hero-subtitle">
						FindMeADriver connects shippers straight to self-employed carriers across
						<strong>road, rail, sea and air</strong> — one transparent platform fee per mode,
						shown before you post.
					</p>
					<div class="hero-actions">
						{#if auth.isAuthenticated}
							<Button href={auth.homePath} icon={ArrowRight}>Go to your account</Button>
						{:else}
							<Button href="/register" icon={Enterprise}>Post a load</Button>
							<Button href="/register" kind="secondary" icon={DeliveryTruck}>Find loads to carry</Button>
						{/if}
					</div>
					<ul class="trust">
						<li>Price shown before you post</li>
						<li>No agency markup</li>
						<li>Road · Rail · Sea · Air</li>
						<li>Compliance-aware matching</li>
					</ul>
				</div>
			</Column>
		</Row>

		<!-- Two-sided value -->
		<Row>
			<Column>
				<h2 class="section-heading">Built for both sides of the load</h2>
				<p class="section-sub">
					One marketplace, two ways in — post the freight you need moved, or fill the capacity
					you already have.
				</p>
				<div class="two-sided">
					<div class="fmad-card side-card">
						<Tile>
							<span class="icon-badge"><Enterprise size={24} /></span>
							<h3>I need to move freight</h3>
							<p class="who">For shippers</p>
							<ul class="benefits">
								<li>One platform for road, rail, sea and air</li>
								<li>One transparent fee per mode — shown upfront</li>
								<li>Straight to self-employed, compliance-checked carriers</li>
								<li>Door-to-door intermodal in a single booking</li>
							</ul>
							<Button href="/register" icon={ArrowRight}>Post a load</Button>
						</Tile>
					</div>
					<div class="fmad-card side-card">
						<Tile>
							<span class="icon-badge"><DeliveryTruck size={24} /></span>
							<h3>I have capacity to fill</h3>
							<p class="who">For carriers</p>
							<ul class="benefits">
								<li>Browse loads that fit your modes, licence &amp; hours</li>
								<li>Keep more of the rate — no agency taking a cut</li>
								<li>Matched the moment a load fits your duty clock</li>
								<li>Earn ratings that win you more work</li>
							</ul>
							<Button href="/register" kind="secondary" icon={ArrowRight}>Find loads</Button>
						</Tile>
					</div>
				</div>
			</Column>
		</Row>

		<!-- How it works -->
		<Row>
			<Column>
				<h2 class="section-heading">How it works</h2>
				<div class="steps">
					<div class="step">
						<span class="step-n">1</span>
						<h3>Post or browse</h3>
						<p>Shippers post a load in any mode; carriers browse live matches in seconds.</p>
					</div>
					<div class="step">
						<span class="step-n">2</span>
						<h3>Match</h3>
						<p>We match on mode, licence, lane, available hours and cabotage rules — automatically.</p>
					</div>
					<div class="step">
						<span class="step-n">3</span>
						<h3>Move &amp; get paid</h3>
						<p>The carrier moves the load; you pay one clear fee. No chains, no surprises.</p>
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

		<!-- Why FindMeADriver -->
		<Row>
			<Column>
				<h2 class="section-heading">Why FindMeADriver</h2>
				<div class="why">
					<div class="why-item">
						<span class="icon-badge sm"><Money size={22} /></span>
						<h3>Transparent pricing</h3>
						<p>Every fee is shown before you commit — carrier cost plus one fixed per-mode rate.</p>
					</div>
					<div class="why-item">
						<span class="icon-badge sm"><Partnership size={22} /></span>
						<h3>Direct connection</h3>
						<p>No agencies, no markup, no chains. You deal straight with the carrier.</p>
					</div>
					<div class="why-item">
						<span class="icon-badge sm"><Security size={22} /></span>
						<h3>Compliance built in</h3>
						<p>Cabotage limits, duty-clock hours and mode credentials are checked automatically.</p>
					</div>
					<div class="why-item">
						<span class="icon-badge sm"><FlowConnection size={22} /></span>
						<h3>True intermodal</h3>
						<p>Sequence road, rail, sea and air into one door-to-door movement, priced per leg.</p>
					</div>
				</div>
			</Column>
		</Row>

		<!-- Final CTA -->
		<Row>
			<Column>
				<Tile class="cta-tile">
					<h2>Ready to move freight the direct way?</h2>
					<p>Whether you've got capacity to fill or deliveries that need doing, you're set up in under a minute.</p>
					<div class="cta-actions">
						{#if auth.isAuthenticated}
							<Button href={auth.homePath} icon={ArrowRight}>Go to your account</Button>
						{:else}
							<Button href="/register" icon={Enterprise}>Post a load</Button>
							<Button href="/register" kind="secondary" icon={DeliveryTruck}>Find loads to carry</Button>
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
	.trust {
		list-style: none;
		display: flex;
		justify-content: center;
		flex-wrap: wrap;
		gap: 0.75rem 1.75rem;
		margin: 2rem 0 0;
		padding: 0;
		font-size: 0.85rem;
		color: var(--cds-text-secondary);
	}
	.trust li {
		display: inline-flex;
		align-items: center;
		gap: 0.4rem;
	}
	.trust li::before {
		content: '✓';
		color: #24a148;
		font-weight: 800;
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

	/* ---- Two-sided value cards ---- */
	.two-sided {
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: 1.25rem;
		margin: 1.5rem 0;
	}
	.side-card :global(.bx--tile) {
		display: flex;
		flex-direction: column;
		height: 100%;
		padding: 1.75rem;
	}
	.side-card h3 {
		font-size: 1.25rem;
		margin: 0.85rem 0 0.15rem;
	}
	.side-card .who {
		color: var(--cds-text-secondary);
		font-size: 0.875rem;
		margin: 0 0 1rem;
	}
	.benefits {
		list-style: none;
		padding: 0;
		margin: 0 0 1.5rem;
	}
	.benefits li {
		display: flex;
		gap: 0.55rem;
		padding: 0.4rem 0;
		font-size: 0.95rem;
		line-height: 1.45;
	}
	.benefits li::before {
		content: '→';
		color: var(--fmad-accent, #0f62fe);
		font-weight: 700;
	}
	.side-card :global(.bx--btn) {
		margin-top: auto;
		align-self: flex-start;
	}

	/* ---- How it works ---- */
	.steps {
		display: grid;
		grid-template-columns: repeat(3, 1fr);
		gap: 1.5rem;
		margin: 1.5rem 0;
	}
	.step-n {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		width: 2.5rem;
		height: 2.5rem;
		border-radius: 50%;
		background: var(--fmad-grad, linear-gradient(135deg, #0f62fe, #8a3ffc));
		color: #fff;
		font-weight: 700;
		margin-bottom: 0.85rem;
	}
	.step h3 {
		font-size: 1.05rem;
		margin: 0 0 0.35rem;
	}
	.step p {
		color: var(--cds-text-secondary);
		font-size: 0.92rem;
		line-height: 1.5;
		margin: 0;
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

	/* ---- Why FindMeADriver ---- */
	.why {
		display: grid;
		grid-template-columns: repeat(4, 1fr);
		gap: 1.25rem;
		margin: 1.5rem 0;
	}
	.why-item h3 {
		font-size: 1rem;
		margin: 0.6rem 0 0.3rem;
	}
	.why-item p {
		color: var(--cds-text-secondary);
		font-size: 0.88rem;
		line-height: 1.5;
		margin: 0;
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

	@media (max-width: 900px) {
		.why {
			grid-template-columns: 1fr 1fr;
		}
	}
	@media (max-width: 672px) {
		.hero {
			padding: 2.5rem 1rem;
		}
		.hero h1 {
			font-size: 2rem;
		}
		.two-sided,
		.steps,
		.why {
			grid-template-columns: 1fr;
		}
	}
</style>