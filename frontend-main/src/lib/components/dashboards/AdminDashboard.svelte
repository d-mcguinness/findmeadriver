<script lang="ts">
	import { Grid, Row, Column, ClickableTile } from 'carbon-components-svelte';
	import { UserAdmin, UserMultiple, Van, Document, Settings } from 'carbon-icons-svelte';
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
			<ClickableTile class="dashboard-tile" href="/dashboard/users">
				<div class="feature-tile">
					<UserAdmin size={32} />
					<h3>Manage Users</h3>
					<p>View, edit, and manage all registered drivers and employers.</p>
				</div>
			</ClickableTile>
		</Column>
		<Column lg={5} md={4} sm={4}>
			<ClickableTile class="dashboard-tile" href="/dashboard/analytics#users">
				<div class="feature-tile">
					<UserMultiple size={32} />
					<h3>User Analytics</h3>
					<p>Registration breakdown by driver, employer, and admin accounts.</p>
				</div>
			</ClickableTile>
		</Column>
		<Column lg={5} md={4} sm={4}>
			<ClickableTile class="dashboard-tile" href="/dashboard/analytics#jobs">
				<div class="feature-tile">
					<Van size={32} />
					<h3>Job Analytics</h3>
					<p>All posted jobs with status, applications, and route details.</p>
				</div>
			</ClickableTile>
		</Column>
		<Column lg={5} md={4} sm={4}>
			<ClickableTile class="dashboard-tile" href="/dashboard/analytics#documents">
				<div class="feature-tile">
					<Document size={32} />
					<h3>Document Compliance</h3>
					<p>Review pending licences, insurance, CPC and tachograph cards.</p>
				</div>
			</ClickableTile>
		</Column>
		<Column lg={5} md={4} sm={4}>
			<ClickableTile class="dashboard-tile" href="/dashboard/settings">
				<div class="feature-tile">
					<Settings size={32} />
					<h3>Settings</h3>
					<p>Configure platform settings and manage roles.</p>
				</div>
			</ClickableTile>
		</Column>
	</Row>
</Grid>
