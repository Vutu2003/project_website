const SESSION_KEY = 'medical-maintenance.access-token'

export const authStorage = {
  get(): string | null {
    return window.sessionStorage.getItem(SESSION_KEY)
  },
  set(token: string): void {
    window.sessionStorage.setItem(SESSION_KEY, token)
  },
  clear(): void {
    window.sessionStorage.removeItem(SESSION_KEY)
  },
}
