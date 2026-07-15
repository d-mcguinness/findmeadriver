import type { TransportMode } from './transport-modes';

export interface DayAvailability {
	id: number | null;
	date: string;
	mode: string;
	availableHours: number;
}

/** One transport mode's duty clock: declared vs committed vs remaining over the
 *  current week + fortnight, against that mode's own regulatory ceiling. */
export interface DutyClock {
	mode: string;
	regulation: string;
	maxDailyHours: number;
	maxWeeklyHours: number;
	maxFortnightlyHours: number;
	declaredThisWeek: number;
	committedThisWeek: number;
	remainingThisWeek: number;
	declaredFortnight: number;
	committedFortnight: number;
	remainingFortnight: number;
}

export interface AvailabilityResponse {
	days: DayAvailability[];
	dutyClocks: DutyClock[];
	weeklyTotal: number;
	fortnightlyTotal: number;
	weeklyRemaining: number | null;
	fortnightlyRemaining: number | null;
}

export interface CarrierLane {
	id: number;
	originCountry: string;
	destinationCountry: string;
	createdAt: string;
}

export interface CabotageExposure {
	country: string;
	opsInWindow: number;
	limit: number;
	windowStart: string;
	oldestOpDate?: string;
	newestOpDate?: string;
	newestOpLocation?: string;
}

export type LoadStopType =
	| 'PICKUP'
	| 'DELIVERY'
	| 'WAYPOINT'
	| 'REST'
	| 'BORDER'
	| 'FERRY_TERMINAL'
	| 'EUROTUNNEL';

export interface LoadStopLocation {
	id?: number;
	name: string;
	addressLine?: string;
	city?: string;
	country?: string;
	latitude?: number;
	longitude?: number;
	timezone?: string;
}

export interface LoadStop {
	id?: number;
	sequence: number;
	type: LoadStopType;
	location: LoadStopLocation;
	earliestAt?: string;
	latestAt?: string;
	actualAt?: string;
}

export interface Load {
	id: number;
	title: string;
	description: string;
	pickupLocation: string;
	deliveryLocation: string;
	/**
	 * Full ordered route. Present on loads linked to a Shipment (Phase-0 TMS
	 * tree); empty array on legacy loads. Consumers should prefer this over
	 * pickupLocation/deliveryLocation when length > 0.
	 */
	stops?: LoadStop[];
	estimatedDurationHours: number;
	dateNeeded: string;
	// Optional flexible-window context from a routing search (see README.md,
	// "Proposed: multimodal routing engine"); not surfaced in any form yet —
	// dateNeeded above stays the one date every existing UI reads.
	earliestReadyDate?: string;
	latestHandoverDate?: string;
	arrivalDeadline?: string;
	ratePerHour: number;
	currency?: string;
	/** Pricing (M1b): carrier cost, per-mode platform commission, shipper total. */
	carrierCost?: number;
	commissionPercent?: number;
	commissionAmount?: number;
	shipperTotal?: number;
	/** Per-mode pricing quantities (M3b) — present so the edit form can prefill. */
	distanceKm?: number;
	weightKg?: number;
	volumeM3?: number;
	containerCount?: number;
	pieceCount?: number;
	pickupCountry?: string;
	deliveryCountry?: string;
	/** International / domestic / unknown classification of the leg (cabotage context). */
	movementType?: string;
	requiredLicenceCategory?: string;
	/** @deprecated kept for legacy callers; mirror of requiredLicenceCategory. */
	requiredCdlType?: string;
	/** Transport mode of the underlying shipment leg. The per-mode browse pre-filter
	 *  keys on this — a load only surfaces when this mode's duty clock has room. */
	transportMode?: TransportMode;
	status: string;
	shipperCompanyName: string;
	assignedCarrierId?: number;
	assignedCarrierName?: string;
	applicationCount: number;
	createdAt: string;
}

