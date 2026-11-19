import { shallowRef } from 'vue'
import { defineStore } from 'pinia'

type Destination = 'report' | 'diagnosis'

/** 首页填写的问题只在当前账号的内存中转交一次。 @author yangaobo */
export const useIntakeStore = defineStore('intake', () => {
  const draft = shallowRef<{
    ownerId: string
    destination: Destination
    description: string
  } | null>(null)

  function prepare(
    ownerId: string,
    destination: Destination,
    description: string,
  ) {
    draft.value = { ownerId, destination, description: description.trim() }
  }

  function reset() {
    draft.value = null
  }

  function take(ownerId: string | undefined, destination: Destination) {
    const current = draft.value
    reset()
    return current &&
      current.ownerId === ownerId &&
      current.destination === destination
      ? current.description
      : ''
  }

  return { prepare, reset, take }
})
