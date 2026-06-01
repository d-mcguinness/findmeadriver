<script lang="ts">
	// Role guard lives in +layout.ts (load), which redirects before this renders.
	let { children } = $props();
</script>

{@render children()}
