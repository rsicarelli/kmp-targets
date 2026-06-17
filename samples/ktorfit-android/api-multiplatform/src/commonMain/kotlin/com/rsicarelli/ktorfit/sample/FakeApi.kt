package com.rsicarelli.ktorfit.sample

import de.jensklingenberg.ktorfit.Ktorfit
import de.jensklingenberg.ktorfit.http.GET

/**
 * Identical fake API to the android-only module, but this module supports Android + JVM. It is the
 * control: it forces ktorfit codegen across two targets via `commonMain`.
 */
interface FakeApi {
    @GET("ping") suspend fun ping(): String
}

fun fakeApi(): FakeApi =
    Ktorfit.Builder().baseUrl("https://example.com/").build().createFakeApi()
