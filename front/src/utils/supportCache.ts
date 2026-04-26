import type { MemberSupportSession, SupportMessage } from '@/api/support'

const SUPPORT_CACHE_PREFIX = 'member-support-cache-v1'
const SUPPORT_ATTACHMENT_DB = 'kawang-support-attachments'
const SUPPORT_ATTACHMENT_STORE = 'attachments'

export type CachedSupportState = {
  session: MemberSupportSession | null
  messages: SupportMessage[]
  nextCursor: string | null
  hasMore: boolean
  savedAt: string
}

function supportCacheKey(memberId: number) {
  return `${SUPPORT_CACHE_PREFIX}:${memberId}`
}

function canUseBrowserStorage() {
  return typeof window !== 'undefined' && typeof window.localStorage !== 'undefined'
}

export function readCachedSupportState(memberId: number) {
  if (!canUseBrowserStorage()) {
    return null
  }

  try {
    const raw = window.localStorage.getItem(supportCacheKey(memberId))
    if (!raw) {
      return null
    }
    const parsed = JSON.parse(raw) as CachedSupportState
    if (!parsed || !Array.isArray(parsed.messages)) {
      return null
    }
    return parsed
  } catch {
    return null
  }
}

export function writeCachedSupportState(memberId: number, state: CachedSupportState) {
  if (!canUseBrowserStorage()) {
    return
  }

  try {
    window.localStorage.setItem(supportCacheKey(memberId), JSON.stringify(state))
  } catch {
    // Ignore quota and serialization failures. Runtime state remains usable.
  }
}

export function clearCachedSupportState(memberId: number) {
  if (!canUseBrowserStorage()) {
    return
  }

  try {
    window.localStorage.removeItem(supportCacheKey(memberId))
  } catch {
    // Ignore storage failures during cleanup.
  }
}

let supportAttachmentDbPromise: Promise<IDBDatabase> | null = null

function openSupportAttachmentDb() {
  if (supportAttachmentDbPromise) {
    return supportAttachmentDbPromise
  }

  supportAttachmentDbPromise = new Promise((resolve, reject) => {
    if (typeof window === 'undefined' || !window.indexedDB) {
      reject(new Error('IndexedDB unavailable'))
      return
    }

    const request = window.indexedDB.open(SUPPORT_ATTACHMENT_DB, 1)

    request.onerror = () => reject(request.error ?? new Error('Failed to open support attachment cache'))
    request.onupgradeneeded = () => {
      const database = request.result
      if (!database.objectStoreNames.contains(SUPPORT_ATTACHMENT_STORE)) {
        database.createObjectStore(SUPPORT_ATTACHMENT_STORE)
      }
    }
    request.onsuccess = () => resolve(request.result)
  })

  return supportAttachmentDbPromise
}

async function withAttachmentStore<T>(mode: IDBTransactionMode, handler: (store: IDBObjectStore) => IDBRequest<T>) {
  const database = await openSupportAttachmentDb()
  return new Promise<T>((resolve, reject) => {
    const transaction = database.transaction(SUPPORT_ATTACHMENT_STORE, mode)
    const store = transaction.objectStore(SUPPORT_ATTACHMENT_STORE)
    const request = handler(store)

    request.onerror = () => reject(request.error ?? new Error('Support attachment cache request failed'))
    request.onsuccess = () => resolve(request.result)
  })
}

export async function readCachedSupportAttachment(cacheKey: string) {
  if (!cacheKey) {
    return null
  }

  try {
    return await withAttachmentStore('readonly', (store) => store.get(cacheKey))
  } catch {
    return null
  }
}

export async function writeCachedSupportAttachment(cacheKey: string, blob: Blob) {
  if (!cacheKey) {
    return
  }

  try {
    await withAttachmentStore('readwrite', (store) => store.put(blob, cacheKey))
  } catch {
    // Ignore cache write failures. The network path still works.
  }
}

export async function removeCachedSupportAttachment(cacheKey: string) {
  if (!cacheKey) {
    return
  }

  try {
    await withAttachmentStore('readwrite', (store) => store.delete(cacheKey))
  } catch {
    // Ignore cleanup failures.
  }
}

export async function moveCachedSupportAttachment(sourceKey: string, targetKey: string) {
  if (!sourceKey || !targetKey || sourceKey === targetKey) {
    return
  }

  const blob = await readCachedSupportAttachment(sourceKey)
  if (!blob) {
    return
  }

  await writeCachedSupportAttachment(targetKey, blob)
  await removeCachedSupportAttachment(sourceKey)
}
