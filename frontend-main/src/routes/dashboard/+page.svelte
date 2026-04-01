<script lang="ts">
	import { onMount } from 'svelte';
	import { goto } from '$app/navigation';
	import { auth } from '$lib/stores/auth.svelte';

	onMount(() => {
		if (auth.isAdmin) {
			goto('/dashboard/admin');
		} else if (auth.isEmployer) {
			goto('/dashboard/employer');
		} else if (auth.isDriver) {
			goto('/dashboard/driver');
		}
	});
</script>

<p>Redirecting to your dashboard...</p>
