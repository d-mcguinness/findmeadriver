<script lang="ts">
	import { onMount } from 'svelte';
	import { goto } from '$app/navigation';
	import { auth } from '$lib/stores/auth.svelte';
	import { Loading } from 'carbon-components-svelte';

	let { children } = $props();
	let ready = $state(false);

	onMount(() => {
		if (!auth.isAuthenticated) {
			goto('/login');
		} else {
			ready = true;
		}
	});
</script>

{#if ready}
	{@render children()}
{:else}
	<Loading withOverlay={false} description="Checking authentication..." />
{/if}
