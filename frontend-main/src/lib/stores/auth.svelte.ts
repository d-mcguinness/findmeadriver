import { browser } from '$app/environment';
import { api } from '$lib/api';

interface User {
	email: string;
	firstName: string;
	lastName: string;
	phone: string;
	roles: string[];
}

interface LoginResponse {
	message: string;
	user: unknown;
	token: string;
}

// localStorage keys
const TOKEN_KEY = 'token';
const IMPERSONATOR_KEY = 'fmad_impersonator';

function parseJwt(token: string): Record<string, unknown> {
	const base64Url = token.split('.')[1];
	const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
	const jsonPayload = decodeURIComponent(
		atob(base64)
			.split('')
			.map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
			.join('')
	);
	return JSON.parse(jsonPayload);
}

function isTokenExpired(token: string): boolean {
	try {
		const payload = parseJwt(token);
		const exp = payload.exp as number;
		return Date.now() >= exp * 1000;
	} catch {
		return true;
	}
}

function createAuth() {
	let token = $state<string | null>(null);
	let user = $state<User | null>(null);
	let impersonating = $state(false);

	function hydrateFromToken(jwt: string) {
		const claims = parseJwt(jwt);
		token = jwt;
		user = {
			email: claims.email as string,
			firstName: claims.firstName as string,
			lastName: claims.lastName as string,
			phone: claims.phone as string,
			roles: claims.roles as string[]
		};
	}

	function initialize() {
		const stored = localStorage.getItem(TOKEN_KEY);
		if (stored && !isTokenExpired(stored)) {
			hydrateFromToken(stored);
			// Survive a refresh mid-impersonation so the banner stays.
			impersonating = localStorage.getItem(IMPERSONATOR_KEY) !== null;
		} else {
			localStorage.removeItem(TOKEN_KEY);
			localStorage.removeItem(IMPERSONATOR_KEY);
			impersonating = false;
		}
	}

	async function login(email: string, password: string) {
		const response = await api.post<LoginResponse>('/api/user/login', { email, password });
		localStorage.setItem(TOKEN_KEY, response.token);
		hydrateFromToken(response.token);
	}

	function logout() {
		token = null;
		user = null;
		impersonating = false;
		localStorage.removeItem(TOKEN_KEY);
		localStorage.removeItem(IMPERSONATOR_KEY);
	}

	// Admin starts mimicking a user: stash the admin's own token, swap in the
	// target's token (from POST /api/admin/users/{id}/impersonate — same shape as
	// login), and re-derive state. api.ts reads localStorage '${TOKEN_KEY}' live,
	// so the next request goes out as the target.
	function impersonate(response: LoginResponse) {
		const current = localStorage.getItem(TOKEN_KEY);
		if (current && !impersonating) {
			localStorage.setItem(IMPERSONATOR_KEY, current);
		}
		impersonating = true;
		localStorage.setItem(TOKEN_KEY, response.token);
		hydrateFromToken(response.token);
	}

	// Restore the stashed admin token and drop the marker.
	function stopImpersonating() {
		const adminToken = localStorage.getItem(IMPERSONATOR_KEY);
		localStorage.removeItem(IMPERSONATOR_KEY);
		impersonating = false;
		if (adminToken && !isTokenExpired(adminToken)) {
			localStorage.setItem(TOKEN_KEY, adminToken);
			hydrateFromToken(adminToken);
		} else {
			logout();
		}
	}

	return {
		get token() { return token; },
		get user() { return user; },
		get isAuthenticated() { return token !== null; },
		get isCarrier() { return user?.roles?.includes('ROLE_CARRIER') ?? false; },
		get isShipper() { return user?.roles?.includes('ROLE_SHIPPER') ?? false; },
		get isAdmin() { return user?.roles?.includes('ROLE_ADMIN') ?? false; },
		// Per-role landing path — the dashboard index was removed, so login, the
		// role-group guards and the nav all route here instead of a shared /dashboard.
		get homePath() {
			if (user?.roles?.includes('ROLE_ADMIN')) return '/dashboard/users';
			if (user?.roles?.includes('ROLE_SHIPPER')) return '/dashboard/itineraries';
			if (user?.roles?.includes('ROLE_CARRIER')) return '/dashboard/capabilities';
			return '/login';
		},
		get isImpersonating() { return impersonating; },
		get impersonatedLabel() {
			return user ? (`${user.firstName ?? ''} ${user.lastName ?? ''}`.trim() || user.email) : '';
		},
		initialize,
		login,
		logout,
		impersonate,
		stopImpersonating
	};
}

export const auth = createAuth();

// Rehydrate from localStorage at module load — before any component mounts.
// This must not wait for onMount: child layouts mount before parents, so a
// guard in dashboard/+layout.svelte would otherwise run before a root-layout
// onMount could restore the token, bouncing cold loads of authed routes to
// /login. Guarded by `browser` so it's a no-op under SSR/prerender.
if (browser) {
	auth.initialize();
}
