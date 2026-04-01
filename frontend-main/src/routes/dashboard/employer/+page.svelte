<script lang="ts">
	import { Grid, Row, Column, Tile } from 'carbon-components-svelte';
	import { Enterprise, Add, Search, Chat } from 'carbon-icons-svelte';
	import { auth } from '$lib/stores/auth.svelte';
</script>

<Grid>
	<Row>
		<Column>
			<h1>Welcome, {auth.user?.firstName}!</h1>
			<p class="dashboard-subtitle">Your employer dashboard</p>
		</Column>
	</Row>

	<Row>
		<Column lg={4} md={4} sm={4}>
			<Tile class="dashboard-tile">
				<div class="feature-tile">
					<Enterprise size={32} />
					<h3>Company Profile</h3>
					<p>Manage your company information and visibility to drivers.</p>
				</div>
			</Tile>
		</Column>
		<Column lg={4} md={4} sm={4}>
			<Tile class="dashboard-tile">
				<div class="feature-tile">
					<Add size={32} />
					<h3>Post a Job</h3>
					<p>Create a new job listing to attract qualified drivers.</p>
				</div>
			</Tile>
		</Column>
		<Column lg={4} md={4} sm={4}>
			<Tile class="dashboard-tile">
				<div class="feature-tile">
					<Search size={32} />
					<h3>Browse Drivers</h3>
					<p>Search for drivers by CDL type, experience, and location.</p>
				</div>
			</Tile>
		</Column>
		<Column lg={4} md={4} sm={4}>
			<Tile class="dashboard-tile">
				<div class="feature-tile">
					<Chat size={32} />
					<h3>Messages</h3>
					<p>Communicate directly with interested drivers.</p>
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
