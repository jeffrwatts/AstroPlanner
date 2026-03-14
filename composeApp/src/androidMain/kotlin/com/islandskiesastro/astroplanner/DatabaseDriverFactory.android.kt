package com.islandskiesastro.astroplanner

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.islandskiesastro.astroplanner.database.AstroDatabase

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver =
        AndroidSqliteDriver(AstroDatabase.Schema, context, "astro.db")
}
