<script lang="ts">
	import { auth } from '$lib/stores/auth.svelte';
	import AdminDashboard from '$lib/components/dashboards/AdminDashboard.svelte';
	import DriverDashboard from '$lib/components/dashboards/DriverDashboard.svelte';
	import EmployerDashboard from '$lib/components/dashboards/EmployerDashboard.svelte';
</script>

{#if auth.isAdmin}
	<AdminDashboard />
{:else if auth.isEmployer}
	<EmployerDashboard />
{:else if auth.isDriver}
	<DriverDashboard />
{:else}
	<p>No dashboard available for your account.</p>
{/if}
