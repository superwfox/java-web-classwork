export interface Location { latitude: number; longitude: number }
export interface Message extends Location {
  id: number
  text: string
  country: string
  createdAt: string
  updatedAt: string
  version: number
}
export class ApiError extends Error {
  constructor(message: string, public status: number) { super(message) }
}
const endpoint = new URL('api/messages', new URL(import.meta.env.BASE_URL, window.location.href))
async function request<T>(url: URL, options: RequestInit = {}): Promise<T> {
  const response = await fetch(url, { ...options, signal: AbortSignal.timeout(15000) })
  if (!response.ok) {
    const body = await response.json().catch(() => null)
    throw new ApiError(body?.error || '连接失败，请稍后重试', response.status)
  }
  return response.status === 204 ? undefined as T : response.json() as Promise<T>
}
export async function listMessages(): Promise<Message[]> {
  const messages: Message[] = []
  let after: number | null = 0
  do {
    const url = new URL(endpoint)
    url.searchParams.set('after', String(after))
    const page: { items: Message[]; next: number | null } = await request(url)
    messages.push(...page.items)
    after = page.next
  } while (after !== null)
  return messages
}
export function writeMessage<T = Message>(fields: Record<string, string | number>): Promise<T> {
  return request<T>(endpoint, {
    method: 'POST',
    headers: { 'X-Requested-With': 'JavaWeb' },
    body: new URLSearchParams(Object.entries(fields).map(([key, value]) => [key, String(value)])),
  })
}
export function countryName(code: string) {
  if (!code) return '未知地域'
  try { return new Intl.DisplayNames(['zh-CN'], { type: 'region' }).of(code) || code } catch { return code }
}
