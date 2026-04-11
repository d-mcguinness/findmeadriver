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

export interface Job {
	id: number;
	title: string;
	description: string;
	pickupLocation: string;
	deliveryLocation: string;
	estimatedDurationHours: number;
	dateNeeded: string;
	ratePerHour: number;
	requiredCdlType: string;
	status: string;
	employerCompanyName: string;
	assignedDriverId?: number;
	assignedDriverName?: string;
	applicationCount: number;
	createdAt: string;
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
