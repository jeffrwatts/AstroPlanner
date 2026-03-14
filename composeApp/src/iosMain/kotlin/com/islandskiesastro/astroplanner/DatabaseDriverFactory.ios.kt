package com.islandskiesastro.astroplanner

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.islandskiesastro.astroplanner.database.AstroDatabase

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver =
        NativeSqliteDriver(AstroDatabase.Schema, "astro.db")
}
