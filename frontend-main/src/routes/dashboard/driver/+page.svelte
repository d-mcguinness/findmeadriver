<script lang="ts">
	import { Grid, Row, Column, Tile } from 'carbon-components-svelte';
	import { DeliveryTruck, Search, Document, Chat } from 'carbon-icons-svelte';
	import { auth } from '$lib/stores/auth.svelte';
</script>

<Grid>
	<Row>
		<Column>
			<h1>Welcome, {auth.user?.firstName}!</h1>
			<p class="dashboard-subtitle">Your driver dashboard</p>
		</Column>
	</Row>

	<Row>
		<Column lg={4} md={4} sm={4}>
			<Tile class="dashboard-tile">
				<div class="feature-tile">
					<DeliveryTruck size={32} />
					<h3>My Profile</h3>
					<p>View and update your driver profile, license info, and endorsements.</p>
				</div>
			</Tile>
		</Column>
		<Column lg={4} md={4} sm={4}>
			<Tile class="dashboard-tile">
				<div class="feature-tile">
					<Search size={32} />
					<h3>Job Matches</h3>
					<p>Browse jobs that match your CDL type, experience, and location.</p>
				</div>
			</Tile>
		</Column>
		<Column lg={4} md={4} sm={4}>
			<Tile class="dashboard-tile">
				<div class="feature-tile">
					<Document size={32} />
					<h3>Applications</h3>
					<p>Track the status of your job applications.</p>
				</div>
			</Tile>
		</Column>
		<Column lg={4} md={4} sm={4}>
			<Tile class="dashboard-tile">
				<div class="feature-tile">
					<Chat size={32} />
					<h3>Messages</h3>
					<p>Communicate directly with potential employers.</p>
				</div>
			</Tile>
		</Column>
	</Row>

	<Row>
		<Column>
			<Tile>
				<h3>Your Info</h3>
				<p><strong>Email:</strong> {auth.user?.email}</p>
				<p><strong>Name:</strong> {auth.user?.firstName} {auth.user?.lastName}</p>
				<p><strong>Phone:</strong> {auth.user?.phone || 'Not set'}</p>
			</Tile>
		</Column>
	</Row>
</Grid>
