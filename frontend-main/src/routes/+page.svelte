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
					<h1>Spare Capacity, Every Mode, Put to Work</h1>
					<p class="hero-subtitle">
						FindMeADriver is the spare-hours freight marketplace &mdash; now multimodal. Connect with
						self-employed carriers across <strong>road, rail, sea and air</strong>, and pay one
						transparent platform fee per mode. No agencies, no long-term commitments &mdash; just
						capacity matched to jobs.
					</p>
					<div class="hero-actions">
						{#if auth.isAuthenticated}
							<Button href="/dashboard" icon={ArrowRight}>Go to Dashboard</Button>
						{:else}
							<Button href="/register" icon={DeliveryTruck}>I'm a Driver</Button>
							<Button href="/register" kind="secondary" icon={Enterprise}>I'm an Employer</Button>
						{/if}
					</div>
				</div>
			</Column>
		</Row>

		<!-- Multimodal + transparent pricing -->
		<Row>
			<Column>
				<h2 class="section-heading">One platform. Every mode.</h2>
				<p class="section-sub">
					Post a job by road, rail, sea or air and we match it to the right carrier. You pay the
					carrier's cost plus a single, transparent platform fee that depends only on the mode &mdash;
					shown before you post, with no agency markup.
				</p>
			</Column>
		</Row>
		<Row class="modes-row">
			{#each modes as m}
				{@const Icon = MODE_ICON[m.mode] ?? DeliveryTruck}
				<Column lg={4} md={4} sm={4}>
					<Tile>
						<div class="mode-tile">
							<Icon size={32} />
							<h3>{m.label}</h3>
							<div class="mode-fee">
								<span class="fee-num">{m.commissionPercent}%</span>
								<span class="fee-label">platform fee</span>
							</div>
							<p class="mode-basis">{m.basis}</p>
							<p class="mode-tagline">{m.tagline}</p>
						</div>
					</Tile>
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

		<!-- How It Works: Drivers -->
		<Row>
			<Column>
				<h2 class="section-heading">How It Works for Drivers</h2>
			</Column>
		</Row>
		<Row class="steps-row">
			<Column lg={5} md={4} sm={4}>
				<Tile>
					<div class="step-tile">
						<div class="step-number">1</div>
						<Time size={32} />
						<h3>Set Your Hours</h3>
						<p>Log your available spare hours each week. EU tachograph limits (9h/day, 56h/week, 90h/fortnight) are enforced automatically.</p>
					</div>
				</Tile>
			</Column>
			<Column lg={5} md={4} sm={4}>
				<Tile>
					<div class="step-tile">
						<div class="step-number">2</div>
						<DeliveryTruck size={32} />
						<h3>Get Matched</h3>
						<p>See jobs that fit your CDL type and available time slots. Apply with one click.</p>
					</div>
				</Tile>
			</Column>
			<Column lg={5} md={4} sm={4}>
				<Tile>
					<div class="step-tile">
						<div class="step-number">3</div>
						<Star size={32} />
						<h3>Drive &amp; Get Paid</h3>
						<p>Complete deliveries, earn on your terms, and build your reputation with ratings.</p>
					</div>
				</Tile>
			</Column>
		</Row>

		<!-- How It Works: Employers -->
		<Row>
			<Column>
				<h2 class="section-heading">How It Works for Employers</h2>
			</Column>
		</Row>
		<Row class="steps-row">
			<Column lg={5} md={4} sm={4}>
				<Tile>
					<div class="step-tile">
						<div class="step-number">1</div>
						<Enterprise size={32} />
						<h3>Post a Job</h3>
						<p>Describe your delivery, set the date, duration, and rate. Takes 2 minutes.</p>
					</div>
				</Tile>
			</Column>
			<Column lg={5} md={4} sm={4}>
				<Tile>
					<div class="step-tile">
						<div class="step-number">2</div>
						<CertificateCheck size={32} />
						<h3>Review Drivers</h3>
						<p>See verified, rated drivers who match your requirements and are available on your date.</p>
					</div>
				</Tile>
			</Column>
			<Column lg={5} md={4} sm={4}>
				<Tile>
					<div class="step-tile">
						<div class="step-number">3</div>
						<Checkmark size={32} />
						<h3>Job Done</h3>
						<p>Assign a driver, track progress, mark complete. Pay per delivery, not per day.</p>
					</div>
				</Tile>
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
				<Tile>
					<div class="highlight-tile">
						<Time size={24} />
						<h4>Tachograph Compliant</h4>
						<p>All availability is validated against EU driving time regulations. No risk of overbooking.</p>
					</div>
				</Tile>
			</Column>
			<Column lg={4} md={4} sm={4}>
				<Tile>
					<div class="highlight-tile">
						<CertificateCheck size={24} />
						<h4>Verified Drivers</h4>
						<p>Licence, insurance, and CPC card verification for every driver on the platform.</p>
					</div>
				</Tile>
			</Column>
			<Column lg={4} md={4} sm={4}>
				<Tile>
					<div class="highlight-tile">
						<Star size={24} />
						<h4>Ratings &amp; Reviews</h4>
						<p>Both parties rate each other after every job. Trust is earned, not assumed.</p>
					</div>
				</Tile>
			</Column>
			<Column lg={4} md={4} sm={4}>
				<Tile>
					<div class="highlight-tile">
						<Enterprise size={24} />
						<h4>No Middlemen</h4>
						<p>Direct connection between driver and employer. Transparent rates, no agency markup.</p>
					</div>
				</Tile>
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
							<Button href="/register" icon={DeliveryTruck}>Register as Driver</Button>
							<Button href="/register" kind="secondary" icon={Enterprise}>Register as Employer</Button>
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
	.hero {
		padding: 3rem 0 2rem;
		text-align: center;
	}
	.hero h1 {
		font-size: 2.5rem;
		font-weight: 600;
		margin-bottom: 1rem;
		line-height: 1.2;
	}
	.hero-subtitle {
		font-size: 1.125rem;
		color: var(--cds-text-secondary);
		margin-bottom: 2rem;
		max-width: 600px;
		margin-left: auto;
		margin-right: auto;
		line-height: 1.6;
	}
	.hero-actions {
		display: flex;
		justify-content: center;
		gap: 1rem;
		flex-wrap: wrap;
	}
	.section-heading {
		font-size: 1.5rem;
		font-weight: 600;
		margin: 2.5rem 0 1rem;
	}
	.section-sub {
		color: var(--cds-text-secondary);
		font-size: 1rem;
		line-height: 1.6;
		max-width: 760px;
		margin: -0.5rem 0 1.25rem;
	}
	.modes-row {
		margin-bottom: 1rem;
	}
	.mode-tile {
		padding: 0.5rem 0;
		text-align: center;
	}
	.mode-tile h3 {
		margin: 0.5rem 0 0.5rem;
	}
	.mode-fee {
		display: flex;
		align-items: baseline;
		justify-content: center;
		gap: 0.35rem;
		margin-bottom: 0.5rem;
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
		padding: 0.75rem 1rem;
		font-size: 0.9375rem;
		margin-bottom: 1rem;
	}
	.pricing-cta span {
		flex: 1 1 auto;
		min-width: 12rem;
	}
	.steps-row {
		margin-bottom: 1rem;
	}
	.step-tile {
		padding: 1rem 0;
		text-align: center;
	}
	.step-tile h3 {
		margin: 0.75rem 0 0.5rem;
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
		width: 2rem;
		height: 2rem;
		border-radius: 50%;
		background: var(--cds-interactive);
		color: white;
		font-weight: 700;
		font-size: 0.875rem;
		margin-bottom: 0.75rem;
	}
	.highlights-row {
		margin-bottom: 2rem;
	}
	.highlight-tile {
		padding: 0.5rem 0;
	}
	.highlight-tile h4 {
		margin: 0.5rem 0 0.25rem;
	}
	.highlight-tile p {
		color: var(--cds-text-secondary);
		font-size: 0.875rem;
		line-height: 1.5;
	}
	.cta-actions {
		display: flex;
		gap: 1rem;
		margin-top: 1.5rem;
		flex-wrap: wrap;
	}
	@media (max-width: 672px) {
		.hero h1 {
			font-size: 1.75rem;
		}
	}
</style>
