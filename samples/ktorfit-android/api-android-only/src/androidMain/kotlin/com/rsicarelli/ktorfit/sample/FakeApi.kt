package com.rsicarelli.ktorfit.sample

import de.jensklingenberg.ktorfit.Ktorfit
import de.jensklingenberg.ktorfit.http.GET

/**
 * A fake API. Nothing here talks to a real server — it exists to force ktorfit's KSP processor to
 * generate the `createFakeApi()` extension below.
 *
 * NOTE: this lives in **androidMain**, not commonMain. With a single Android target there is no
 * commonMain metadata compilation (and so no `kspCommonMainKotlinMetadata` task) to carry the
 * generated extension. ktorfit's processor emits `createFakeApi()` into the Android compilation's
 * KSP output, so keeping the call site in androidMain puts both in the same compilation and it
 * resolves. Move this file back to commonMain and the build fails with `Unresolved reference
 * 'createFakeApi'` — that is the single-target limitation this sample documents.
 */
interface FakeApi {
    @GET("ping") suspend fun ping(): String
}

/** Calls the ktorfit-GENERATED extension. Its presence is the whole test. */
fun fakeApi(): FakeApi =
    Ktorfit.Builder().baseUrl("https://example.com/").build().createFakeApi()
