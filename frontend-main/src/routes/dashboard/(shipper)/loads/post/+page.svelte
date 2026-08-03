<script lang="ts">
	import {
		Grid, Row, Column,
		Button, TextInput, TextArea, NumberInput, Select, SelectItem,
		InlineNotification, Tag
	} from 'carbon-components-svelte';
	import { ArrowLeft, Add, TrashCan, ArrowUp, ArrowDown, MagicWand } from 'carbon-icons-svelte';
	import { api } from '$lib/api';
	import { auth } from '$lib/stores/auth.svelte';
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import { onMount } from 'svelte';
	import LocationPicker from '$lib/components/LocationPicker.svelte';
	import RouteTransferMap from '$lib/components/RouteTransferMap.svelte';
	import { calculateRoute, findNearbyTransfers, type RouteInfo, type TransferOption } from '$lib/google-maps';
	import { licenceCategoriesFor } from '$lib/licence-categories';
	import { TRANSPORT_MODE_OPTIONS, transportModeLabel, modeTagColor, estimatedCommissionPct } from '$lib/transport-modes';
	import { estimateLegCarrierCost, chargeableQuantity, chargeUnitForMode, type LegQuantities } from '$lib/pricing';
	import { formatMoney } from '$lib/money';
	import { HAULAGE_COUNTRIES } from '$lib/countries';
	import type { LoadStopType, Itinerary } from '$lib/types';

	type ShipperOption = { id: number; companyName: string; email: string; country?: string };

	type StopDraft = {
		clientId: string;
		type: LoadStopType;
		country: string;
		address: string;
		coords: { lat: number; lng: number } | null;
	};

	type LegDraft = {
		clientId: string;
		transportMode: string;
		pickupLocation: string;
		deliveryLocation: string;
		pickupCountry: string;
		deliveryCountry: string;
		pickupCoords: { lat: number; lng: number } | null;
		deliveryCoords: { lat: number; lng: number } | null;
		requiredLicenceCategory: string;
		distanceKm: number;
		weightKg: number;
		volumeM3: number;
		containerCount: number;
		// Only set when editing an existing itinerary: the distance basis the
		// server reported, and the distance it came with. On submit the basis is
		// echoed back only while the number is untouched — see submitPayloadLeg.
		loadedDistanceSource?: string;
		loadedDistanceKm?: number;
	};

	const STOP_TYPE_OPTIONS: { value: LoadStopType; label: string }[] = [
		{ value: 'PICKUP', label: 'Pickup' },
		{ value: 'DELIVERY', label: 'Delivery' },
		{ value: 'WAYPOINT', label: 'Waypoint' },
		{ value: 'REST', label: 'Rest stop' },
		{ value: 'BORDER', label: 'Border crossing' },
		{ value: 'FERRY_TERMINAL', label: 'Ferry terminal' },
		{ value: 'EUROTUNNEL', label: 'Eurotunnel' }
	];

	const STOP_TAG_COLOR: Record<LoadStopType, 'green' | 'red' | 'blue' | 'purple' | 'cyan' | 'teal' | 'magenta'> = {
		PICKUP: 'green',
		DELIVERY: 'red',
		WAYPOINT: 'blue',
		REST: 'purple',
		BORDER: 'magenta',
		FERRY_TERMINAL: 'cyan',
		EUROTUNNEL: 'teal'
	};

	const BASIS_LABELS: Record<string, string> = {
		PER_KM: 'per km',
		PER_CONTAINER: 'per container',
		PER_CHARGEABLE_KG: 'per chargeable-kg',
		PER_PIECE: 'per piece'
	};

	let shipperCountry = $state('IE');
	let licenceOptions = $derived(licenceCategoriesFor(shipperCountry));

	let loadForm = $state({
		title: '',
		description: '',
		transportMode: 'ROAD',
		estimatedDurationHours: 4,
		dateNeeded: '',
		ratePerHour: 25,
		requiredLicenceCategory: 'C'
	});

	let isIntermodal = $derived(loadForm.transportMode === 'INTERMODAL');
	// Set once an existing itinerary has been loaded for editing (via
	// ?itineraryId=), rather than posting a new one.
	let editingItineraryId = $state<number | null>(null);

	// Per-mode quantity inputs that drive the rate card (per-km / per-container /
	// per-chargeable-kg). Left blank → the server prices on rate × hours instead.
	let quantities = $state<{
		distanceKm: number | null;
		weightKg: number | null;
		volumeM3: number | null;
		containerCount: number | null;
		pieceCount: number | null;
	}>({ distanceKm: null, weightKg: null, volumeM3: null, containerCount: null, pieceCount: null });

	const UNIT_LABELS: Record<string, string> = {
		PER_KM: 'per km', PER_CONTAINER: 'per container',
		PER_CHARGEABLE_KG: 'per chargeable-kg', PER_PIECE: 'per piece',
		PER_HOUR: 'per hour', FLAT: 'flat'
	};
	const UNIT_QTY: Record<string, string> = {
		PER_KM: 'km', PER_CONTAINER: 'containers',
		PER_CHARGEABLE_KG: 'chargeable-kg', PER_PIECE: 'pieces'
	};
	const unitLabel = (u: string) => UNIT_LABELS[u] ?? u;
	const unitQty = (u: string) => UNIT_QTY[u] ?? '';

	// LegQuantities for the rate-card mirror (null → undefined = "not provided").
	let singleLegQuantities: LegQuantities = $derived({
		distanceKm: quantities.distanceKm ?? undefined,
		weightKg: quantities.weightKg ?? undefined,
		volumeM3: quantities.volumeM3 ?? undefined,
		containerCount: quantities.containerCount ?? undefined,
		pieceCount: quantities.pieceCount ?? undefined
	});

	// Live estimate of what the shipper will be charged. Carrier cost comes from
	// the mode's rate card when the relevant quantity is present (mirrors the
	// server's PricingPolicy); otherwise it falls back to rate × hours. The
	// platform fee varies by mode; the backend recomputes the authoritative figure.
	let pricingPreview = $derived.by(() => {
		const mode = loadForm.transportMode;
		const hours = Number(loadForm.estimatedDurationHours) || 0;
		const rate = Number(loadForm.ratePerHour) || 0;
		const rateCardCost = estimateLegCarrierCost(mode, singleLegQuantities);
		const usingRateCard = rateCardCost != null;
		const carrierCost = rateCardCost ?? hours * rate;
		const pct = estimatedCommissionPct(mode);
		const fee = carrierCost * (pct / 100);
		return {
			carrierCost, pct, fee, total: carrierCost + fee,
			usingRateCard, unit: chargeUnitForMode(mode), qty: chargeableQuantity(mode, singleLegQuantities)
		};
	});

	function newStop(type: LoadStopType): StopDraft {
		return {
			clientId: `stop-${Math.random().toString(36).slice(2, 9)}`,
			type,
			country: shipperCountry,
			address: '',
			coords: null
		};
	}

	let stops = $state<StopDraft[]>([newStop('PICKUP'), newStop('DELIVERY')]);

	// First PICKUP / last DELIVERY drive the legacy API shape and the route calc.
	let firstPickup = $derived(stops.find(s => s.type === 'PICKUP'));
	let lastDelivery = $derived([...stops].reverse().find(s => s.type === 'DELIVERY'));

	function newLeg(mode: string): LegDraft {
		return {
			clientId: `leg-${Math.random().toString(36).slice(2, 9)}`,
			transportMode: mode,
			pickupLocation: '',
			deliveryLocation: '',
			pickupCountry: shipperCountry,
			deliveryCountry: shipperCountry,
			pickupCoords: null,
			deliveryCoords: null,
			requiredLicenceCategory: 'C',
			distanceKm: 100,
			weightKg: 100,
			volumeM3: 1,
			containerCount: 1
		};
	}

	// Sensible starter: a road feeder into an ocean main leg.
	let legs = $state<LegDraft[]>([newLeg('ROAD'), newLeg('OCEAN')]);

	// Live estimate, mirroring the server: each leg priced on its mode's rate
	// card (per-km / per-container / per chargeable-kg), then the per-mode fee
	// on top, summed. The backend recomputes the authoritative figure on submit.
	function legQuantitiesFor(l: LegDraft) {
		return {
			distanceKm: Number(l.distanceKm) || undefined,
			weightKg: Number(l.weightKg) || undefined,
			volumeM3: Number(l.volumeM3) || undefined,
			containerCount: Number(l.containerCount) || undefined
		};
	}
	let intermodalPreview = $derived.by(() => {
		let carrier = 0;
		let fee = 0;
		const rows = legs.map((l) => {
			const q = legQuantitiesFor(l);
			const c = estimateLegCarrierCost(l.transportMode, q) ?? 0;
			const pct = estimatedCommissionPct(l.transportMode);
			const f = (c * pct) / 100;
			carrier += c;
			fee += f;
			return {
				carrier: c,
				pct,
				fee: f,
				total: c + f,
				unit: chargeUnitForMode(l.transportMode),
				qty: chargeableQuantity(l.transportMode, q)
			};
		});
		return { rows, carrier, fee, total: carrier + fee };
	});

	// Every leg's pickup + delivery, in order, for the admin-only overview map
	// that links the whole itinerary together — each leg keeps its own mode so
	// the overview draws the same recommended route (real road/rail/sea
	// routing, not a straight line) as that leg's own map below.
	let allLegRoutes = $derived(
		legs.map((l) => ({
			mode: l.transportMode,
			stops: [
				{ type: 'PICKUP', address: l.pickupLocation, country: l.pickupCountry, coords: l.pickupCoords },
				{ type: 'DELIVERY', address: l.deliveryLocation, country: l.deliveryCountry, coords: l.deliveryCoords }
			]
		}))
	);

	function onLegPickupSelected(leg: LegDraft, place: { address: string; lat: number; lng: number }) {
		leg.pickupLocation = place.address;
		leg.pickupCoords = { lat: place.lat, lng: place.lng };
	}
	function onLegDeliverySelected(leg: LegDraft, place: { address: string; lat: number; lng: number }) {
		leg.deliveryLocation = place.address;
		leg.deliveryCoords = { lat: place.lat, lng: place.lng };
	}

	function addLeg() {
		legs = [...legs, newLeg('ROAD')];
	}
	function removeLeg(i: number) {
		if (legs.length <= 1) return;
		legs = legs.filter((_, j) => j !== i);
	}
	function moveLeg(i: number, delta: -1 | 1) {
		const t = i + delta;
		if (t < 0 || t >= legs.length) return;
		const copy = [...legs];
		[copy[i], copy[t]] = [copy[t], copy[i]];
		legs = copy;
	}

	let postError = $state('');
	let postSuccess = $state('');
	let postLoading = $state(false);

	let shippers = $state<ShipperOption[]>([]);
	let selectedShipperId = $state<string>('');

	onMount(async () => {
		// Arriving via the old /loads/post-intermodal link/bookmark.
		const modeParam = page.url.searchParams.get('mode');
		if (modeParam && (modeParam === 'INTERMODAL' || TRANSPORT_MODE_OPTIONS.some((o) => o.value === modeParam))) {
			loadForm.transportMode = modeParam;
		}

		if (auth.isAdmin) {
			try {
				shippers = await api.get<ShipperOption[]>('/api/admin/shippers');
				const hinted = page.url.searchParams.get('shipperId');
				if (hinted && shippers.some(e => String(e.id) === hinted)) {
					selectedShipperId = hinted;
				} else if (shippers.length > 0) {
					selectedShipperId = String(shippers[0].id);
				}
			} catch {
				shippers = [];
			}
		}

		// Arriving via "Edit" on an existing itinerary (/dashboard/itineraries).
		const itineraryId = page.url.searchParams.get('itineraryId');
		if (itineraryId) {
			await loadItineraryForEdit(Number(itineraryId));
		}
	});

	async function loadItineraryForEdit(id: number) {
		postError = '';
		try {
			const it = await api.get<Itinerary>(
				auth.isAdmin ? `/api/admin/itineraries/${id}` : `/api/shipper/itineraries/${id}`
			);
			loadForm = { ...loadForm, transportMode: 'INTERMODAL', title: it.orderTitle ?? '', description: it.description ?? '', dateNeeded: it.dateNeeded ?? '' };
			legs = (it.legs ?? []).map((l) => ({
				clientId: `leg-${Math.random().toString(36).slice(2, 9)}`,
				transportMode: l.mode,
				pickupLocation: l.pickupLocation ?? '',
				deliveryLocation: l.deliveryLocation ?? '',
				pickupCountry: l.originCountry ?? shipperCountry,
				deliveryCountry: l.destinationCountry ?? shipperCountry,
				pickupCoords: null,
				deliveryCoords: null,
				requiredLicenceCategory: l.requiredLicenceCategory ?? 'C',
				distanceKm: l.distanceKm ?? 100,
				weightKg: l.weightKg ?? 100,
				volumeM3: l.volumeM3 ?? 1,
				containerCount: l.containerCount ?? 1,
				loadedDistanceSource: l.distanceSource,
				loadedDistanceKm: l.distanceKm
			}));
			editingItineraryId = it.id;
		} catch (e: any) {
			postError = e.message || 'Failed to load the itinerary for editing';
		}
	}

	// One leg, shaped for POST/PUT. The mode-specific quantity drives the
	// rate-card carrier cost; the distance goes along for *every* mode, not just
	// road, because it is the leg's measured length (used for CO2 and for the
	// route planner's own figures) — sending it only for road would blank it on
	// every non-road leg each time an itinerary is edited.
	function submitPayloadLeg(l: LegDraft) {
		return {
			transportMode: l.transportMode,
			pickupLocation: l.pickupLocation,
			deliveryLocation: l.deliveryLocation,
			pickupCountry: l.pickupCountry,
			deliveryCountry: l.deliveryCountry,
			requiredLicenceCategory: l.requiredLicenceCategory,
			distanceKm: l.distanceKm,
			// Keep the server's basis only while the distance is exactly what it
			// gave us; the moment the shipper changes the number it is theirs, and
			// omitting this records it as CLIENT_SUPPLIED.
			distanceSource:
				l.loadedDistanceSource && Number(l.distanceKm) === Number(l.loadedDistanceKm)
					? l.loadedDistanceSource
					: undefined,
			weightKg: l.transportMode === 'AIR' ? l.weightKg : undefined,
			volumeM3: l.transportMode === 'AIR' ? l.volumeM3 : undefined,
			containerCount:
				l.transportMode === 'OCEAN' || l.transportMode === 'RAIL' ? l.containerCount : undefined
		};
	}

	let routeInfo = $state<RouteInfo | null>(null);
	// Per-stop nearby transfer availability (by mode), keyed by the stop's clientId.
	let transfersByStop = $state<Record<string, TransferOption[] | 'loading'>>({});
	let routeLoading = $state(false);

	async function recalculateRoute() {
		// Route through every stop that has coordinates, in their current order,
		// so waypoints (and border/ferry/rest stops) count toward distance + time.
		const points = stops.filter((s) => s.coords).map((s) => s.coords!);
		if (points.length < 2) {
			routeInfo = null;
			return;
		}
		const origin = points[0];
		const destination = points[points.length - 1];
		const intermediates = points.slice(1, -1);
		routeLoading = true;
		try {
			routeInfo = await calculateRoute(origin, destination, intermediates);
			if (routeInfo) {
				const hours = Math.round((routeInfo.durationSeconds / 3600) * 100) / 100;
				loadForm = { ...loadForm, estimatedDurationHours: hours };
				// Feed the road rate card — distance is its chargeable quantity.
				quantities = { ...quantities, distanceKm: Math.round(routeInfo.distanceKm * 10) / 10 };
			}
		} catch {
			routeInfo = null;
		} finally {
			routeLoading = false;
		}
	}

	function onStopPlaceSelected(index: number, place: { address: string; lat: number; lng: number }) {
		stops[index].address = place.address;
		stops[index].coords = { lat: place.lat, lng: place.lng };
		recalculateRoute();
		checkTransfers(stops[index].clientId, { lat: place.lat, lng: place.lng });
	}

	// When a stop gets real coordinates, ask Google Places which modes have a
	// transfer point (airport / rail station / ferry terminal) nearby, so the
	// shipper can see whether air/rail/sea are realistic from that stop.
	async function checkTransfers(clientId: string, coords: { lat: number; lng: number }) {
		transfersByStop = { ...transfersByStop, [clientId]: 'loading' };
		try {
			const result = await findNearbyTransfers(coords);
			transfersByStop = { ...transfersByStop, [clientId]: result };
		} catch {
			transfersByStop = { ...transfersByStop, [clientId]: [] };
		}
	}

	function addStop() {
		// Insert before the final DELIVERY when there is one, otherwise append.
		const lastDeliveryIdx = stops.map(s => s.type).lastIndexOf('DELIVERY');
		const insertAt = lastDeliveryIdx === -1 ? stops.length : lastDeliveryIdx;
		stops = [...stops.slice(0, insertAt), newStop('WAYPOINT'), ...stops.slice(insertAt)];
	}

	function removeStop(index: number) {
		if (stops.length <= 2) return; // keep at least pickup + delivery
		stops = stops.filter((_, i) => i !== index);
		recalculateRoute();
	}

	function moveStop(index: number, delta: -1 | 1) {
		const target = index + delta;
		if (target < 0 || target >= stops.length) return;
		const copy = [...stops];
		[copy[index], copy[target]] = [copy[target], copy[index]];
		stops = copy;
		recalculateRoute();
	}

	function validate(): string | null {
		if (!loadForm.title.trim()) return 'Title is required.';
		if (!loadForm.dateNeeded) return 'Date is required.';
		if (isIntermodal) {
			if (legs.length < 1) return 'Add at least one leg.';
			for (let i = 0; i < legs.length; i++) {
				const l = legs[i];
				if (!l.pickupLocation.trim() || !l.deliveryLocation.trim()) {
					return `Leg ${i + 1} needs a pickup and delivery location.`;
				}
			}
			return null;
		}
		if (!firstPickup || !firstPickup.address.trim()) return 'At least one pickup with an address is required.';
		if (!lastDelivery || !lastDelivery.address.trim()) return 'At least one delivery with an address is required.';
		const blank = stops.findIndex(s => !s.address.trim());
		if (blank !== -1) return `Stop ${blank + 1} has no address.`;
		return null;
	}

	// --- Demo convenience: fill the form with a realistic sample load (single-leg
	// or intermodal, depending on the mode currently selected). Shown to
	// shippers/admins only — handy for exercising the pricing preview per mode. ---
	type LoadSample = {
		mode: string; title: string; description: string;
		pickup: string; pickupCountry: string; delivery: string; deliveryCountry: string;
		hours: number; rate: number; licence: string;
		q: Partial<{ distanceKm: number; weightKg: number; volumeM3: number; containerCount: number; pieceCount: number }>;
	};

	const LOAD_SAMPLES: LoadSample[] = [
		{ mode: 'ROAD', title: 'Dublin → Cork pallet run',
		  description: '12 ambient pallets, tail-lift required. Same-day delivery.',
		  pickup: 'Dublin, Ireland', pickupCountry: 'IE', delivery: 'Cork, Ireland', deliveryCountry: 'IE',
		  hours: 3.5, rate: 30, licence: 'C', q: { distanceKm: 255 } },
		{ mode: 'OCEAN', title: 'Rotterdam → Dublin FCL',
		  description: '2 × 40ft containers of machinery parts, full-container load.',
		  pickup: 'Port of Rotterdam, Netherlands', pickupCountry: 'NL', delivery: 'Dublin Port, Ireland', deliveryCountry: 'IE',
		  hours: 8, rate: 40, licence: 'C', q: { containerCount: 2 } },
		{ mode: 'AIR', title: 'Cologne → Dublin air freight',
		  description: 'Time-critical pharma, temperature-controlled, 450 kg.',
		  pickup: 'Cologne Bonn Airport, Germany', pickupCountry: 'DE', delivery: 'Dublin Airport, Ireland', deliveryCountry: 'IE',
		  hours: 5, rate: 50, licence: 'C', q: { weightKg: 450, volumeM3: 1.8 } },
		{ mode: 'RAIL', title: 'Duisburg → Lyon intermodal rail',
		  description: '3 containers, freight-all-kinds, scheduled service.',
		  pickup: 'Duisburg Terminal, Germany', pickupCountry: 'DE', delivery: 'Lyon Terminal, France', deliveryCountry: 'FR',
		  hours: 14, rate: 35, licence: 'C', q: { containerCount: 3 } }
	];

	type SampleLeg = {
		mode: string; from: string; fromCountry: string; to: string; toCountry: string;
		distanceKm?: number; weightKg?: number; volumeM3?: number; containerCount?: number;
	};
	type ItinerarySample = { title: string; description: string; dueInDays: number; legs: SampleLeg[] };

	const ITINERARY_SAMPLES: ItinerarySample[] = [
		{ title: 'Dublin → Amsterdam door-to-door', dueInDays: 10,
		  description: 'Road feeder, short-sea, then final-mile road — one 40ft container.',
		  legs: [
			{ mode: 'ROAD', from: 'Dublin, Ireland', fromCountry: 'IE', to: 'Dublin Port, Ireland', toCountry: 'IE', distanceKm: 12 },
			{ mode: 'OCEAN', from: 'Dublin Port, Ireland', fromCountry: 'IE', to: 'Port of Rotterdam, Netherlands', toCountry: 'NL', containerCount: 1 },
			{ mode: 'ROAD', from: 'Port of Rotterdam, Netherlands', fromCountry: 'NL', to: 'Amsterdam, Netherlands', toCountry: 'NL', distanceKm: 80 }
		  ] },
		{ title: 'Madrid → Dublin express (air)', dueInDays: 5,
		  description: 'Road to the airport, air freight, then road to the consignee — 600 kg.',
		  legs: [
			{ mode: 'ROAD', from: 'Madrid, Spain', fromCountry: 'ES', to: 'Madrid-Barajas Airport, Spain', toCountry: 'ES', distanceKm: 18 },
			{ mode: 'AIR', from: 'Madrid-Barajas Airport, Spain', fromCountry: 'ES', to: 'Dublin Airport, Ireland', toCountry: 'IE', weightKg: 600, volumeM3: 2.4 },
			{ mode: 'ROAD', from: 'Dublin Airport, Ireland', fromCountry: 'IE', to: 'Dublin, Ireland', toCountry: 'IE', distanceKm: 12 }
		  ] },
		{ title: 'Hamburg → Dublin (rail + short-sea)', dueInDays: 14,
		  description: 'Rail to the port, short-sea crossing, then road final mile — two containers.',
		  legs: [
			{ mode: 'RAIL', from: 'Hamburg, Germany', fromCountry: 'DE', to: 'Bremerhaven, Germany', toCountry: 'DE', containerCount: 2 },
			{ mode: 'OCEAN', from: 'Bremerhaven, Germany', fromCountry: 'DE', to: 'Dublin Port, Ireland', toCountry: 'IE', containerCount: 2 },
			{ mode: 'ROAD', from: 'Dublin Port, Ireland', fromCountry: 'IE', to: 'Naas, Ireland', toCountry: 'IE', distanceKm: 35 }
		  ] }
	];

	// Fills a random intermodal sample and switches the mode to Multimodal —
	// used by both the mode-aware autofill button (when already on Multimodal)
	// and the admin-only "autofill multimodal" shortcut (regardless of the
	// mode currently selected).
	function fillIntermodalSample() {
		const s = ITINERARY_SAMPLES[Math.floor(Math.random() * ITINERARY_SAMPLES.length)];
		const due = new Date();
		due.setDate(due.getDate() + s.dueInDays);
		loadForm = { ...loadForm, transportMode: 'INTERMODAL', title: s.title, description: s.description, dateNeeded: due.toISOString().split('T')[0] };
		legs = s.legs.map((l) => {
			const base = newLeg(l.mode);
			return {
				...base,
				pickupLocation: l.from,
				deliveryLocation: l.to,
				pickupCountry: l.fromCountry,
				deliveryCountry: l.toCountry,
				distanceKm: l.distanceKm ?? base.distanceKm,
				weightKg: l.weightKg ?? base.weightKg,
				volumeM3: l.volumeM3 ?? base.volumeM3,
				containerCount: l.containerCount ?? base.containerCount
			};
		});
		postError = '';
		postSuccess = '';
	}

	function fillSingleLegSample() {
		const s = LOAD_SAMPLES[Math.floor(Math.random() * LOAD_SAMPLES.length)];
		const due = new Date();
		due.setDate(due.getDate() + 7);
		loadForm = {
			title: s.title,
			description: s.description,
			transportMode: s.mode,
			estimatedDurationHours: s.hours,
			dateNeeded: due.toISOString().split('T')[0],
			ratePerHour: s.rate,
			requiredLicenceCategory: s.licence
		};
		quantities = {
			distanceKm: s.q.distanceKm ?? null,
			weightKg: s.q.weightKg ?? null,
			volumeM3: s.q.volumeM3 ?? null,
			containerCount: s.q.containerCount ?? null,
			pieceCount: s.q.pieceCount ?? null
		};
		stops = [
			{ ...newStop('PICKUP'), country: s.pickupCountry, address: s.pickup },
			{ ...newStop('DELIVERY'), country: s.deliveryCountry, address: s.delivery }
		];
		routeInfo = null;
		postError = '';
		postSuccess = '';
	}

	function autofillForm() {
		if (isIntermodal) fillIntermodalSample();
		else fillSingleLegSample();
	}

	async function handleSubmit() {
		postError = '';
		postSuccess = '';
		const v = validate();
		if (v) { postError = v; return; }

		postLoading = true;
		try {
			if (isIntermodal) {
				const payload = {
					title: loadForm.title,
					description: loadForm.description,
					dateNeeded: loadForm.dateNeeded,
					legs: legs.map(submitPayloadLeg)
				};
				if (editingItineraryId) {
					await api.put(
						auth.isAdmin
							? `/api/admin/itineraries/${editingItineraryId}`
							: `/api/shipper/itineraries/${editingItineraryId}`,
						payload
					);
					postSuccess = 'Itinerary updated! Redirecting...';
				} else {
					if (auth.isAdmin) {
						if (!selectedShipperId) {
							postError = 'Please choose an shipper to create the load under.';
							postLoading = false;
							return;
						}
						await api.post(
							`/api/admin/itineraries?shipperId=${encodeURIComponent(selectedShipperId)}`,
							payload
						);
					} else {
						await api.post('/api/shipper/itineraries', payload);
					}
					postSuccess = 'Intermodal load created! Redirecting...';
				}
				setTimeout(() => goto('/dashboard/itineraries'), 1200);
				return;
			}

			// Send the full ordered Stops list. Legacy pickup/delivery fields are
			// still populated for any consumer that hasn't migrated; the backend
			// uses stops when present and ignores them.
			const payload = {
				...loadForm,
				pickupLocation: firstPickup!.address,
				deliveryLocation: lastDelivery!.address,
				ratePerHour: loadForm.ratePerHour,
				// Per-mode quantities so the server prices on the rate card.
				// Only the populated ones are sent (undefined is dropped by JSON).
				distanceKm: quantities.distanceKm ?? undefined,
				weightKg: quantities.weightKg ?? undefined,
				volumeM3: quantities.volumeM3 ?? undefined,
				containerCount: quantities.containerCount ?? undefined,
				pieceCount: quantities.pieceCount ?? undefined,
				stops: stops.map(s => ({
					type: s.type,
					locationName: s.address,
					addressLine: s.address,
					country: s.country,
					latitude: s.coords?.lat,
					longitude: s.coords?.lng
				}))
			};

			if (auth.isAdmin) {
				if (!selectedShipperId) {
					postError = 'Please choose an shipper to create the load under.';
					postLoading = false;
					return;
				}
				await api.post(
					`/api/admin/loads?shipperId=${encodeURIComponent(selectedShipperId)}`,
					payload
				);
			} else {
				await api.post('/api/shipper/loads', payload);
			}
			postSuccess = 'Load created successfully! Redirecting...';
			setTimeout(() => goto('/dashboard/itineraries'), 1500);
		} catch (e: any) {
			postError = e.message || (isIntermodal ? 'Failed to create intermodal load' : 'Failed to create load');
		} finally {
			postLoading = false;
		}
	}
