import { api } from './client';
import type {
  AuthResponse,
  Booking,
  EventSeat,
  EventSummary,
  LockSeatResponse,
  Page,
  QueueStatus,
  TicketType,
  UserBookings,
  UserProfile,
  Venue,
} from './types';

export const auth = {
  register: (email: string, password: string, requestedRoles: string[]) =>
    api.anonymousPost<{ id: string; email: string; roles: string[] }>('/api/v1/auth/register', {
      email,
      password,
      requestedRoles,
    }),

  login: (email: string, password: string) =>
    api.anonymousPost<AuthResponse>('/api/v1/auth/login', { email, password }),

  logout: (refreshToken: string) =>
    api.anonymousPost<void>(`/api/v1/auth/logout?refreshToken=${encodeURIComponent(refreshToken)}`),

  me: () => api.get<UserProfile>('/api/v1/user/me'),
};

export const events = {
  list: (page = 0, size = 50) =>
    api.get<Page<EventSummary>>(`/api/v1/events/?page=${page}&size=${size}`),

  create: (payload: {
    title: string;
    description: string;
    venue_id: string;
    startDate: string;
    endDate: string;
    ticketSaleStartDate: string;
    ticketSaleEndDate: string;
  }) => api.post<EventSummary>('/api/v1/events/', payload),

  availableSeats: (eventId: string) =>
    api.get<EventSeat[]>(`/api/v1/events/${eventId}/seats/available`),
};

export const ticketTypes = {
  forEvent: (eventId: string) => api.get<TicketType[]>(`/api/v1/ticket-types/event/${eventId}`),

  create: (payload: {
    title: string;
    description: string;
    event_id: string;
    price: number;
    totalQuantity: number;
  }) => api.post<TicketType>('/api/v1/ticket-types/', payload),
};

export const eventSeats = {
  /** Omitting section/rowLabel/seatId applies the ticket type to every seat in the event. */
  assignTicketType: (payload: {
    eventId: string;
    ticketTypeId: string;
    section?: string;
    rowLabel?: string;
    seatId?: string;
  }) => api.post<{ Updated: boolean }>('/api/v1/event-seats/assign-ticket-type', payload),
};

export const queue = {
  join: (eventId: string) => api.post<QueueStatus>(`/api/v1/booking/queue/${eventId}/join`),
  status: (eventId: string) => api.get<QueueStatus>(`/api/v1/booking/queue/${eventId}/status`),
};

export const bookings = {
  lock: (eventId: string, seatIds: string[], ticketTypeId: string) =>
    api.post<LockSeatResponse>('/api/v1/bookings/lock', { eventId, seatIds, ticketTypeId }),

  initiatePayment: (bookingId: string) =>
    api.post<{ status: string }>('/api/v1/bookings/payment/initiate', { bookingId }),

  get: (bookingId: string) => api.get<Booking>(`/api/v1/bookings/${bookingId}`),

  mine: () => api.get<UserBookings>('/api/v1/bookings/my-bookings'),
};

export const venues = {
  list: () => api.get<Venue[]>('/api/v1/venue/'),
};

export const staff = {
  validateByReference: (bookingReference: string) =>
    api.post<{ Valid: boolean }>('/api/v1/staff/booking', { bookingReference }),

  validateByQr: (qrCode: string) => api.post<{ Valid: boolean }>('/api/v1/staff/qr', { qrCode }),
};
