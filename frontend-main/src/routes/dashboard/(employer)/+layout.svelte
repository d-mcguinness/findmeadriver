<script lang="ts">
	import { onMount } from 'svelte';
	import { goto } from '$app/navigation';
	import { auth } from '$lib/stores/auth.svelte';

	let { children } = $props();
	let allowed = $state(false);

	onMount(() => {
		if (!auth.isEmployer && !auth.isAdmin) {
			goto('/dashboard');
		} else {
			allowed = true;
		}
	});
</script>

{#if allowed}
	{@render children()}
{/if}
