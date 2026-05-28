import { redirect } from '@sveltejs/kit';
import { auth } from '$lib/stores/auth.svelte';
import type { LayoutLoad } from './$types';

// Employer area: admins may also enter (they post jobs on an employer's behalf).
export const load: LayoutLoad = () => {
	if (!auth.isEmployer && !auth.isAdmin) {
		redirect(307, '/dashboard');
	}
};
