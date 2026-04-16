import { computed, ref } from 'vue'

export function useCursorPager() {
  const currentPage = ref(1)
  const pageCursors = ref<Array<string | null>>([null])
  const hasMore = ref(false)

  const canPrev = computed(() => currentPage.value > 1)
  const canNext = computed(() => hasMore.value)

  function reset() {
    currentPage.value = 1
    pageCursors.value = [null]
    hasMore.value = false
  }

  function getCursor(page = currentPage.value) {
    return pageCursors.value[page - 1] ?? null
  }

  function commit(page: number, nextCursor: string | null, more: boolean) {
    currentPage.value = page
    hasMore.value = more
    const nextPageCursors = pageCursors.value.slice(0, page)
    if (!nextPageCursors.length) {
      nextPageCursors.push(null)
    }
    if (more) {
      nextPageCursors[page] = nextCursor
    }
    pageCursors.value = nextPageCursors
  }

  return {
    currentPage,
    hasMore,
    canPrev,
    canNext,
    reset,
    getCursor,
    commit,
  }
}