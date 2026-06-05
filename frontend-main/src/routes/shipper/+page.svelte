<script lang="ts">
	import { Grid, Row, Column, Button, Tile } from 'carbon-components-svelte';
	import {
		Enterprise,
		Search,
		CheckmarkOutline,
		ConnectionSignal,
		Time,
		ArrowRight
	} from 'carbon-icons-svelte';
</script>

<Grid>
	<Row>
		<Column lg={10} md={6} sm={4}>
			<div class="hero">
				<Enterprise size={48} />
				<h1>Book Qualified Carriers, Directly</h1>
				<p class="hero-subtitle">
					Skip the freight brokers. FindMeADriver gives you direct access to a pool of
					verified multi-modal carriers actively looking for freight to move.
				</p>
				<div class="hero-actions">
					<Button href="/register" icon={ArrowRight}>Register as a Shipper</Button>
					<Button href="/login" kind="secondary">Already have an account?</Button>
				</div>
			</div>
		</Column>
	</Row>

	<Row>
		<Column lg={8} md={8} sm={4}>
			<h2>How It Works</h2>
		</Column>
	</Row>

	<Row>
		<Column lg={4} md={4} sm={4}>
			<Tile>
				<div class="feature-tile">
					<Enterprise size={32} />
					<h3>1. Set Up Your Company</h3>
					<p>
						Create your company profile with industry, size, and location. Let carriers
						know who you are and what freight you ship.
					</p>
				</div>
			</Tile>
		</Column>
		<Column lg={4} md={4} sm={4}>
			<Tile>
				<div class="feature-tile">
					<Search size={32} />
					<h3>2. Browse Carriers</h3>
					<p>
						Search by transport mode, credentials, experience, and location. Filter to find
						exactly the carriers you need.
					</p>
				</div>
			</Tile>
		</Column>
		<Column lg={4} md={4} sm={4}>
			<Tile>
				<div class="feature-tile">
					<ConnectionSignal size={32} />
					<h3>3. Review &amp; Assign</h3>
					<p>
						Compare applications, message carriers, and assign the load — all on the
						platform with no middleman fees.
					</p>
				</div>
			</Tile>
		</Column>
	</Row>

	<Row>
		<Column lg={8} md={8} sm={4}>
			<h2>Why Shippers Choose Us</h2>
		</Column>
	</Row>

	<Row>
		<Column lg={8} md={6} sm={4}>
			<Tile>
				<div class="benefit-list">
					<div class="benefit-item">
						<Time size={24} />
						<div>
							<h4>Faster Booking</h4>
							<p>Cut weeks off your booking timeline by connecting directly with available carriers across every transport mode.</p>
						</div>
					</div>
					<div class="benefit-item">
						<CheckmarkOutline size={24} />
						<div>
							<h4>Verified Profiles</h4>
							<p>Every carrier profile includes supported modes, credentials, and experience — no guesswork.</p>
						</div>
					</div>
					<div class="benefit-item">
						<Search size={24} />
						<div>
							<h4>Targeted Search</h4>
							<p>Filter by road, rail, sea, or air. Find carriers with the exact modes and credentials you need.</p>
						</div>
					</div>
					<div class="benefit-item">
						<Enterprise size={24} />
						<div>
							<h4>Built for Your Industry</h4>
							<p>Whether you ship by road, rail, sea, or air — across any industry — we've got carriers for you.</p>
						</div>
					</div>
				</div>
			</Tile>
		</Column>
		<Column lg={4} md={2} sm={4}>
			<Tile>
				<div class="cta-tile">
					<h3>Ready to find carriers?</h3>
					<p>Create your shipper profile and start browsing qualified carriers across every mode today.</p>
					<Button href="/register" icon={ArrowRight}>Register Now</Button>
				</div>
			</Tile>
		</Column>
	</Row>
</Grid>

<style>
	.benefit-list {
		display: flex;
		flex-direction: column;
		gap: 1.5rem;
	}

	.benefit-item {
		display: flex;
		gap: 1rem;
		align-items: flex-start;
	}

	.benefit-item h4 {
		margin: 0 0 0.25rem;
	}

	.benefit-item p {
		margin: 0;
		color: var(--cds-text-secondary, #525252);
	}

	.cta-tile {
		padding: 1rem 0;
	}

	.cta-tile h3 {
		margin-bottom: 0.75rem;
	}

	.cta-tile p {
		color: var(--cds-text-secondary, #525252);
		margin-bottom: 1.5rem;
	}
</style>
