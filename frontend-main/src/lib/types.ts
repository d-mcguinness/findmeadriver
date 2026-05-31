export interface DayAvailability {
	id: number | null;
	date: string;
	availableHours: number;
}

export interface AvailabilityResponse {
	days: DayAvailability[];
	weeklyTotal: number;
	fortnightlyTotal: number;
	weeklyRemaining: number;
	fortnightlyRemaining: number;
}

export interface DriverLane {
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

export type JobStopType =
	| 'PICKUP'
	| 'DELIVERY'
	| 'WAYPOINT'
	| 'REST'
	| 'BORDER'
	| 'FERRY_TERMINAL'
	| 'EUROTUNNEL';

export interface JobStopLocation {
	id?: number;
	name: string;
	addressLine?: string;
	city?: string;
	country?: string;
	latitude?: number;
	longitude?: number;
	timezone?: string;
}

export interface JobStop {
	id?: number;
	sequence: number;
	type: JobStopType;
	location: JobStopLocation;
	earliestAt?: string;
	latestAt?: string;
	actualAt?: string;
}

export interface Job {
	id: number;
	title: string;
	description: string;
	pickupLocation: string;
	deliveryLocation: string;
	/**
	 * Full ordered route. Present on jobs linked to a Shipment (Phase-0 TMS
	 * tree); empty array on legacy jobs. Consumers should prefer this over
	 * pickupLocation/deliveryLocation when length > 0.
	 */
	stops?: JobStop[];
	estimatedDurationHours: number;
	dateNeeded: string;
	ratePerHour: number;
	currency?: string;
	/** Pricing (M1b): carrier cost, per-mode platform commission, shipper total. */
	carrierCost?: number;
	commissionPercent?: number;
	commissionAmount?: number;
	shipperTotal?: number;
	pickupCountry?: string;
	deliveryCountry?: string;
	requiredLicenceCategory?: string;
	/** @deprecated kept for legacy callers; mirror of requiredLicenceCategory. */
	requiredCdlType?: string;
	/** Transport mode of the underlying shipment leg: ROAD | RAIL | OCEAN | AIR | … */
	transportMode?: string;
	status: string;
	shipperCompanyName: string;
	assignedDriverId?: number;
	assignedDriverName?: string;
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
}

export interface Itinerary {
	id: number;
	shipperId?: number;
	shipperName?: string;
	orderId?: number;
	orderTitle?: string;
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

export interface JobApplication {
	id: number;
	jobId: number;
	jobTitle: string;
	jobStatus: string;
	driverName: string;
	driverEmail: string;
	driverId: number;
	status: string;
	coverNote: string;
	appliedAt: string;
	driverAverageRating?: number;
	driverRatingCount?: number;
	driverVerified: boolean;
}

export interface RatingResponse {
	id: number;
	jobId: number;
	jobTitle: string;
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

export interface DriverComplianceSummary {
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
	totalDrivers: number;
	totalShippers: number;
	totalJobs: number;
	openJobs: number;
	assignedJobs: number;
	inProgressJobs: number;
	completedJobs: number;
	cancelledJobs: number;
	pendingDocuments: number;
}

export interface TimeSlot {
	id: number;
	date: string;
	startTime: string;
	endTime: string;
	hours: number;
}
