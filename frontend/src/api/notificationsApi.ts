import { apiRequest } from './client'
import type { UserNotification, PageResponse } from '../types/workflow'
export const notificationsChanged = 'maintenance:notifications-changed'
export const notificationsApi = {
 list(page = 0, size = 10): Promise<PageResponse<UserNotification>> { return apiRequest(`/api/notifications?page=${page}&size=${size}`) },
 unread(): Promise<{ count: number }> { return apiRequest('/api/notifications/unread-count') },
 async read(id: number): Promise<UserNotification> { const result = await apiRequest<UserNotification>(`/api/notifications/${id}/read`, { method: 'POST' }); window.dispatchEvent(new Event(notificationsChanged)); return result },
 async readAll(): Promise<void> { await apiRequest('/api/notifications/read-all', { method: 'POST' }); window.dispatchEvent(new Event(notificationsChanged)) },
}
