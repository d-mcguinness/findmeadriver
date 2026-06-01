import { redirect } from '@sveltejs/kit';
import { auth } from '$lib/stores/auth.svelte';
import type { LayoutLoad } from './$types';

// Shipper area: admins may also enter (they post loads on an shipper's behalf).
export const load: LayoutLoad = () => {
	if (!auth.isShipper && !auth.isAdmin) {
		redirect(307, '/dashboard');
	}
};
