<script lang="ts">
	// Auth gate lives in +layout.ts (load), which redirects before this renders.
	let { children } = $props();
</script>

{@render children()}
