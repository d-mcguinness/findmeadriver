<script lang="ts">
	import { onMount } from 'svelte';
	import { loadGoogleMaps } from '$lib/google-maps';

	let {
		labelText = 'Location',
		placeholder = 'Enter address or Eircode',
		value = $bindable(''),
		onPlaceSelected = (_place: { address: string; lat: number; lng: number }) => {}
	}: {
		labelText?: string;
		placeholder?: string;
		value?: string;
		onPlaceSelected?: (place: { address: string; lat: number; lng: number }) => void;
	} = $props();

	let pickerEl: HTMLDivElement;
	let autocompleteEl: HTMLDivElement;
	let mapEl: HTMLDivElement;
	let map: google.maps.Map | null = null;
	let marker: google.maps.marker.AdvancedMarkerElement | null = null;
	let mapsReady = $state(false);
	let mapOpen = $state(false);
	let mapInitialized = false;
	let loadError = $state('');
	let placeAutocompleteRef: HTMLElement | null = null;
	let shadowInput: HTMLInputElement | null = null;

	// Eircode: routing key (letter + 2 digits/chars) + optional space + 4 alphanumeric
	const EIRCODE_REGEX = /^[A-Za-z]\d[\dWw]\s?[A-Za-z0-9]{4}$/;

	function isEircode(val: string): boolean {
		return EIRCODE_REGEX.test(val.trim());
	}

	function extractEircode(results: google.maps.GeocoderResult[]): string | null {
		for (const result of results) {
			for (const component of result.address_components) {
				if (component.types.includes('postal_code') && isEircode(component.long_name)) {
					return component.long_name;
				}
			}
		}
		return null;
	}

	function setAutocompleteInputValue(text: string) {
		if (shadowInput) {
			shadowInput.value = text;
			return;
		}
		// Fallback: try open shadow root
		if (placeAutocompleteRef) {
			const inner = placeAutocompleteRef.shadowRoot?.querySelector('input');
			if (inner) {
				shadowInput = inner;
				inner.value = text;
			}
		}
	}

	async function geocodeEircode(eircode: string) {
		const g = await loadGoogleMaps();
		const geocoder = new g.maps.Geocoder();
		try {
			const res = await geocoder.geocode({
				address: eircode.trim(),
				componentRestrictions: { country: 'IE' }
			});
			if (res.results[0]) {
				const loc = res.results[0].geometry.location;
				const lat = loc.lat();
				const lng = loc.lng();
				const address = res.results[0].formatted_address;
				const resolvedEircode = extractEircode(res.results);
				const display = resolvedEircode || address;

				value = display;
				setAutocompleteInputValue(display);
				mapOpen = true;
				await new Promise(r => setTimeout(r, 260));
				if (!mapInitialized) await initMap();
				setMarker(lat, lng);
				onPlaceSelected({ address, lat, lng });
			}
		} catch (err) {
			console.error('Eircode geocoding failed:', err);
		}
	}

	async function initMap() {
		if (mapInitialized || !mapsReady) return;
		mapInitialized = true;

		try {
			const g = await loadGoogleMaps();

			const { AdvancedMarkerElement } = await g.maps.importLibrary('marker') as google.maps.MarkerLibrary;

			map = new g.maps.Map(mapEl, {
				center: { lat: 53.35, lng: -6.26 },
				zoom: 7,
				mapId: 'FINDMEADRIVER_MAP',
				disableDefaultUI: true,
				zoomControl: true,
				gestureHandling: 'cooperative'
			});

			map.addListener('click', async (e: google.maps.MapMouseEvent) => {
				if (!e.latLng) return;
				const lat = e.latLng.lat();
				const lng = e.latLng.lng();

				setMarker(lat, lng);

				const geocoder = new g.maps.Geocoder();
				try {
					const res = await geocoder.geocode({ location: { lat, lng } });
					if (res.results[0]) {
						const address = res.results[0].formatted_address;
						const eircode = extractEircode(res.results);
						const display = eircode || address;
						value = display;
						setAutocompleteInputValue(display);
						onPlaceSelected({ address, lat, lng });
					}
				} catch { /* ignore */ }
			});
		} catch (err) {
			console.error('Failed to init map:', err);
			mapInitialized = false;
		}
	}

	async function setMarker(lat: number, lng: number) {
		if (!map) return;
		const pos = { lat, lng };
		map.setCenter(pos);
		map.setZoom(14);

		if (marker) {
			marker.position = pos;
		} else {
			const g = await loadGoogleMaps();
			const { AdvancedMarkerElement } = await g.maps.importLibrary('marker') as google.maps.MarkerLibrary;
			marker = new AdvancedMarkerElement({ map, position: pos });
		}
	}

	async function openMap() {
		if (!mapsReady) return;
		mapOpen = true;

		await new Promise(r => setTimeout(r, 260));

		if (!mapInitialized) {
			await initMap();
		} else if (map) {
			google.maps.event.trigger(map, 'resize');
		}
	}

	function handleClickOutside(e: MouseEvent) {
		if (!pickerEl) return;
		if (pickerEl.contains(e.target as Node)) return;
		// PlaceAutocompleteElement dropdown renders in shadow DOM inside our container,
		// but Google's overlay may be outside — check for gmp elements
		const target = e.target as HTMLElement;
		if (target.closest('gmp-internal-place-autocomplete-overlay')) return;
		mapOpen = false;
	}

	onMount(async () => {
		document.addEventListener('mousedown', handleClickOutside);

		try {
			const g = await loadGoogleMaps();
			mapsReady = true;

			// Use the new PlaceAutocompleteElement API
			// @ts-ignore — PlaceAutocompleteElement is available after importing 'places'
			const placeAutocomplete = new g.maps.places.PlaceAutocompleteElement({
				componentRestrictions: { country: 'ie' },
				types: ['geocode', 'establishment']
			});

			// Style the inner input to match our design
			placeAutocomplete.style.width = '100%';
			autocompleteEl.appendChild(placeAutocomplete);
			placeAutocompleteRef = placeAutocomplete;

			// Capture the shadow DOM input reference from the first focusin event
			autocompleteEl.addEventListener('focusin', (e: FocusEvent) => {
				if (!shadowInput) {
					const target = e.composedPath()[0];
					if (target instanceof HTMLInputElement) {
						shadowInput = target;
					}
				}
			}, { once: true });

			// Intercept Enter key to handle Eircode input directly
			autocompleteEl.addEventListener('keydown', async (e: KeyboardEvent) => {
				// Also capture shadow input from keyboard events as fallback
				if (!shadowInput) {
					const target = e.composedPath()[0];
					if (target instanceof HTMLInputElement) shadowInput = target;
				}
				if (e.key === 'Enter') {
					const actualTarget = (shadowInput || e.composedPath()[0]) as HTMLInputElement;
					const inputValue = actualTarget?.value || '';
					if (isEircode(inputValue)) {
						e.preventDefault();
						e.stopPropagation();
						await geocodeEircode(inputValue);
					}
				}
			});

			placeAutocomplete.addEventListener('gmp-placeselect', async (evt: any) => {
				const place = evt.place;
				if (!place) return;

				// Fetch full place details including geometry
				await place.fetchFields({ fields: ['displayName', 'formattedAddress', 'location'] });

				const location = place.location;
				if (location) {
					const lat = location.lat();
					const lng = location.lng();
					const address = place.formattedAddress || place.displayName || '';

					value = address;
					mapOpen = true;
					await new Promise(r => setTimeout(r, 260));
					if (!mapInitialized) await initMap();
					setMarker(lat, lng);
					onPlaceSelected({ address, lat, lng });
				}
			});
		} catch (err) {
			console.error('Google Maps load failed:', err);
			loadError = 'Maps failed to load. You can still type an address manually.';
		}

		return () => {
			document.removeEventListener('mousedown', handleClickOutside);
		};
	});
