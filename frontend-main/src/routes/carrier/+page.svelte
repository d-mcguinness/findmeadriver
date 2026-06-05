<script lang="ts">
	import { Grid, Row, Column, Button, Tile } from 'carbon-components-svelte';
	import {
		DeliveryTruck,
		Search,
		CheckmarkOutline,
		ConnectionSignal,
		Currency,
		ArrowRight
	} from 'carbon-icons-svelte';
</script>

<Grid>
	<Row>
		<Column lg={10} md={6} sm={4}>
			<div class="hero">
				<DeliveryTruck size={48} />
				<h1>Move Freight on Your Terms</h1>
				<p class="hero-subtitle">
					FindMeADriver connects you directly with shippers looking for qualified carriers across
					road, rail, sea and air. No recruiters, no fees — just real loads matched to your modes.
				</p>
				<div class="hero-actions">
					<Button href="/register" icon={ArrowRight}>Register as a Carrier</Button>
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
					<CheckmarkOutline size={32} />
					<h3>1. Create Your Profile</h3>
					<p>
						Sign up with your supported modes, credentials, endorsements, years of experience, and
						preferred work area. Your profile is your resume.
					</p>
				</div>
			</Tile>
		</Column>
		<Column lg={4} md={4} sm={4}>
			<Tile>
				<div class="feature-tile">
					<Search size={32} />
					<h3>2. Get Matched</h3>
					<p>
						Shippers search for carriers that fit their needs. When your profile matches,
						you'll see their load listings right in your dashboard.
					</p>
				</div>
			</Tile>
		</Column>
		<Column lg={4} md={4} sm={4}>
			<Tile>
				<div class="feature-tile">
					<ConnectionSignal size={32} />
					<h3>3. Connect Directly</h3>
					<p>
						Message shippers, schedule interviews, and negotiate terms — all without
						a middleman taking a cut.
					</p>
				</div>
			</Tile>
		</Column>
	</Row>

	<Row>
		<Column lg={8} md={8} sm={4}>
			<h2>Why Carriers Choose Us</h2>
		</Column>
	</Row>

	<Row>
		<Column lg={8} md={6} sm={4}>
			<Tile>
				<div class="benefit-list">
					<div class="benefit-item">
						<Currency size={24} />
						<div>
							<h4>No Fees, Ever</h4>
							<p>FindMeADriver is completely free for carriers. We make money from shipper subscriptions, not from you.</p>
						</div>
					</div>
					<div class="benefit-item">
						<DeliveryTruck size={24} />
						<div>
							<h4>Multi-Modal</h4>
							<p>Built for carriers across every mode — road, rail, sea and air. We understand your industry.</p>
						</div>
					</div>
					<div class="benefit-item">
						<Search size={24} />
						<div>
							<h4>Loads on Your Lanes</h4>
							<p>Set your preferred lanes and only see loads that fit your routes.</p>
						</div>
					</div>
					<div class="benefit-item">
						<CheckmarkOutline size={24} />
						<div>
							<h4>Verified Shippers</h4>
							<p>Every shipper on the platform is verified so you know you're talking to real companies.</p>
						</div>
					</div>
				</div>
			</Tile>
		</Column>
		<Column lg={4} md={2} sm={4}>
			<Tile>
				<div class="cta-tile">
					<h3>Ready to get started?</h3>
					<p>Create your profile in under 5 minutes and start seeing matched loads today.</p>
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
