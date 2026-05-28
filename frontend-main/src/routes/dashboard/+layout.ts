import { redirect } from '@sveltejs/kit';
import { auth } from '$lib/stores/auth.svelte';
import type { LayoutLoad } from './$types';

// Auth gate for the whole dashboard. Runs before the layout renders (no
// flash-then-redirect), and reads the auth store which is hydrated from
// localStorage at module load — so the token is already present here even on a
// cold load. Client-side only (root layout sets ssr = false).
export const load: LayoutLoad = () => {
	if (!auth.isAuthenticated) {
		redirect(307, '/login');
	}
};
