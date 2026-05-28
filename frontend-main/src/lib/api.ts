const BASE_URL = '';

interface ApiOptions {
	method?: string;
	body?: unknown;
	headers?: Record<string, string>;
}

export async function apiFetch<T = unknown>(path: string, options: ApiOptions = {}): Promise<T> {
	const token = localStorage.getItem('token');

	const headers: Record<string, string> = {
		'Content-Type': 'application/json',
		...options.headers
	};

	if (token) {
		headers['Authorization'] = `Bearer ${token}`;
	}

	const response = await fetch(`${BASE_URL}${path}`, {
		method: options.method || 'GET',
		headers,
		body: options.body ? JSON.stringify(options.body) : undefined
	});

	// Parse a JSON body only when there is one — 204 / empty responses (e.g.
	// DELETE endpoints) have no body and response.json() would throw.
	const text = await response.text();
	let data: any = undefined;
	if (text) {
		try {
			data = JSON.parse(text);
		} catch {
			data = { error: text };
		}
	}

	if (!response.ok) {
		// Surface the backend's message as Error.message (callers read e.message),
		// while keeping the raw fields (error/status/fields) for callers that
		// read those directly (e.g. the login page reads e.error).
		const message =
			(data && (data.error || data.message)) || `Request failed (${response.status})`;
		const err = new Error(message) as Error & Record<string, unknown>;
		err.status = response.status;
		if (data && typeof data === 'object') Object.assign(err, data);
		throw err;
	}

	return data as T;
}

export const api = {
	get: <T = unknown>(path: string) => apiFetch<T>(path),
	post: <T = unknown>(path: string, body: unknown) => apiFetch<T>(path, { method: 'POST', body }),
	put: <T = unknown>(path: string, body: unknown) => apiFetch<T>(path, { method: 'PUT', body }),
	delete: <T = unknown>(path: string) => apiFetch<T>(path, { method: 'DELETE' })
};
