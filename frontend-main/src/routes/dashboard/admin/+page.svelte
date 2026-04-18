<script lang="ts">
	import { Grid, Row, Column, ClickableTile } from 'carbon-components-svelte';
	import { UserAdmin, Analytics, Settings } from 'carbon-icons-svelte';
	import { auth } from '$lib/stores/auth.svelte';
</script>

<Grid>
	<Row>
		<Column>
			<h1>Admin Panel</h1>
			<p class="dashboard-subtitle">Welcome, {auth.user?.firstName}</p>
		</Column>
	</Row>

	<Row>
		<Column lg={5} md={4} sm={4}>
			<ClickableTile class="dashboard-tile" href="/dashboard/admin/users">
				<div class="feature-tile">
					<UserAdmin size={32} />
					<h3>Manage Users</h3>
					<p>View, edit, and manage all registered drivers and employers.</p>
				</div>
			</ClickableTile>
		</Column>
		<Column lg={5} md={4} sm={4}>
			<ClickableTile class="dashboard-tile" href="/dashboard/admin/analytics">
				<div class="feature-tile">
					<Analytics size={32} />
					<h3>Platform Analytics</h3>
					<p>View registration trends, job postings, and platform usage.</p>
				</div>
			</ClickableTile>
		</Column>
		<Column lg={5} md={4} sm={4}>
			<ClickableTile class="dashboard-tile" href="/dashboard/admin/settings">
				<div class="feature-tile">
					<Settings size={32} />
					<h3>Settings</h3>
					<p>Configure platform settings and manage roles.</p>
				</div>
			</ClickableTile>
		</Column>
	</Row>
</Grid>