export interface ItineraryLeg {
	shipmentId: number;
	legSequence: number;
	mode: string;
	status?: string;
	originCountry?: string;
	destinationCountry?: string;
	pickupLocation?: string;
	deliveryLocation?: string;
	pickupLocationType?: string;
	pickupCode?: string;
	deliveryLocationType?: string;
	deliveryCode?: string;
	currency?: string;
	chargeUnit?: string;
	chargeableQuantity?: number;
	carrierCost?: number;
	commissionPercent?: number;
	commissionAmount?: number;
	shipperTotal?: number;
	// Per-mode pricing quantities + the Load-side fields — present only when
	// fetched via GET one itinerary (the edit form's prefill), not in lists.
	distanceKm?: number;
	weightKg?: number;
	volumeM3?: number;
	containerCount?: number;
	pieceCount?: number;
	requiredLicenceCategory?: string;
	ratePerHour?: number;
	estimatedDurationHours?: number;
}

export interface Itinerary {
	id: number;
	shipperId?: number;
	shipperName?: string;
	orderId?: number;
	orderTitle?: string;
	description?: string;
	dateNeeded?: string;
	// Optional flexible-window context from a routing search — not surfaced
	// in any form yet; dateNeeded above stays the one date every existing UI reads.
	earliestReadyDate?: string;
	latestHandoverDate?: string;
	arrivalDeadline?: string;
	status?: string;
	mode?: string;
	currency?: string;
	carrierCostTotal?: number;
	commissionTotal?: number;
	grandTotal?: number;
	originCountry?: string;
	destinationCountry?: string;
	legCount?: number;
	legs: ItineraryLeg[];
	createdAt?: string;
	updatedAt?: string;
}

export interface LoadApplication {
	id: number;
	loadId: number;
	loadTitle: string;
	loadStatus: string;
	carrierName: string;
	carrierEmail: string;
	carrierId: number;
	status: string;
	coverNote: string;
	appliedAt: string;
	carrierAverageRating?: number;
	carrierRatingCount?: number;
	carrierVerified: boolean;
}

export interface RatingResponse {
	id: number;
	loadId: number;
	loadTitle: string;
	reviewerName: string;
	score: number;
	comment: string;
	createdAt: string;
}

export interface UserRatingSummary {
	averageRating: number;
	totalRatings: number;
	recentRatings: RatingResponse[];
}

export interface ComplianceDocument {
	id: number;
	documentType: string;
	documentNumber: string;
	expiryDate: string;
	status: string;
	uploadedAt: string;
	verifiedAt?: string;
	notes?: string;
}

export interface CarrierComplianceSummary {
	documents: ComplianceDocument[];
	allVerified: boolean;
	verifiedCount: number;
	totalCount: number;
}

export interface AdminUser {
	id: number;
	email: string;
	firstName: string;
	lastName: string;
	phone: string;
	enabled: boolean;
	roles: string[];
	userType: string;
	licenseNumber?: string;
	licenceCategory?: string;
	/** @deprecated mirror of licenceCategory; backend keeps it populated. */
	cdlType?: string;
	yearsExperience?: number;
	companyName?: string;
	industry?: string;
}

export interface PlatformStats {
	totalUsers: number;
	totalCarriers: number;
	totalShippers: number;
	totalLoads: number;
	openLoads: number;
	assignedLoads: number;
	inProgressLoads: number;
	completedLoads: number;
	cancelledLoads: number;
	pendingDocuments: number;
}

export interface TimeSlot {
	id: number;
	date: string;
	startTime: string;
	endTime: string;
	hours: number;
}

// ---- Routing engine (com.driverdirect.routing): propose / accept ----

/** A location the shipper can route between (GET /api/shipper/locations):
 *  public reference nodes (ports/airports/rail terminals) + the shipper's own. */
export interface RoutableLocation {
	id: number;
	name: string;
	country: string;
	locationType: string;
	unlocode?: string;
	iata?: string;
	latitude?: number;
	longitude?: number;
}

/** One leg of a proposed route. */
export interface RouteLeg {
	mode: string;
	originLocationId: number;
	originLocationName: string;
	destinationLocationId: number;
	destinationLocationName: string;
	scheduled: boolean;
}

/** One proposed door-to-door option — a Pareto-best point on (cost, CO2). */
export interface RouteOption {
	totalCost: number;
	totalCo2Kg: number;
	handoverBy: string;
	arrival: string;
	/** False only on the fastest-possible fallback when no route met the
	 *  deadline (verdict computed server-side in the destination zone). */
	meetsDeadline: boolean;
	legs: RouteLeg[];
}