</script>

<div class="location-picker" bind:this={pickerEl}>
	<label class="picker-label">{labelText}</label>
	<!-- svelte-ignore a11y_click_events_have_key_events -->
	<!-- svelte-ignore a11y_no_static_element_interactions -->
	<div class="autocomplete-wrapper" bind:this={autocompleteEl}
		onclick={openMap}
	></div>
	{#if !mapsReady && !loadError}
		<input
			class="bx--text-input"
			type="text"
			{placeholder}
			bind:value
		/>
	{/if}
	{#if loadError}
		<p class="load-error">{loadError}</p>
		<input
			class="bx--text-input"
			type="text"
			{placeholder}
			bind:value
		/>
	{/if}
	<div class="map-container" class:open={mapOpen && mapsReady} bind:this={mapEl}></div>
</div>

<style>
	.location-picker {
		display: flex;
		flex-direction: column;
		gap: 0.5rem;
		position: relative;
	}
	.picker-label {
		font-size: 0.75rem;
		color: var(--cds-text-secondary, #525252);
		font-weight: 400;
		letter-spacing: 0.32px;
	}
	.autocomplete-wrapper {
		width: 100%;
	}
	/* Style the Google PlaceAutocompleteElement to match Carbon TextInput */
	.autocomplete-wrapper :global(gmp-place-autocomplete) {
		width: 100%;
		--gmpx-color-surface: var(--cds-field, #f4f4f4);
		--gmpx-color-on-surface: var(--cds-text-primary, #161616);
		--gmpx-color-on-surface-variant: var(--cds-text-placeholder, #a8a8a8);
		--gmpx-color-primary: var(--cds-focus, #0f62fe);
		--gmpx-font-family-base: inherit;
		--gmpx-font-size-base: 0.875rem;
		border: none;
		border-bottom: 1px solid var(--cds-border-strong, #8d8d8d);
		background-color: var(--cds-field, #f4f4f4);
		height: 2.5rem;
		display: flex;
		align-items: center;
	}
	.autocomplete-wrapper :global(gmp-place-autocomplete:focus-within) {
		outline: 2px solid var(--cds-focus, #0f62fe);
		outline-offset: -2px;
	}
	/* Override internal input styles via ::part if supported */
	.autocomplete-wrapper :global(gmp-place-autocomplete)::part(input) {
		font-size: 0.875rem;
		height: 2.5rem;
		padding: 0 1rem;
		border: none;
		background: transparent;
		color: var(--cds-text-primary, #161616);
	}
	.map-container {
		width: 100%;
		height: 0;
		overflow: hidden;
		border: 1px solid var(--cds-border-subtle, #e0e0e0);
		border-top: none;
		transition: height 0.25s ease;
	}
	.map-container.open {
		height: 220px;
		overflow: visible;
	}
	.load-error {
		font-size: 0.75rem;
		color: #da1e28;
		margin: 0;
	}
	.bx--text-input {
		font-size: 0.875rem;
		height: 2.5rem;
		padding: 0 1rem;
		border: none;
		border-bottom: 1px solid var(--cds-border-strong, #8d8d8d);
		background-color: var(--cds-field, #f4f4f4);
		color: var(--cds-text-primary, #161616);
		width: 100%;
		outline: none;
	}
	.bx--text-input:focus {
		outline: 2px solid var(--cds-focus, #0f62fe);
		outline-offset: -2px;
	}
</style>