</script>

<Grid>
	<Row>
		<Column>
			<div class="page-header">
				<Button kind="ghost" size="small" href="/dashboard/itineraries" icon={ArrowLeft}>
					Back
				</Button>
				<h1 class="section-heading">
					<span class="icon-badge sm"><Add size={20} /></span>
					{editingItineraryId ? 'Edit Intermodal Load' : isIntermodal ? 'Post an Intermodal Load' : 'Create a Load'}
				</h1>
				{#if isIntermodal}
					<p class="sub">
						{editingItineraryId
							? "Update this itinerary's legs and route — the number of legs can't change here; cancel and repost for that."
							: "Build a door-to-door movement from multiple legs — each with its own mode, route, and rate. We price every leg with its mode's platform fee and roll them up to one total."}
					</p>
				{/if}
			</div>
		</Column>
	</Row>

	<Row>
		<Column lg={11} md={8} sm={4}>
			{#if postError}
				<InlineNotification kind="error" title="Error" subtitle={postError}
					on:close={() => postError = ''} />
			{/if}
			{#if postSuccess}
				<InlineNotification kind="success" title="Success" subtitle={postSuccess}
					on:close={() => postSuccess = ''} />
			{/if}

			<div class="form-grid">
				{#if !editingItineraryId && (auth.isShipper || auth.isAdmin)}
					<div class="autofill-bar">
						<Button kind="tertiary" size="small" icon={MagicWand} on:click={autofillForm}>
							{isIntermodal ? 'Autofill with a sample intermodal route' : 'Autofill with sample data'}
						</Button>
						{#if auth.isAdmin}
							<Button kind="tertiary" size="small" icon={MagicWand} on:click={fillIntermodalSample}>
								Autofill multimodal sample (admin)
							</Button>
						{/if}
						<span class="autofill-hint">Fills a random demo load — handy for quick testing.</span>
					</div>
				{/if}

				{#if !editingItineraryId && auth.isAdmin}
					<Select bind:selected={selectedShipperId}
						labelText="Create Load On Behalf Of (Shipper)">
						{#each shippers as emp}
							<SelectItem value={String(emp.id)}
								text="{emp.companyName} ({emp.email})" />
						{/each}
					</Select>
				{/if}

				<TextInput bind:value={loadForm.title}
					labelText="Load Title" placeholder="e.g. Dublin to Cork delivery" />

				<TextArea bind:value={loadForm.description}
					labelText="Description" placeholder="Describe the delivery requirements..."
					rows={3} />

				<TextInput bind:value={loadForm.dateNeeded}
					labelText="Date Needed (YYYY-MM-DD)" placeholder="2026-04-10"
					type="date" />

				<Select bind:selected={loadForm.transportMode} labelText="Transport Mode" disabled={!!editingItineraryId}>
					{#each TRANSPORT_MODE_OPTIONS as opt}
						<SelectItem value={opt.value} text={opt.label} />
					{/each}
					<SelectItem value="INTERMODAL" text="Multimodal" />
				</Select>

				{#if isIntermodal}
					<div class="legs-section">
						{#if auth.isShipper || auth.isAdmin}
							<div class="legs-overview-map">
								<h4>Full itinerary overview</h4>
								<p class="legs-overview-hint">All legs linked in order, each drawn with its own mode's recommended route — real road/rail routing, sea lanes, etc. — not a straight line.</p>
								<RouteTransferMap legs={allLegRoutes} showRouteOptions={false} />
							</div>
						{/if}

						<div class="legs-header">
							<h3 class="section-heading">Legs</h3>
							{#if !editingItineraryId}
								<Button kind="tertiary" size="small" icon={Add} on:click={addLeg}>Add leg</Button>
							{/if}
						</div>
						<p class="legs-hint">
							{editingItineraryId
								? "Legs run in order, top to bottom. Each leg is priced — and its recommended route shown — on its own mode. The number of legs can't be changed here."
								: "Legs run in order, top to bottom. Each leg is priced — and its recommended route shown — on its own mode."}
						</p>

						{#each legs as leg, i (leg.clientId)}
							<div class="leg-card">
								<div class="leg-top">
									<span class="leg-seq">Leg {i + 1}</span>
									<Tag type={modeTagColor(leg.transportMode)}>{transportModeLabel(leg.transportMode)}</Tag>
									<span class="leg-spacer"></span>
									<Button kind="ghost" size="small" icon={ArrowUp} iconDescription="Move up"
										disabled={i === 0} on:click={() => moveLeg(i, -1)} />
									<Button kind="ghost" size="small" icon={ArrowDown} iconDescription="Move down"
										disabled={i === legs.length - 1} on:click={() => moveLeg(i, 1)} />
									<Button kind="danger-ghost" size="small" icon={TrashCan} iconDescription="Remove leg"
										disabled={legs.length <= 1 || !!editingItineraryId} on:click={() => removeLeg(i)} />
								</div>

								<div class="leg-row">
									<Select bind:selected={leg.transportMode} labelText="Mode">
										{#each TRANSPORT_MODE_OPTIONS as opt}
											<SelectItem value={opt.value} text={opt.label} />
										{/each}
									</Select>
									<Select bind:selected={leg.requiredLicenceCategory} labelText="Required Licence">
										{#each licenceOptions as opt}
											<SelectItem value={opt.code} text={opt.label} />
										{/each}
									</Select>
								</div>

								<div class="leg-row">
									<Select bind:selected={leg.pickupCountry} labelText="From country">
										{#each HAULAGE_COUNTRIES as c}
											<SelectItem value={c.code} text="{c.code} — {c.name}" />
										{/each}
									</Select>
									<LocationPicker
										labelText="From"
										placeholder="Pickup location"
										bind:value={leg.pickupLocation}
										onPlaceSelected={(place) => onLegPickupSelected(leg, place)}
									/>
								</div>
								<div class="leg-row">
									<Select bind:selected={leg.deliveryCountry} labelText="To country">
										{#each HAULAGE_COUNTRIES as c}
											<SelectItem value={c.code} text="{c.code} — {c.name}" />
										{/each}
									</Select>
									<LocationPicker
										labelText="To"
										placeholder="Delivery location"
										bind:value={leg.deliveryLocation}
										onPlaceSelected={(place) => onLegDeliverySelected(leg, place)}
									/>
								</div>

								<div class="leg-map">
									<RouteTransferMap
										stops={[
											{ type: 'PICKUP', address: leg.pickupLocation, country: leg.pickupCountry, coords: leg.pickupCoords },
											{ type: 'DELIVERY', address: leg.deliveryLocation, country: leg.deliveryCountry, coords: leg.deliveryCoords }
										]}
										quantities={{ distanceKm: leg.distanceKm, weightKg: leg.weightKg, volumeM3: leg.volumeM3, containerCount: leg.containerCount }}
										mode={leg.transportMode}
									/>
								</div>

								<div class="leg-row">
									{#if leg.transportMode === 'AIR'}
										<NumberInput bind:value={leg.weightKg} label="Weight (kg)" min={1} step={1} />
										<NumberInput bind:value={leg.volumeM3} label="Volume (m³)" min={0.1} step={0.1} />
									{:else if leg.transportMode === 'ROAD'}
										<NumberInput bind:value={leg.distanceKm} label="Distance (km)" min={1} step={1} />
									{:else}
										<NumberInput bind:value={leg.containerCount} label="Containers" min={1} step={1} />
									{/if}
								</div>

								<div class="leg-price">
									<span class="leg-basis">{BASIS_LABELS[intermodalPreview.rows[i]?.unit] ?? ''} × {intermodalPreview.rows[i]?.qty ?? '—'}</span>
									<span>Carrier {formatMoney(intermodalPreview.rows[i]?.carrier)}</span>
									<span>+ {transportModeLabel(leg.transportMode)} fee {intermodalPreview.rows[i]?.pct}% ({formatMoney(intermodalPreview.rows[i]?.fee)})</span>
									<strong>= {formatMoney(intermodalPreview.rows[i]?.total)}</strong>
								</div>
							</div>
						{/each}
					</div>

					<div class="totals">
						<div class="totals-line"><span>Carrier cost (all legs)</span><strong>{formatMoney(intermodalPreview.carrier)}</strong></div>
						<div class="totals-line"><span>Platform fee (all legs)</span><strong>{formatMoney(intermodalPreview.fee)}</strong></div>
						<div class="totals-line grand"><span>Estimated total</span><strong>{formatMoney(intermodalPreview.total)}</strong></div>
						<p class="totals-hint">Estimate — the server confirms exact per-mode fees on submit.</p>
					</div>
				{:else}
					<div class="stops-section">
						<div class="stops-header">
							<h3>Route</h3>
							<Button kind="tertiary" size="small" icon={Add} on:click={addStop}>
								Add stop
							</Button>
						</div>
						<p class="stops-hint">
							Order matters — drag-free reordering via the arrow buttons. International
							routes can include border, ferry, or Eurotunnel stops between pickup and
							delivery.
						</p>

						{#each stops as stop, i (stop.clientId)}
							<div class="stop-row">
								<div class="stop-index">
									<span class="seq">{i + 1}</span>
									<Tag type={STOP_TAG_COLOR[stop.type]} size="sm">{stop.type}</Tag>
								</div>

								<div class="stop-fields">
									<Select bind:selected={stop.type} labelText="Type" hideLabel>
										{#each STOP_TYPE_OPTIONS as opt}
											<SelectItem value={opt.value} text={opt.label} />
										{/each}
									</Select>
									<Select bind:selected={stop.country} labelText="Country" hideLabel>
										{#each HAULAGE_COUNTRIES as c}
											<SelectItem value={c.code} text="{c.code} — {c.name}" />
										{/each}
									</Select>
									<LocationPicker
										labelText={`Stop ${i + 1} address`}
										placeholder="Address, Eircode, or postcode"
										bind:value={stop.address}
										onPlaceSelected={(place) => onStopPlaceSelected(i, place)}
									/>
								</div>

								<div class="stop-actions">
									<Button kind="ghost" size="small" icon={ArrowUp}
										iconDescription="Move up"
										disabled={i === 0}
										on:click={() => moveStop(i, -1)} />
									<Button kind="ghost" size="small" icon={ArrowDown}
										iconDescription="Move down"
										disabled={i === stops.length - 1}
										on:click={() => moveStop(i, 1)} />
									<Button kind="danger-ghost" size="small" icon={TrashCan}
										iconDescription="Remove stop"
										disabled={stops.length <= 2}
										on:click={() => removeStop(i)} />
								</div>
							</div>
						{@const tx = transfersByStop[stop.clientId]}
						{#if tx}
							<div class="stop-transfers">
								<span class="transfers-label">Transfers nearby</span>
								{#if tx === 'loading'}
									<span class="transfers-loading">checking…</span>
								{:else}
									{#each tx as t}
										<Tag type={t.available ? modeTagColor(t.mode) : 'gray'} size="sm">{transportModeLabel(t.mode)}{#if t.available && t.distanceKm != null} · {t.distanceKm} km{:else if !t.available} · none{/if}</Tag>
									{/each}
								{/if}
							</div>
						{/if}
						{/each}
					</div>

					{#if routeLoading}
						<div class="route-info">
							<p class="route-calculating">Calculating route through all stops...</p>
						</div>
					{:else if routeInfo}
						<div class="route-info">
							<div class="route-detail">
								<strong>Distance:</strong> {routeInfo.distanceText} ({routeInfo.distanceKm.toFixed(1)} km)
							</div>
							<div class="route-detail">
								<strong>Estimated Drive Time:</strong> {routeInfo.durationText}
							</div>
						</div>
					{/if}

					<div class="route-map-section">
						<h3>Route map</h3>
						<p class="route-map-hint">Your stops, the recommended route for {transportModeLabel(loadForm.transportMode)}, and the nearest air / rail / sea transfer points within 50&nbsp;km.</p>
						<RouteTransferMap {stops} {quantities} mode={loadForm.transportMode} />
					</div>

					<div class="quantity-section">
						<h3>Shipment quantity</h3>
						<p class="quantity-hint">
							Priced on the {transportModeLabel(loadForm.transportMode)} rate card
							({unitLabel(pricingPreview.unit)}). Leave blank to fall back to rate &times; hours.
						</p>
						{#if loadForm.transportMode === 'ROAD'}
							<NumberInput bind:value={quantities.distanceKm}
								label="Distance (km)" min={0} step={1} allowEmpty
								helperText="Auto-filled from the route above — override if needed." />
						{:else if loadForm.transportMode === 'RAIL' || loadForm.transportMode === 'OCEAN'}
							<NumberInput bind:value={quantities.containerCount}
								label="Containers" min={0} step={1} allowEmpty />
						{:else if loadForm.transportMode === 'AIR'}
							<div class="form-row">
								<NumberInput bind:value={quantities.weightKg}
									label="Weight (kg)" min={0} step={1} allowEmpty />
								<NumberInput bind:value={quantities.volumeM3}
									label="Volume (m³)" min={0} step={0.1} allowEmpty
									helperText="Chargeable kg = max(actual, volume × 167)." />
							</div>
						{/if}
					</div>

					<div class="form-row">
						<NumberInput bind:value={loadForm.estimatedDurationHours}
							label="Estimated Hours" min={0.01} max={10} step={0.01} />
						<NumberInput bind:value={loadForm.ratePerHour}
							label="Rate per Hour (&euro;)" min={10} max={200} step={1} />
					</div>

					<Select bind:selected={loadForm.requiredLicenceCategory}
						labelText="Required Licence Category">
						{#each licenceOptions as opt}
							<SelectItem value={opt.code} text={opt.label} />
						{/each}
					</Select>

					<div class="pricing-preview">
						<h4>Pricing preview</h4>
						<div class="pricing-rows">
							<div class="pricing-line">
								{#if pricingPreview.usingRateCard}
									<span>Carrier cost · {unitLabel(pricingPreview.unit)} ({Math.round(pricingPreview.qty ?? 0)} {unitQty(pricingPreview.unit)})</span>
								{:else}
									<span>Carrier cost · hourly ({loadForm.estimatedDurationHours}h × {formatMoney(loadForm.ratePerHour)})</span>
								{/if}
								<strong>{formatMoney(pricingPreview.carrierCost)}</strong>
							</div>
							<div class="pricing-line">
								<span>Platform fee · {transportModeLabel(loadForm.transportMode)} ({pricingPreview.pct}%)</span>
								<strong>{formatMoney(pricingPreview.fee)}</strong>
							</div>
							<div class="pricing-line pricing-total">
								<span>You pay</span>
								<strong>{formatMoney(pricingPreview.total)}</strong>
							</div>
						</div>
						<p class="pricing-hint">
							{pricingPreview.usingRateCard
								? 'Priced on the mode rate card — the server confirms the exact figure when you post.'
								: 'Falling back to rate × hours — enter the shipment quantity above to price on the rate card.'}
						</p>
					</div>
				{/if}

				<Button on:click={handleSubmit} disabled={postLoading || !loadForm.title || !loadForm.dateNeeded}>
					{#if postLoading}
						{editingItineraryId ? 'Saving...' : 'Creating...'}
					{:else if editingItineraryId}
						Save Changes
					{:else if isIntermodal}
						Create Intermodal Load
					{:else}
						Create a Load
					{/if}
				</Button>
			</div>
		</Column>
	</Row>
</Grid>

<style>
	.page-header {
		margin-bottom: 1.5rem;
	}
	.page-header h1 {
		display: flex;
		align-items: center;
		gap: 0.5rem;
		margin-top: 0.5rem;
	}
	.sub { color: var(--cds-text-secondary); max-width: 720px; line-height: 1.5; margin-top: 0.5rem; }
	.form-grid {
		display: flex;
		flex-direction: column;
		gap: 1rem;
		max-width: 820px;
	}
	.form-row {
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: 1rem;
	}
	.autofill-bar {
		display: flex;
		align-items: center;
		gap: 0.75rem;
		flex-wrap: wrap;
	}
	.autofill-hint {
		font-size: 0.8125rem;
		color: var(--cds-text-secondary);
	}
	.stops-section {
		display: flex;
		flex-direction: column;
		gap: 0.75rem;
		padding: 1rem;
		background: var(--cds-layer, #f4f4f4);
		border-left: 3px solid var(--cds-interactive, #0f62fe);
	}
	.stops-header {
		display: flex;
		justify-content: space-between;
		align-items: center;
	}
	.stops-header h3 {
		margin: 0;
		font-size: 1rem;
	}
	.stops-hint {
		font-size: 0.8125rem;
		color: var(--cds-text-secondary);
		margin: 0;
	}
	.stop-row {
		display: grid;
		grid-template-columns: 6rem 1fr auto;
		gap: 0.75rem;
		align-items: start;
		padding: 0.5rem;
		background: var(--cds-background, #fff);
	}
	.stop-index {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 0.25rem;
		padding-top: 0.25rem;
	}
	.stop-index .seq {
		font-weight: 600;
		font-size: 0.875rem;
	}
	.stop-fields {
		display: grid;
		grid-template-columns: 8rem 9rem 1fr;
		gap: 0.5rem;
	}
	.stop-actions {
		display: flex;
		gap: 0.125rem;
		align-items: center;
	}
	.route-info {
		background: var(--cds-layer, #f4f4f4);
		border-left: 3px solid var(--cds-interactive, #0f62fe);
		padding: 0.75rem 1rem;
		display: flex;
		gap: 2rem;
		flex-wrap: wrap;
	}
	.route-detail {
		font-size: 0.875rem;
	}
	.route-calculating {
		font-size: 0.875rem;
		color: var(--cds-text-secondary);
		font-style: italic;
		margin: 0;
	}
	.route-map-section {
		display: flex;
		flex-direction: column;
		gap: 0.5rem;
	}
	.route-map-section h3 {
		margin: 0;
		font-size: 1rem;
	}
	.route-map-hint {
		font-size: 0.8125rem;
		color: var(--cds-text-secondary);
		margin: 0;
	}
	.quantity-section {
		display: flex;
		flex-direction: column;
		gap: 0.5rem;
		padding: 1rem;
		background: var(--cds-layer, #f4f4f4);
		border-left: 3px solid var(--cds-interactive, #0f62fe);
	}
	.quantity-section h3 {
		margin: 0;
		font-size: 1rem;
	}
	.quantity-hint {
		font-size: 0.8125rem;
		color: var(--cds-text-secondary);
		margin: 0;
	}
	.pricing-preview {
		background: var(--cds-layer, #f4f4f4);
		border-left: 3px solid var(--cds-support-success, #24a148);
		padding: 0.75rem 1rem;
	}
	.pricing-preview h4 {
		margin: 0 0 0.5rem;
		font-size: 0.875rem;
		text-transform: uppercase;
		letter-spacing: 0.02em;
		color: var(--cds-text-secondary);
	}
	.pricing-rows {
		display: flex;
		flex-direction: column;
		gap: 0.25rem;
	}
	.pricing-line {
		display: flex;
		justify-content: space-between;
		gap: 1rem;
		font-size: 0.875rem;
	}
	.pricing-total {
		border-top: 1px solid var(--cds-border-subtle, #e0e0e0);
		margin-top: 0.25rem;
		padding-top: 0.375rem;
		font-size: 1rem;
	}
	.pricing-hint {
		font-size: 0.75rem;
		color: var(--cds-text-secondary);
		margin: 0.5rem 0 0;
	}
	.legs-section {
		display: flex; flex-direction: column; gap: 0.75rem; padding: 1rem;
		background: var(--cds-layer, #f4f4f4);
		border-left: 3px solid var(--cds-interactive, #0f62fe);
	}
	.legs-header { display: flex; justify-content: space-between; align-items: center; }
	.legs-header h3 { margin: 0; font-size: 1rem; }
	.legs-hint { font-size: 0.8125rem; color: var(--cds-text-secondary); margin: 0; }
	.leg-card {
		display: flex; flex-direction: column; gap: 0.5rem;
		padding: 0.75rem; background: var(--cds-background, #fff);
		border: 1px solid var(--cds-border-subtle, #e0e0e0);
	}
	.leg-top { display: flex; align-items: center; gap: 0.5rem; }
	.leg-seq { font-weight: 600; font-size: 0.875rem; }
	.leg-spacer { flex: 1 1 auto; }
	.leg-row { display: grid; grid-template-columns: 1fr 2fr; gap: 0.5rem; }
	.leg-map { margin: 0.25rem 0; }
	.legs-overview-map {
		display: flex; flex-direction: column; gap: 0.375rem;
		padding: 0.75rem; margin-bottom: 0.25rem;
		background: var(--cds-background, #fff);
		border: 1px solid var(--cds-border-subtle, #e0e0e0);
	}
	.legs-overview-map h4 { margin: 0; font-size: 0.875rem; }
	.legs-overview-hint { font-size: 0.8125rem; color: var(--cds-text-secondary); margin: 0; }
	.leg-price {
		display: flex; gap: 0.75rem; flex-wrap: wrap; align-items: baseline;
		font-size: 0.8125rem; color: var(--cds-text-secondary);
		border-top: 1px dashed var(--cds-border-subtle, #e0e0e0); padding-top: 0.5rem;
	}
	.leg-price strong { color: var(--cds-text-primary); font-size: 0.9375rem; }
	.leg-basis {
		padding: 0.05rem 0.4rem;
		border-radius: 3px;
		background: var(--cds-layer-accent, #e0e0e0);
		font-size: 0.6875rem;
	}
	.totals {
		background: var(--cds-layer, #f4f4f4);
		border-left: 3px solid var(--cds-support-success, #24a148);
		padding: 0.75rem 1rem;
	}
	.totals-line { display: flex; justify-content: space-between; gap: 1rem; font-size: 0.875rem; }
	.totals-line.grand {
		border-top: 1px solid var(--cds-border-subtle, #e0e0e0);
		margin-top: 0.35rem; padding-top: 0.45rem; font-size: 1.0625rem;
	}
	.totals-hint { font-size: 0.75rem; color: var(--cds-text-secondary); margin: 0.5rem 0 0; }
	@media (max-width: 672px) {
		.form-row,
		.stop-fields,
		.leg-row {
			grid-template-columns: 1fr;
		}
		.stop-row {
			grid-template-columns: 1fr;
		}
	}
	.stop-transfers {
		display: flex;
		align-items: center;
		flex-wrap: wrap;
		gap: 0.35rem;
		padding: 0 0.5rem 0.5rem;
		font-size: 0.8125rem;
	}
	.transfers-label { color: var(--cds-text-secondary); margin-right: 0.25rem; }
	.transfers-loading { color: var(--cds-text-secondary); font-style: italic; }
</style>
