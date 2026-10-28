package org.lianye.di

import org.lianye.domain.repository.LianyeRepository
import org.lianye.data.draft.DraftStore
import java.io.File

class AppComponent(filesDir: File) {
    val draftStore = DraftStore(File(filesDir, "drafts/current"))
    val lianyeRepository: LianyeRepository by lazy { LianyeRepository(draftStore) }
}
