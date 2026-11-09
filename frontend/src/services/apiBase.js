export const API_BASE = `${import.meta.env.BASE_URL.replace(/\/$/, '')}/api/v1`

export function notifyUnauthorized() {
  window.dispatchEvent(new CustomEvent('pet-home:unauthorized'))
}
