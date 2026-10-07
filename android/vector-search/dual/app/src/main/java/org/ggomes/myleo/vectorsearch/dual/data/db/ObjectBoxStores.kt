package org.ggomes.myleo.vectorsearch.dual.data.db

import android.content.Context
import io.objectbox.BoxStore

/** Process-wide ObjectBox store singleton (MyObjectBox is generated at build time). */
object ObjectBoxStores {

    @Volatile
    private var instance: BoxStore? = null

    fun get(context: Context): BoxStore = instance ?: synchronized(this) {
        instance ?: MyObjectBox.builder()
            .androidContext(context.applicationContext)
            .name("vectors")
            .build()
            .also { instance = it }
    }
}
