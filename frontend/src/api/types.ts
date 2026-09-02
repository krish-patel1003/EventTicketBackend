export type Role = 'USER' | 'ORGANIZER' | 'STAFF' | 'ADMIN';

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
}

export interface UserProfile {
  email: string;
  roles: Role[];
  emailVerified: boolean;
}

export interface Venue {
  id: string;
  name: string;
  location: string;
  /** Capacity an event at this venue inherits. */
  seatCount: number;
  createdAt: string;
}

export interface TicketType {
  id: string;
  title: string;
  description: string | null;
  price: number;
  totalQuantity: number;
  availableQuantity: number;
}

export interface EventSummary {
  id: string;
  title: string;
  description: string | null;
  organizerId: string;
  venueId: string;
  startDate: string;
  endDate: string;
  ticketSaleStartDate: string;
  ticketSaleEndDate: string;
  ticketTypeList: TicketType[];
  active: boolean;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
}

export interface EventSeat {
  id: string;
  event_id: string;
  event: string;
  seatNumber: string;
  ticketType: string | null;
  rowLabel: string;
  section: string;
  locked: boolean;
  reserved: boolean;
}

export interface QueueStatus {
  eventId: string;
  userId: string;
  /** 1-based place in the waiting room, or -1 once admitted (or never queued). */
  position: number;
  /** True once the promoter has admitted this user to the seat map. */
  active: boolean;
}

export interface LockSeatResponse {
  bookingId: string;
  eventId: string;
  seatIds: string[];
  ticketTypeId: string;
  locked: boolean;
}

export type PaymentStatus = 'PENDING' | 'SUCCESS' | 'FAILED';

export interface Booking {
  id: string;
  bookingReference: string;
  user: { id: string; email: string };
  event: { id: string; title: string; venue: string; startDate: string; endDate: string };
  ticketType: { id: string; title: string };
  paymentStatus: PaymentStatus;
  billingAmount: number;
  seats: { seatId: string; seatNumber: string; rowLabel: string; section: string }[];
  /** Base64 PNG, present only once the payment has settled successfully. */
  qrCode: string | null;
}

export interface UserBookings {
  userId: string;
  email: string;
  bookingsList: Booking[];
}

export interface ApiErrorBody {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
}
