package com.rsicarelli.ktorfit.sample

import de.jensklingenberg.ktorfit.Ktorfit
import de.jensklingenberg.ktorfit.http.GET

/**
 * A fake API. Nothing here talks to a real server — it exists to force ktorfit's KSP processor to
 * generate the `createFakeApi()` extension below. If codegen does not run for the Android-only
 * selection, this file fails to compile with an unresolved `createFakeApi` reference.
 */
interface FakeApi {
    @GET("ping") suspend fun ping(): String
}

/** Calls the ktorfit-GENERATED extension. Its presence is the whole test. */
fun fakeApi(): FakeApi =
    Ktorfit.Builder().baseUrl("https://example.com/").build().createFakeApi()
