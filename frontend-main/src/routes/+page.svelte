<script lang="ts">
	import { Grid, Row, Column, Button, Tile, ClickableTile } from 'carbon-components-svelte';
	import { DeliveryTruck, Enterprise, ConnectionSignal, ArrowRight } from 'carbon-icons-svelte';
	import { auth } from '$lib/stores/auth.svelte';
</script>

<div class="landing">
	<Grid>
		<Row>
			<Column lg={10} md={6} sm={4}>
				<div class="hero">
					<h1>Connect Drivers with Employers, Directly</h1>
					<p class="hero-subtitle">
						Driver Direct is the platform that brings qualified commercial drivers and employers
						together — no middlemen, no hassle.
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

		<Row class="features-row">
			<Column lg={5} md={4} sm={4}>
				<Tile>
					<div class="feature-tile">
						<DeliveryTruck size={32} />
						<h3>Find CDL Jobs Fast</h3>
						<p>Browse opportunities that match your license type, experience, and preferred radius.</p>
					</div>
				</Tile>
			</Column>
			<Column lg={5} md={4} sm={4}>
				<Tile>
					<div class="feature-tile">
						<Enterprise size={32} />
						<h3>Hire Qualified Drivers</h3>
						<p>Access a pool of verified drivers with the endorsements and experience you need.</p>
					</div>
				</Tile>
			</Column>
			<Column lg={5} md={4} sm={4}>
				<Tile>
					<div class="feature-tile">
						<ConnectionSignal size={32} />
						<h3>Direct Communication</h3>
						<p>Connect directly with candidates or employers — no recruiters in the way.</p>
					</div>
				</Tile>
			</Column>
		</Row>
	</Grid>
</div>
