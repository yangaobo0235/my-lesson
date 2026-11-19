import { inject, watch, type InjectionKey, type Ref } from 'vue'

/** 知识栏目共享当前修订的导航上下文，实际读取仍由目标页授权。 @author yangaobo */
export const knowledgeRevisionKey: InjectionKey<Ref<string | undefined>> =
  Symbol('knowledgeRevision')

/** 记录页内选中的修订，供相邻栏目保留链接上下文。 @author yangaobo */
export function useKnowledgeRevision(selection: () => string | undefined) {
  const revision = inject(knowledgeRevisionKey)
  if (revision)
    watch(selection, (id) => {
      revision.value = id
    })
}
